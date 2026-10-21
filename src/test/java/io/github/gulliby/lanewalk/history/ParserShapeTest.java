package io.github.gulliby.lanewalk.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class ParserShapeTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-003:1", "", list()),
        row("AC-003:2", "  \012 # ignored\012", list()),
        row("AC-003:3", " # note\015\012r @r @V1 |  start  \015\012s r | \011work | more\011  \015\012m @tip s @last r |   \015", list(map("id", "r", "index", 0L, "labels", list("r", "V1"), "parents", list(), "subject", "start"), map("id", "s", "index", 1L, "labels", list(), "parents", list(0L), "subject", "\011work | more\011"), map("id", "m", "index", 2L, "labels", list("tip", "last"), "parents", list(1L, 0L), "subject", ""))),
        row("AC-003:4", "A_1- @v_1-\012a A_1-\012", list(map("id", "A_1-", "index", 0L, "labels", list("v_1-"), "parents", list(), "subject", ""), map("id", "a", "index", 1L, "labels", list(), "parents", list(0L), "subject", ""))));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(ParserShapeTest.acceptanceActual(input))));
  }

  private static java.util.Map<String, Object> map(Object... entries) {
    java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
    for (int i = 0; i < entries.length; i += 2) out.put((String) entries[i], entries[i + 1]);
    return out;
  }

  private static java.util.List<Object> list(Object... items) {
    return new java.util.ArrayList<>(java.util.Arrays.asList(items));
  }

  /** JSON numbers compare by value: 2, 2L and 2.0 are equal; collections compare as lists. */
  private static Object normal(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean) return value;
    if (value instanceof Character || value instanceof Enum) return value.toString();
    if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) {
      double number = ((Number) value).doubleValue();
      return number == Math.rint(number) && Math.abs(number) <= 9007199254740991d ? (Object) (long) number : (Object) number;
    }
    if (value instanceof Number) return ((Number) value).longValue();
    if (value instanceof java.util.Map) {
      java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
      for (java.util.Map.Entry<?, ?> entry : ((java.util.Map<?, ?>) value).entrySet())
        out.put(String.valueOf(entry.getKey()), normal(entry.getValue()));
      return out;
    }
    java.util.List<Object> out = new java.util.ArrayList<>();
    if (value instanceof Iterable) {
      for (Object item : (Iterable<?>) value) out.add(normal(item));
      return out;
    }
    if (value.getClass().isArray()) {
      for (int i = 0; i < java.lang.reflect.Array.getLength(value); i++) out.add(normal(java.lang.reflect.Array.get(value, i)));
      return out;
    }
    return value;
  }
}
// END acceptance rows

final class ParserShapeTest {
    @org.junit.jupiter.api.Test
    void historyIndexesAndConstructorPreserveAcceptedOrder() {
        History history = Parser.parse("r @r\na r\nb r @B\nm b a\n");

        assertEquals(4, history.size());
        assertEquals(List.of("r", "a", "b", "m"),
                history.commits().stream().map(Commit::id).toList());
        assertEquals(List.of(List.of(1, 2), List.of(3), List.of(3), List.of()),
                List.of(history.children(0), history.children(1), history.children(2), history.children(3)));
        assertEquals(0, history.indexOf("r"));
        assertEquals(0, history.indexOfLabel("r"));
        assertEquals(2, history.indexOfLabel("B"));
        assertEquals(-1, history.indexOf("R"));
        assertEquals(-1, history.indexOfLabel("b"));
        assertEquals(-1, history.indexOf("missing"));
        assertEquals(-1, history.indexOfLabel("missing"));
        assertEquals(List.of(2, 1), history.get(3).parents());

        for (int index = 0; index < history.size(); index++) {
            assertEquals(history.commits().get(index), history.get(index));
        }

        History reconstructed = new History(history.commits());
        assertEquals(history.commits(), reconstructed.commits());
        assertEquals(List.of(List.of(1, 2), List.of(3), List.of(3), List.of()),
                List.of(reconstructed.children(0), reconstructed.children(1),
                        reconstructed.children(2), reconstructed.children(3)));
        assertEquals(0, reconstructed.indexOf("r"));
        assertEquals(0, reconstructed.indexOfLabel("r"));
        assertEquals(2, reconstructed.indexOfLabel("B"));
        assertEquals(-1, reconstructed.indexOf("R"));
        assertEquals(-1, reconstructed.indexOfLabel("b"));
    }

    @org.junit.jupiter.api.Test
    void loaderReadsNamedUtf8AndRemovesItsSingleLeadingBom() {
        FakeIo io = new FakeIo(
                new byte[0],
                new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 0x63, 0x31, 0x0A});

        History history = Loader.load(io, "ok.txt");

        assertEquals(List.of("c1"), history.commits().stream().map(Commit::id).toList());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("ok.txt", io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderReadsStdinAndParserAcceptsCrlf() {
        FakeIo io = new FakeIo(new byte[] {0x63, 0x31, 0x0D, 0x0A}, new byte[0]);

        History history = Loader.load(io, "-");

        assertEquals(List.of("c1"), history.commits().stream().map(Commit::id).toList());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNull(io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderRejectsAnEmptyNamedHistory() {
        FakeIo io = new FakeIo(new byte[0], new byte[] {
                0x20, 0x20, 0x0A, 0x23, 0x20, 0x63, 0x6F, 0x6D, 0x6D, 0x65, 0x6E, 0x74});

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "empty.txt"));

        assertEquals("\"empty.txt\": no commits", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("empty.txt", io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderRejectsEmptyStdinHistory() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "-"));

        assertEquals("<stdin>: no commits", failure.getMessage());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNull(io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderPrefixesParserProblemsWithTheSourceDisplay() {
        FakeIo io = new FakeIo(new byte[0], "c1\nc2 zz".getBytes(StandardCharsets.UTF_8));

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "bad.txt"));

        assertEquals("\"bad.txt\": HG004 unknown parent \"zz\" at 2:4", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("bad.txt", io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderPreservesNamedFileReadFailures() {
        FakeIo io = new FakeIo(new byte[0], new byte[0]);
        io.failFile = true;

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "missing.txt"));

        assertEquals("\"missing.txt\": cannot read the file", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("missing.txt", io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderPreservesStrictUtf8FailuresFromStdinBytes() {
        FakeIo io = new FakeIo(new byte[] {65, (byte) 255, 10}, new byte[0]);

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "-"));

        assertEquals("<stdin>: input is not valid UTF-8", failure.getMessage());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertNull(io.lastPath);
        assertNoWrites(io);
    }

    @org.junit.jupiter.api.Test
    void loaderRemovesOnlyOneOfTwoLeadingBoms() {
        FakeIo io = new FakeIo(new byte[0], new byte[] {
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                0x63, 0x31, 0x0A});

        Failure failure = assertThrows(Failure.class, () -> Loader.load(io, "bom.txt"));

        assertEquals("\"bom.txt\": HG001 unexpected character \"?\" at 1:1", failure.getMessage());
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals("bom.txt", io.lastPath);
        assertNoWrites(io);
    }

    static Object acceptanceActual(Object input) {
        History history = Parser.parse((String) input);
        List<Map<String, Object>> actual = new ArrayList<>();
        for (Commit commit : history.commits()) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("index", commit.index());
            fields.put("id", commit.id());
            fields.put("parents", commit.parents());
            fields.put("labels", commit.labels());
            fields.put("subject", commit.subject());
            actual.add(fields);
        }
        return actual;
    }

    private static void assertNoWrites(FakeIo io) {
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
        private boolean failFile;

        private FakeIo(byte[] stdinBytes, byte[] fileBytes) {
            this.stdinBytes = stdinBytes;
            this.fileBytes = fileBytes;
        }

        @Override
        public byte[] readStdin() {
            stdinReads++;
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
