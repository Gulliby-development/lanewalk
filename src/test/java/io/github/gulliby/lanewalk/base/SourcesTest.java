package io.github.gulliby.lanewalk.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.commands.Command;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SourcesTest {
    @Test
    void failurePreservesItsMessageAndRuntimeType() {
        Failure failure = new Failure("expected problem");
        RuntimeException runtimeFailure = failure;

        assertEquals("expected problem", runtimeFailure.getMessage());
    }

    @Test
    void commandExposesMetadataAndBufferedResults() {
        Command command = new Command() {
            @Override
            public String name() {
                return "sample";
            }

            @Override
            public String usage() {
                return "lanewalk sample FILE";
            }

            @Override
            public List<String> args() {
                return List.of("FILE");
            }

            @Override
            public int max() {
                return Integer.MAX_VALUE;
            }

            @Override
            public Set<String> specs() {
                return Set.of("--negative");
            }

            @Override
            public Result run(List<String> args, Set<String> flags, Io io) {
                return new Result(args.get(0), flags.contains("--negative") ? 1 : 0);
            }
        };

        assertEquals("sample", command.name());
        assertEquals("lanewalk sample FILE", command.usage());
        assertEquals(List.of("FILE"), command.args());
        assertEquals(Integer.MAX_VALUE, command.max());
        assertEquals(Set.of("--negative"), command.specs());
        assertEquals(new Command.Result("buffered", 0),
                command.run(List.of("buffered"), Set.of(), new FakeIo(new byte[0], new byte[0])));
        assertEquals(new Command.Result("answer", 1),
                command.run(List.of("answer"), Set.of("--negative"), new FakeIo(new byte[0], new byte[0])));
    }

    @Test
    void readsStdinAndUsesItsDisplayName() {
        // Fixture bytes: 63 31 0A.
        FakeIo io = new FakeIo(new byte[] {0x63, 0x31, 0x0A}, new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", "c1\n"), source);
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertEquals(null, io.lastPath);
        assertNoOutput(io);
    }

    @Test
    void readsPlainPathUnchangedAndQuotesItsDisplayName() {
        FakeIo io = new FakeIo(new byte[0], new byte[] {0x63, 0x31, 0x0A});

        Sources.Source source = Sources.read(io, "history.txt");

        assertEquals(new Sources.Source("\"history.txt\"", "c1\n"), source);
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("history.txt", io.lastPath);
        assertNoOutput(io);
    }

    @Test
    void quotesSupplementaryCharactersInDisplayButReadsTheOriginalPath() {
        FakeIo io = new FakeIo(new byte[0], new byte[] {0x63, 0x31, 0x0A});

        Sources.Source source = Sources.read(io, "a😀.txt");

        assertEquals(new Sources.Source("\"a?.txt\"", "c1\n"), source);
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("a😀.txt", io.lastPath);
        assertNoOutput(io);
    }

    @Test
    void reportsStdinReadFailureWithoutEmittingOutput() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);
        io.failStdin = true;

        Failure failure = assertThrows(Failure.class, () -> Sources.read(io, "-"));

        assertEquals("<stdin>: cannot read the file", failure.getMessage());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void reportsFileReadFailureWithoutChangingThePath() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);
        io.failFile = true;

        Failure failure = assertThrows(Failure.class, () -> Sources.read(io, "history.txt"));

        assertEquals("\"history.txt\": cannot read the file", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("history.txt", io.lastPath);
        assertNoOutput(io);
    }

    @Test
    void acceptsEmptyInput() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", ""), source);
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void decodesUnicodeAndPreservesCarriageReturnAndLineFeed() {
        // Fixture bytes: 41 C3 A9 F0 9F 98 80 0D 0A.
        FakeIo io = new FakeIo(
                new byte[] {0x41, (byte) 0xC3, (byte) 0xA9, (byte) 0xF0, (byte) 0x9F,
                        (byte) 0x98, (byte) 0x80, 0x0D, 0x0A},
                new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", "Aé😀\r\n"), source);
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNoOutput(io);
    }

    @Test
    void removesOneLeadingBom() {
        // Fixture bytes: EF BB BF 41.
        FakeIo io = new FakeIo(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41}, new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", "A"), source);
        assertNoOutput(io);
    }

    @Test
    void removesOnlyTheFirstOfTwoLeadingBoms() {
        // Fixture bytes: EF BB BF EF BB BF 41.
        FakeIo io = new FakeIo(
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x41},
                new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", "\uFEFFA"), source);
        assertNoOutput(io);
    }

    @Test
    void preservesAnInteriorBom() {
        // Fixture bytes: 41 EF BB BF 42.
        FakeIo io = new FakeIo(
                new byte[] {0x41, (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x42},
                new byte[0]);

        Sources.Source source = Sources.read(io, "-");

        assertEquals(new Sources.Source("<stdin>", "A\uFEFFB"), source);
        assertNoOutput(io);
    }

    @Test
    void rejectsEverySpecifiedMalformedStdinSequence() {
        // Each literal fixture below is submitted to Sources.read and checked for the exact Failure message.
        // Malformed bytes: 41 FF 0A.
        assertInvalidStdin(new byte[] {0x41, (byte) 0xFF, 0x0A});
        // Malformed bytes: 80.
        assertInvalidStdin(new byte[] {(byte) 0x80});
        // Malformed bytes: C2.
        assertInvalidStdin(new byte[] {(byte) 0xC2});
        // Malformed bytes: C0 AF.
        assertInvalidStdin(new byte[] {(byte) 0xC0, (byte) 0xAF});
        // Malformed bytes: ED A0 80.
        assertInvalidStdin(new byte[] {(byte) 0xED, (byte) 0xA0, (byte) 0x80});
        // Malformed bytes: F4 90 80 80.
        assertInvalidStdin(new byte[] {(byte) 0xF4, (byte) 0x90, (byte) 0x80, (byte) 0x80});
    }

    @Test
    void rejectsMalformedNamedFileBytes() {
        // Fixture bytes: 41 FF 0A.
        FakeIo io = new FakeIo(new byte[0], new byte[] {0x41, (byte) 0xFF, 0x0A});

        Failure failure = assertThrows(Failure.class, () -> Sources.read(io, "bad.txt"));

        assertEquals("\"bad.txt\": input is not valid UTF-8", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("bad.txt", io.lastPath);
        assertNoOutput(io);
    }

    private static void assertInvalidStdin(byte[] bytes) {
        FakeIo io = new FakeIo(bytes, new byte[0]);

        Failure failure = assertThrows(Failure.class, () -> Sources.read(io, "-"));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNoOutput(io);
    }

    private static void assertNoOutput(FakeIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] stdinBytes;
        private final byte[] fileBytes;
        private int stdinReads;
        private int fileReads;
        private int outCalls;
        private int errCalls;
        private String lastPath;
        private boolean failStdin;
        private boolean failFile;

        private FakeIo(byte[] stdinBytes, byte[] fileBytes) {
            this.stdinBytes = stdinBytes;
            this.fileBytes = fileBytes;
        }

        @Override
        public byte[] readStdin() throws IOException {
            stdinReads++;
            if (failStdin) {
                throw new IOException("stdin unavailable");
            }
            return stdinBytes;
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            fileReads++;
            lastPath = path;
            if (failFile) {
                throw new IOException("file unavailable");
            }
            return fileBytes;
        }

        @Override
        public void out(String text) {
            outCalls++;
        }

        @Override
        public void err(String text) {
            errCalls++;
        }
    }
}
