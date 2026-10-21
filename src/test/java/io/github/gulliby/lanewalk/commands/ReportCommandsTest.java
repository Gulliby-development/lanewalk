package io.github.gulliby.lanewalk.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class ReportCommandsTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-001:1", "BisectCommand", map("args", list("FILE", "BAD", "GOOD"), "max", 2147483647L, "name", "bisect", "specs", list(), "usage", "lanewalk bisect FILE BAD GOOD...")),
        row("AC-001:2", "DrawCommand", map("args", list("FILE"), "max", 2147483647L, "name", "draw", "specs", list(), "usage", "lanewalk draw FILE [REV...]")));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(ReportCommandsTest.acceptanceActual(input))));
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

// Imports are declared above the host acceptance-row test.

final class ReportCommandsTest {
    private static final String HISTORY = """
            # a small project
            c1 @v1 | start
            c2 c1 | parser
            c3 c1 | lexer
            c4 c2 | parser fix
            c5 c3 @v2 | lexer fix
            m6 c4 c5 | merge lexer work
            c7 m6 | docs
            c8 c5 | experiment
            m9 c7 c8 @rc | merge experiment
            """;

    private static final String TWO = """
            r1 | root one
            r2 | root two
            a3 r1 | on one
            b4 r2 | on two
            x5 r1 | side
            o6 a3 b4 x5 | octopus
            t7 o6
            """;

    private static final String HISTORY_DRAWING =
            "*-+ m9 (@rc) merge experiment\n"
                    + "* | c7 docs\n"
                    + "*-|-+ m6 merge lexer work\n"
                    + "* | | c4 parser fix\n"
                    + "* | | c2 parser\n"
                    + "| * | c8 experiment\n"
                    + "| *-+ c5 (@v2) lexer fix\n"
                    + "| * c3 lexer\n"
                    + "*-+ c1 (@v1) start\n";

    private static final String TWO_DRAWING =
            "* t7\n"
                    + "*-+-+ o6 octopus\n"
                    + "* | | a3 on one\n"
                    + "| * | b4 on two\n"
                    + "| * | r2 root two\n"
                    + "|   * x5 side\n"
                    + "*- -+ r1 root one\n";

    private static final String SELECTED_DRAWING =
            "* c4 parser fix\n"
                    + "* c2 parser\n"
                    + "* c1 (@v1) start\n";

    static Object acceptanceActual(Object input) {
        Command command = switch ((String) input) {
            case "BisectCommand" -> new BisectCommand();
            case "DrawCommand" -> new DrawCommand();
            default -> throw new IllegalArgumentException("unknown command class");
        };
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("name", command.name());
        actual.put("usage", command.usage());
        actual.put("args", command.args());
        actual.put("max", (long) command.max());
        actual.put("specs", new ArrayList<>(command.specs()));
        return actual;
    }

    @Test
    void bisectReportsTheSingleGoodExample() {
        FakeIo io = withHistory();
        Command.Result result = new BisectCommand().run(
                List.of("history.txt", "m9", "c1"), Set.of(), io);

        assertEquals("next        c8\nleft        8 commits\nsteps       about 3\n", result.out());
        assertEquals(0, result.code());
        assertFileRead(io, "history.txt");
        assertNoWrites(io);
    }

    @Test
    void bisectReportsTheMultipleGoodExample() {
        FakeIo io = withHistory();
        Command.Result result = new BisectCommand().run(
                List.of("history.txt", "m9", "c2", "c3"), Set.of(), io);

        assertEquals("next        m6\nleft        6 commits\nsteps       about 3\n", result.out());
        assertEquals(0, result.code());
        assertFileRead(io, "history.txt");
        assertNoWrites(io);
    }

    @Test
    void doneBisectReportsOnlyTheFirstBadCommit() {
        FakeIo io = withHistory();
        Command.Result result = new BisectCommand().run(
                List.of("history.txt", "c4", "c2"), Set.of(), io);

        assertEquals("first       c4\n", result.out());
        assertEquals(0, result.code());
        assertFileRead(io, "history.txt");
        assertNoWrites(io);
    }

    @Test
    void bisectReadsStdinAndResolvesOrderedLabels() {
        FakeIo io = new FakeIo(HISTORY.getBytes(StandardCharsets.UTF_8));
        Command.Result result = new BisectCommand().run(List.of("-", "@rc", "@v1"), Set.of(), io);

        assertEquals("next        c8\nleft        8 commits\nsteps       about 3\n", result.out());
        assertEquals(0, result.code());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertEquals(null, io.lastPath);
        assertNoWrites(io);
    }

    @Test
    void drawRendersEveryCommitInTheNineCommitHistory() {
        FakeIo io = withHistory();
        Command.Result result = new DrawCommand().run(List.of("history.txt"), Set.of(), io);

        assertEquals(HISTORY_DRAWING, result.out());
        assertEquals(0, result.code());
        assertFileRead(io, "history.txt");
        assertNoWrites(io);
    }

    @Test
    void drawRendersEveryCommitInTheSevenCommitHistory() {
        FakeIo io = new FakeIo(new byte[0]);
        io.addFile("two.txt", TWO);
        Command.Result result = new DrawCommand().run(List.of("two.txt"), Set.of(), io);

        assertEquals(TWO_DRAWING, result.out());
        assertEquals(0, result.code());
        assertFileRead(io, "two.txt");
        assertNoWrites(io);
    }

    @Test
    void drawRendersSingleOverlappingAndSuffixRevisionReaches() {
        FakeIo single = withHistory();
        Command.Result singleResult = new DrawCommand().run(
                List.of("history.txt", "c4"), Set.of(), single);
        assertEquals(SELECTED_DRAWING, singleResult.out());
        assertEquals(0, singleResult.code());
        assertFileRead(single, "history.txt");
        assertNoWrites(single);

        FakeIo overlapping = withHistory();
        Command.Result overlappingResult = new DrawCommand().run(
                List.of("history.txt", "c2", "c4", "c4"), Set.of(), overlapping);
        assertEquals(SELECTED_DRAWING, overlappingResult.out());
        assertEquals(0, overlappingResult.code());
        assertFileRead(overlapping, "history.txt");
        assertNoWrites(overlapping);

        FakeIo suffix = withHistory();
        Command.Result suffixResult = new DrawCommand().run(
                List.of("history.txt", "@rc~3"), Set.of(), suffix);
        assertEquals(SELECTED_DRAWING, suffixResult.out());
        assertEquals(0, suffixResult.code());
        assertFileRead(suffix, "history.txt");
        assertNoWrites(suffix);
    }

    @Test
    void drawPreservesStdinLabelOrderUnicodeAndTabs() {
        FakeIo io = new FakeIo("r @z @a | α\ttext\nx | other\n".getBytes(StandardCharsets.UTF_8));
        Command.Result result = new DrawCommand().run(List.of("-", "r"), Set.of(), io);

        assertEquals("* r (@z, @a) α\ttext\n", result.out());
        assertEquals(0, result.code());
        assertEquals(1, io.stdinReads);
        assertEquals(0, io.fileReads);
        assertEquals(null, io.lastPath);
        assertNoWrites(io);
    }

    @Test
    void bothCommandsPropagateSourceFailuresBeforeRevisionFailures() {
        assertSourceFailure(new BisectCommand(), List.of("nowhere.txt", "@", "@"),
                new FakeIo(new byte[0]), "\"nowhere.txt\": cannot read the file", 0, 1, "nowhere.txt");
        assertSourceFailure(new DrawCommand(), List.of("nowhere.txt", "@"),
                new FakeIo(new byte[0]), "\"nowhere.txt\": cannot read the file", 0, 1, "nowhere.txt");

        byte[] malformedUtf8 = new byte[] {0x41, (byte) 0xFF, 0x0A};
        assertSourceFailure(new BisectCommand(), List.of("-", "@", "@"),
                new FakeIo(malformedUtf8), "<stdin>: input is not valid UTF-8", 1, 0, null);
        assertSourceFailure(new DrawCommand(), List.of("-", "@"),
                new FakeIo(malformedUtf8), "<stdin>: input is not valid UTF-8", 1, 0, null);

        FakeIo emptyBisect = new FakeIo(new byte[0]);
        emptyBisect.addFile("empty.txt", "");
        assertSourceFailure(new BisectCommand(), List.of("empty.txt", "@", "@"),
                emptyBisect, "\"empty.txt\": no commits", 0, 1, "empty.txt");
        FakeIo emptyDraw = new FakeIo(new byte[0]);
        emptyDraw.addFile("empty.txt", "");
        assertSourceFailure(new DrawCommand(), List.of("empty.txt", "@"),
                emptyDraw, "\"empty.txt\": no commits", 0, 1, "empty.txt");

        FakeIo badBisect = new FakeIo(new byte[0]);
        badBisect.addFile("bad.txt", "c1\nc2 zz\n");
        assertSourceFailure(new BisectCommand(), List.of("bad.txt", "@", "@"),
                badBisect, "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4", 0, 1, "bad.txt");
        FakeIo badDraw = new FakeIo(new byte[0]);
        badDraw.addFile("bad.txt", "c1\nc2 zz\n");
        assertSourceFailure(new DrawCommand(), List.of("bad.txt", "@"),
                badDraw, "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4", 0, 1, "bad.txt");
    }

    @Test
    void commandsResolveRevisionsInOrderAndBeforeBisectPlanning() {
        FakeIo bisectBadFirst = withHistory();
        assertCommandFailure(new BisectCommand(), List.of("history.txt", "zz", "@"),
                bisectBadFirst, "unknown revision \"zz\"");
        assertFileRead(bisectBadFirst, "history.txt");
        assertNoWrites(bisectBadFirst);

        FakeIo bisectGoodOrder = withHistory();
        assertCommandFailure(new BisectCommand(), List.of("history.txt", "m9", "zz", "@"),
                bisectGoodOrder, "unknown revision \"zz\"");
        assertFileRead(bisectGoodOrder, "history.txt");
        assertNoWrites(bisectGoodOrder);

        FakeIo drawFirst = withHistory();
        assertCommandFailure(new DrawCommand(), List.of("history.txt", "zz", "@"),
                drawFirst, "unknown revision \"zz\"");
        assertFileRead(drawFirst, "history.txt");
        assertNoWrites(drawFirst);

        FakeIo drawInvalidFirst = withHistory();
        assertCommandFailure(new DrawCommand(), List.of("history.txt", "@", "zz"),
                drawInvalidFirst, "invalid revision \"@\"");
        assertFileRead(drawInvalidFirst, "history.txt");
        assertNoWrites(drawInvalidFirst);

        FakeIo goodBeforePlanning = withHistory();
        assertCommandFailure(new BisectCommand(), List.of("history.txt", "c2", "c4", "zz"),
                goodBeforePlanning, "unknown revision \"zz\"");
        assertFileRead(goodBeforePlanning, "history.txt");
        assertNoWrites(goodBeforePlanning);
    }

    @Test
    void bisectReportsTheFirstContradictoryGoodInArgumentOrder() {
        FakeIo c4First = withHistory();
        assertCommandFailure(new BisectCommand(), List.of("history.txt", "c2", "c4", "m9"),
                c4First, "bad commit \"c2\" is reachable from good commit \"c4\"");
        assertFileRead(c4First, "history.txt");
        assertNoWrites(c4First);

        FakeIo m9First = withHistory();
        assertCommandFailure(new BisectCommand(), List.of("history.txt", "c2", "m9", "c4"),
                m9First, "bad commit \"c2\" is reachable from good commit \"m9\"");
        assertFileRead(m9First, "history.txt");
        assertNoWrites(m9First);
    }

    private static FakeIo withHistory() {
        FakeIo io = new FakeIo(new byte[0]);
        io.addFile("history.txt", HISTORY);
        return io;
    }

    private static void assertSourceFailure(Command command, List<String> args, FakeIo io,
                                            String expectedMessage, int stdinReads,
                                            int fileReads, String path) {
        Failure failure = assertThrows(Failure.class, () -> command.run(args, Set.of(), io));

        assertEquals(expectedMessage, failure.getMessage());
        assertEquals(stdinReads, io.stdinReads);
        assertEquals(fileReads, io.fileReads);
        assertEquals(path, io.lastPath);
        assertNoWrites(io);
    }

    private static void assertCommandFailure(Command command, List<String> args, FakeIo io,
                                             String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> command.run(args, Set.of(), io));

        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertFileRead(FakeIo io, String path) {
        assertEquals(0, io.stdinReads);
        assertEquals(1, io.fileReads);
        assertEquals(path, io.lastPath);
    }

    private static void assertNoWrites(FakeIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] stdin;
        private final Map<String, byte[]> files = new LinkedHashMap<>();
        private int stdinReads;
        private int fileReads;
        private int outCalls;
        private int errCalls;
        private String lastPath;

        private FakeIo(byte[] stdin) {
            this.stdin = stdin;
        }

        private void addFile(String path, String contents) {
            files.put(path, contents.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public byte[] readStdin() {
            stdinReads++;
            return stdin;
        }

        @Override
        public byte[] readFile(String path) throws IOException {
            fileReads++;
            lastPath = path;
            byte[] contents = files.get(path);
            if (contents == null) {
                throw new IOException("file unavailable");
            }
            return contents;
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
