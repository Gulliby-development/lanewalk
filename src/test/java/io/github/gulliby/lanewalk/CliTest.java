package io.github.gulliby.lanewalk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.lanewalk.base.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CliTest {
    private static final String HISTORY = "# a small project\n"
            + "c1 @v1 | start\n"
            + "c2 c1 | parser\n"
            + "c3 c1 | lexer\n"
            + "c4 c2 | parser fix\n"
            + "c5 c3 @v2 | lexer fix\n"
            + "m6 c4 c5 | merge lexer work\n"
            + "c7 m6 | docs\n"
            + "c8 c5 | experiment\n"
            + "m9 c7 c8 @rc | merge experiment\n";

    @Test
    void soleHelpArgumentEmitsExactUsageOnce() {
        FakeIo io = new FakeIo(HISTORY);

        assertRun(io, 0,
                List.of("usage: lanewalk check FILE\n"
                        + "       lanewalk order FILE [REV...] [--reverse]\n"
                        + "       lanewalk range FILE SPEC [--reverse]\n"
                        + "       lanewalk base FILE REV REV...\n"
                        + "       lanewalk describe FILE REV\n"
                        + "       lanewalk bisect FILE BAD GOOD...\n"
                        + "       lanewalk draw FILE [REV...]\n"),
                List.of(), "--help");
        assertNoReads(io);
    }

    @Test
    void missingUnknownAndNonsoleHelpCommandsReportExactErrors() {
        FakeIo missing = new FakeIo(HISTORY);
        assertRun(missing, 2, List.of(),
                List.of("lanewalk: missing command (check, order, range, base, describe, bisect or draw)\n"));
        assertNoReads(missing);

        FakeIo unknown = new FakeIo(HISTORY);
        assertRun(unknown, 2, List.of(),
                List.of("lanewalk: unknown command \"fold\"\n"), "fold", "x");
        assertNoReads(unknown);

        FakeIo nonsoleHelp = new FakeIo(HISTORY);
        assertRun(nonsoleHelp, 2, List.of(),
                List.of("lanewalk: unknown command \"--help\"\n"), "--help", "x");
        assertNoReads(nonsoleHelp);
    }

    @Test
    void optionsAreValidatedBeforeArityAndInput() {
        assertValidationError("lanewalk: unknown option \"--write\"\n",
                "order", "history.txt", "--write");
        assertValidationError("lanewalk: missing SPEC\n", "range", "history.txt");
        assertValidationError("lanewalk: too many arguments\n", "check", "a", "b");
        assertValidationError("lanewalk: missing REV\n", "base", "history.txt", "c1");
        assertValidationError("lanewalk: missing GOOD\n", "bisect", "history.txt", "m9");
        assertValidationError("lanewalk: missing FILE\n", "check");
        assertValidationError("lanewalk: unknown option \"--write\"\n", "range", "--write");
        assertValidationError("lanewalk: unknown option \"--help\"\n", "check", "a", "b", "--help");
        assertValidationError("lanewalk: unknown option \"--\"\n", "order", "--");
        assertValidationError("lanewalk: unknown option \"--reverse=true\"\n",
                "order", "history.txt", "--reverse=true");
    }

    @Test
    void checkAndOrderDispatchWithOneBufferedOutputCall() {
        FakeIo check = new FakeIo(HISTORY);
        assertRun(check, 0,
                List.of("ok, 9 commits\nroots       1\nheads       1\nmerges      2\nlabels      3\nlongest     6\n"),
                List.of(), "check", "history.txt");
        assertFileRead(check, "history.txt");

        FakeIo order = new FakeIo(HISTORY);
        assertRun(order, 0, List.of("m9\nc7\nm6\nc4\nc2\nc8\nc5\nc3\nc1\n"),
                List.of(), "order", "history.txt");
        assertFileRead(order, "history.txt");
    }

    @Test
    void rangeAndBaseDispatchWithExactListings() {
        FakeIo range = new FakeIo(HISTORY);
        assertRun(range, 0, List.of("m9\nc7\nm6\nc8\nc5\nc3\n"),
                List.of(), "range", "history.txt", "c4..m9");
        assertFileRead(range, "history.txt");

        FakeIo base = new FakeIo(HISTORY);
        assertRun(base, 0, List.of("c5\n"), List.of(),
                "base", "history.txt", "c7", "c8");
        assertFileRead(base, "history.txt");
    }

    @Test
    void describeDispatchesTheRequestedRevision() {
        FakeIo io = new FakeIo(HISTORY);

        assertRun(io, 0, List.of("v2-4-gc7\n"), List.of(),
                "describe", "history.txt", "c7");
        assertFileRead(io, "history.txt");
    }

    @Test
    void bisectDispatchesNextAndDonePlans() {
        FakeIo next = new FakeIo(HISTORY);
        assertRun(next, 0,
                List.of("next        c8\nleft        8 commits\nsteps       about 3\n"),
                List.of(), "bisect", "history.txt", "m9", "c1");
        assertFileRead(next, "history.txt");

        FakeIo done = new FakeIo(HISTORY);
        assertRun(done, 0, List.of("first       c4\n"), List.of(),
                "bisect", "history.txt", "c4", "c2");
        assertFileRead(done, "history.txt");
    }

    @Test
    void drawDispatchesAllNineExactRows() {
        FakeIo io = new FakeIo(HISTORY);

        assertRun(io, 0,
                List.of("*-+ m9 (@rc) merge experiment\n"
                        + "* | c7 docs\n"
                        + "*-|-+ m6 merge lexer work\n"
                        + "* | | c4 parser fix\n"
                        + "* | | c2 parser\n"
                        + "| * | c8 experiment\n"
                        + "| *-+ c5 (@v2) lexer fix\n"
                        + "| * c3 lexer\n"
                        + "*-+ c1 (@v1) start\n"),
                List.of(), "draw", "history.txt");
        assertFileRead(io, "history.txt");
    }

    @Test
    void optionsCanAppearAnywhereAndDuplicateFlagsCountOnce() {
        FakeIo reverseOrder = new FakeIo(HISTORY);
        assertRun(reverseOrder, 0, List.of("c1\nc2\nc4\n"), List.of(),
                "order", "--reverse", "history.txt", "c4", "--reverse");
        assertFileRead(reverseOrder, "history.txt");

        FakeIo reverseRange = new FakeIo(HISTORY);
        assertRun(reverseRange, 0, List.of("c2\nc4\nm6\nc7\nc8\n"), List.of(),
                "range", "history.txt", "c7...c8", "--reverse");
        assertFileRead(reverseRange, "history.txt");

        FakeIo stdin = new FakeIo("", HISTORY);
        assertRun(stdin, 0, List.of("c4\nc2\nc1\n"), List.of(), "order", "-", "c4");
        assertStdinRead(stdin);

        FakeIo namedDash = new FakeIo(HISTORY);
        assertRun(namedDash, 0, List.of("c4\nc2\nc1\n"), List.of(), "order", "-5", "c4");
        assertFileRead(namedDash, "-5");

        FakeIo dashRevision = new FakeIo(HISTORY);
        assertRun(dashRevision, 2, List.of(),
                List.of("lanewalk: unknown revision \"-\"\n"), "order", "history.txt", "-");
        assertFileRead(dashRevision, "history.txt");
    }

    @Test
    void sourceAndParserFailuresAreBufferedAndPrecedeRevisionValidation() {
        FakeIo unreadable = new FakeIo(HISTORY, new byte[0], true);
        assertRun(unreadable, 2, List.of(),
                List.of("lanewalk: \"nowhere.txt\": cannot read the file\n"),
                "check", "nowhere.txt");
        assertFileRead(unreadable, "nowhere.txt");

        FakeIo empty = new FakeIo("");
        assertRun(empty, 2, List.of(),
                List.of("lanewalk: \"empty.txt\": no commits\n"), "check", "empty.txt");
        assertFileRead(empty, "empty.txt");

        // Malformed input bytes are exactly 41 FF 0A.
        FakeIo malformedStdin = new FakeIo("", new byte[] {0x41, (byte) 0xFF, 0x0A});
        assertRun(malformedStdin, 2, List.of(),
                List.of("lanewalk: <stdin>: input is not valid UTF-8\n"), "check", "-");
        assertStdinRead(malformedStdin);

        FakeIo badHistory = new FakeIo("c1\nc2 zz\n");
        assertRun(badHistory, 2, List.of(),
                List.of("lanewalk: \"bad.txt\": HG004 unknown parent \"zz\" at 2:4\n"),
                "order", "bad.txt");
        assertFileRead(badHistory, "bad.txt");

        FakeIo parserBeforeRevision = new FakeIo("c1\nc2 zz\n");
        assertRun(parserBeforeRevision, 2, List.of(),
                List.of("lanewalk: \"bad.txt\": HG004 unknown parent \"zz\" at 2:4\n"),
                "order", "bad.txt", "zz");
        assertFileRead(parserBeforeRevision, "bad.txt");
    }

    @Test
    void revisionAndBisectFailuresUseExactMessagesAndRespectEvaluationOrder() {
        assertHistoryFailure("lanewalk: unknown revision \"zz\"\n", "order", "history.txt", "zz");
        assertHistoryFailure("lanewalk: invalid revision \"c1~x\"\n",
                "order", "history.txt", "c1~x");
        assertHistoryFailure("lanewalk: unknown revision \"m9^3\"\n",
                "order", "history.txt", "m9^3");
        assertHistoryFailure("lanewalk: unknown revision \"c1~\"\n",
                "order", "history.txt", "c1~");
        assertHistoryFailure("lanewalk: invalid revision \"@\"\n", "order", "history.txt", "@");
        assertHistoryFailure("lanewalk: unknown revision \"@zz\"\n",
                "order", "history.txt", "@zz");
        assertHistoryFailure("lanewalk: invalid revision \".c2\"\n",
                "range", "history.txt", "c1....c2");
        assertHistoryFailure("lanewalk: invalid revision \"\"\n",
                "range", "history.txt", "c4..");
        assertHistoryFailure("lanewalk: invalid revision \"?\"\n",
                "order", "history.txt", "😀");
        assertHistoryFailure("lanewalk: bad commit \"c2\" is reachable from good commit \"c4\"\n",
                "bisect", "history.txt", "c2", "c4");

        FakeIo leftToRight = new FakeIo(HISTORY);
        assertRun(leftToRight, 2, List.of(),
                List.of("lanewalk: unknown revision \"zz\"\n"),
                "order", "history.txt", "zz", "c1~x");
        assertFileRead(leftToRight, "history.txt");

        FakeIo optionBeforeRead = new FakeIo(HISTORY);
        assertRun(optionBeforeRead, 2, List.of(),
                List.of("lanewalk: unknown option \"--write\"\n"),
                "order", "nowhere.txt", "--write");
        assertNoReads(optionBeforeRead);
    }

    @Test
    void negativeAnswersStillEmitOnePossiblyEmptyOutputCall() {
        FakeIo badCheck = new FakeIo("c1\nc2 zz\n");
        assertRun(badCheck, 1, List.of("HG004 unknown parent \"zz\" at 2:4\n1 problem\n"),
                List.of(), "check", "bad.txt");
        assertFileRead(badCheck, "bad.txt");

        FakeIo emptyRange = new FakeIo(HISTORY);
        assertRun(emptyRange, 1, List.of(""), List.of(),
                "range", "history.txt", "c1..c1");
        assertFileRead(emptyRange, "history.txt");

        String two = "r1 | root one\n"
                + "r2 | root two\n"
                + "a3 r1 | on one\n"
                + "b4 r2 | on two\n"
                + "x5 r1 | side\n"
                + "o6 a3 b4 x5 | octopus\n"
                + "t7 o6\n";
        FakeIo emptyBase = new FakeIo(two);
        assertRun(emptyBase, 1, List.of(""), List.of(),
                "base", "two.txt", "a3", "b4");
        assertFileRead(emptyBase, "two.txt");

        FakeIo emptyDescribe = new FakeIo(two);
        assertRun(emptyDescribe, 1, List.of(""), List.of(),
                "describe", "two.txt", "t7");
        assertFileRead(emptyDescribe, "two.txt");
    }

    private static void assertHistoryFailure(String expectedError, String... argv) {
        FakeIo io = new FakeIo(HISTORY);
        assertRun(io, 2, List.of(), List.of(expectedError), argv);
        assertFileRead(io, "history.txt");
    }

    private static void assertValidationError(String expectedError, String... argv) {
        FakeIo io = new FakeIo(HISTORY);

        assertRun(io, 2, List.of(), List.of(expectedError), argv);
        assertNoReads(io);
    }

    private static void assertRun(
            FakeIo io,
            int expectedCode,
            List<String> expectedOutCalls,
            List<String> expectedErrCalls,
            String... argv) {
        assertEquals(expectedCode, Cli.run(List.of(argv), io));
        assertEquals(expectedOutCalls, io.outCalls);
        assertEquals(expectedErrCalls, io.errCalls);
    }

    private static void assertFileRead(FakeIo io, String expectedPath) {
        assertEquals(List.of(expectedPath), io.filePaths);
        assertEquals(0, io.stdinReads);
    }

    private static void assertNoReads(FakeIo io) {
        assertEquals(List.of(), io.filePaths);
        assertEquals(0, io.stdinReads);
    }

    private static void assertStdinRead(FakeIo io) {
        assertEquals(List.of(), io.filePaths);
        assertEquals(1, io.stdinReads);
    }

    private static final class FakeIo implements Io {
        private final byte[] stdinBytes;
        private final byte[] fileBytes;
        private final boolean failFileReads;
        private final List<String> filePaths = new ArrayList<>();
        private final List<String> outCalls = new ArrayList<>();
        private final List<String> errCalls = new ArrayList<>();
        private int stdinReads;

        private FakeIo(String fileContents) {
            this(fileContents, new byte[0], false);
        }

        private FakeIo(String fileContents, String stdinContents) {
            this(fileContents, stdinContents.getBytes(StandardCharsets.UTF_8), false);
        }

        private FakeIo(String fileContents, byte[] stdinBytes) {
            this(fileContents, stdinBytes, false);
        }

        private FakeIo(String fileContents, byte[] stdinBytes, boolean failFileReads) {
            this.stdinBytes = stdinBytes.clone();
            fileBytes = fileContents.getBytes(StandardCharsets.UTF_8);
            this.failFileReads = failFileReads;
        }

        @Override
        public byte[] readStdin() {
            stdinReads++;
            return stdinBytes.clone();
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            filePaths.add(path);
            if (failFileReads) {
                throw new IOException("file unavailable");
            }
            return fileBytes.clone();
        }

        @Override
        public void out(String text) {
            outCalls.add(text);
        }

        @Override
        public void err(String text) {
            errCalls.add(text);
        }
    }
}
