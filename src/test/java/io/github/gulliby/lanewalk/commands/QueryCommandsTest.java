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
class QueryCommandsTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-001:1", "RangeCommand", map("args", list("FILE", "SPEC"), "max", 2L, "name", "range", "specs", list("--reverse"), "usage", "lanewalk range FILE SPEC [--reverse]")),
        row("AC-001:2", "BaseCommand", map("args", list("FILE", "REV", "REV"), "max", 2147483647L, "name", "base", "specs", list(), "usage", "lanewalk base FILE REV REV...")),
        row("AC-001:3", "DescribeCommand", map("args", list("FILE", "REV"), "max", 2L, "name", "describe", "specs", list(), "usage", "lanewalk describe FILE REV")));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(QueryCommandsTest.acceptanceActual(input))));
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


final class QueryCommandsTest {
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
    void rangeReturnsPlainAndTwoDotResultsInListingOrder() {
        assertResult(new RangeCommand(), List.of("history.txt", "c4"), Set.of(),
                new FakeIo(HISTORY), "c4\nc2\nc1\n", 0);
        assertResult(new RangeCommand(), List.of("history.txt", "c4..m9"), Set.of(),
                new FakeIo(HISTORY), "m9\nc7\nm6\nc8\nc5\nc3\n", 0);
    }

    @Test
    void rangeReversesOnlyTheSelectedSymmetricDifference() {
        assertResult(new RangeCommand(), List.of("history.txt", "c7...c8"), Set.of("--reverse"),
                new FakeIo(HISTORY), "c2\nc4\nm6\nc7\nc8\n", 0);
    }

    @Test
    void rangeEmptyAnswerHasNoOutputWithEitherFlagState() {
        assertResult(new RangeCommand(), List.of("history.txt", "c4..c4"), Set.of(),
                new FakeIo(HISTORY), "", 1);
        assertResult(new RangeCommand(), List.of("history.txt", "c4..c4"), Set.of("--reverse"),
                new FakeIo(HISTORY), "", 1);
    }

    @Test
    void baseFindsPairwiseAndAncestorBases() {
        assertResult(new BaseCommand(), List.of("history.txt", "c4", "c8"), Set.of(),
                new FakeIo(HISTORY), "c1\n", 0);
        assertResult(new BaseCommand(), List.of("history.txt", "c7", "c8"), Set.of(),
                new FakeIo(HISTORY), "c5\n", 0);
        assertResult(new BaseCommand(), List.of("history.txt", "c2", "c4"), Set.of(),
                new FakeIo(HISTORY), "c2\n", 0);
    }

    @Test
    void baseResolvesAllOperandsBeforeComputingTheResult() {
        assertResult(new BaseCommand(), List.of("history.txt", "c7", "c8", "c4"), Set.of(),
                new FakeIo(HISTORY), "c1\n", 0);
    }

    @Test
    void baseKeepsBothCrissCrossBasesAndReturnsEmptyWhenDisconnected() {
        assertResult(new BaseCommand(), List.of("cross.txt", "x", "y"), Set.of(),
                new FakeIo("r\na r\nb r\nx a b\ny b a\n"), "b\na\n", 0);
        assertResult(new BaseCommand(), List.of("separate.txt", "a", "b"), Set.of(),
                new FakeIo("a\nb\n"), "", 1);
    }

    @Test
    void describeFormatsLabelledAncestorAnswers() {
        assertResult(new DescribeCommand(), List.of("history.txt", "c7"), Set.of(),
                new FakeIo(HISTORY), "v2-4-gc7\n", 0);
        assertResult(new DescribeCommand(), List.of("history.txt", "@rc"), Set.of(),
                new FakeIo(HISTORY), "rc\n", 0);
        assertResult(new DescribeCommand(), List.of("history.txt", "c4"), Set.of(),
                new FakeIo(HISTORY), "v1-2-gc4\n", 0);
    }

    @Test
    void describeReturnsAnEmptyNegativeAnswerWithoutLabels() {
        assertResult(new DescribeCommand(), List.of("plain.txt", "b"), Set.of(),
                new FakeIo("a\nb a\n"), "", 1);
    }

    @Test
    void loadingFailuresPrecedeRevisionResolutionForEveryQueryCommand() {
        FakeIo rangeReadFailure = new FakeIo(HISTORY);
        rangeReadFailure.failFile = true;
        assertFailure(new RangeCommand(), List.of("bad.txt", "@"), rangeReadFailure,
                "\"bad.txt\": cannot read the file");
        FakeIo baseReadFailure = new FakeIo(HISTORY);
        baseReadFailure.failFile = true;
        assertFailure(new BaseCommand(), List.of("bad.txt", "@", "zz"), baseReadFailure,
                "\"bad.txt\": cannot read the file");
        FakeIo describeReadFailure = new FakeIo(HISTORY);
        describeReadFailure.failFile = true;
        assertFailure(new DescribeCommand(), List.of("bad.txt", "@"), describeReadFailure,
                "\"bad.txt\": cannot read the file");

        byte[] malformedUtf8 = new byte[] {0x41, (byte) 0xFF, 0x0A};
        assertFailure(new RangeCommand(), List.of("-", "@"),
                new FakeIo(malformedUtf8, new byte[0]), "<stdin>: input is not valid UTF-8");
        assertFailure(new BaseCommand(), List.of("-", "@", "zz"),
                new FakeIo(malformedUtf8, new byte[0]), "<stdin>: input is not valid UTF-8");
        assertFailure(new DescribeCommand(), List.of("-", "@"),
                new FakeIo(malformedUtf8, new byte[0]), "<stdin>: input is not valid UTF-8");

        assertFailure(new RangeCommand(), List.of("empty.txt", "@"), new FakeIo(""),
                "\"empty.txt\": no commits");
        assertFailure(new BaseCommand(), List.of("empty.txt", "@", "zz"), new FakeIo(""),
                "\"empty.txt\": no commits");
        assertFailure(new DescribeCommand(), List.of("empty.txt", "@"), new FakeIo(""),
                "\"empty.txt\": no commits");

        String malformedHistory = "c1\nc2 zz\n";
        assertFailure(new RangeCommand(), List.of("bad.txt", "@"), new FakeIo(malformedHistory),
                "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4");
        assertFailure(new BaseCommand(), List.of("bad.txt", "@", "zz"), new FakeIo(malformedHistory),
                "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4");
        assertFailure(new DescribeCommand(), List.of("bad.txt", "@"), new FakeIo(malformedHistory),
                "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4");
    }

    @Test
    void rangeReportsRevisionErrorsLeftToRightAfterLoading() {
        assertFailure(new RangeCommand(), List.of("history.txt", "c1....c2"), new FakeIo(HISTORY),
                "invalid revision \".c2\"");
        assertFailure(new RangeCommand(), List.of("history.txt", "c4.."), new FakeIo(HISTORY),
                "invalid revision \"\"");
        assertFailure(new RangeCommand(), List.of("history.txt", "zz..@"), new FakeIo(HISTORY),
                "unknown revision \"zz\"");
    }

    @Test
    void baseAndDescribeReportTheFirstInvalidRevisionBeforeLaterOperands() {
        assertFailure(new BaseCommand(), List.of("history.txt", "zz", "@"), new FakeIo(HISTORY),
                "unknown revision \"zz\"");
        assertFailure(new BaseCommand(), List.of("history.txt", "@", "zz"), new FakeIo(HISTORY),
                "invalid revision \"@\"");
        assertFailure(new BaseCommand(), List.of("separate.txt", "a", "b", "zz"), new FakeIo("a\nb\n"),
                "unknown revision \"zz\"");
        assertFailure(new DescribeCommand(), List.of("history.txt", "@"), new FakeIo(HISTORY),
                "invalid revision \"@\"");
    }

    @Test
    void successfulQueriesRouteStdinAndNamedPathsToTheirExclusiveReaders() {
        FakeIo stdin = new FakeIo(HISTORY.getBytes(StandardCharsets.UTF_8), new byte[0]);
        assertResult(new RangeCommand(), List.of("-", "c4"), Set.of(),
                stdin, "c4\nc2\nc1\n", 0);
        assertEquals(1, stdin.stdinReads);
        assertEquals(0, stdin.fileReads);
        assertEquals(null, stdin.lastPath);

        FakeIo named = new FakeIo(HISTORY);
        assertResult(new DescribeCommand(), List.of("custom/history.txt", "@rc"), Set.of(),
                named, "rc\n", 0);
        assertEquals(0, named.stdinReads);
        assertEquals(1, named.fileReads);
        assertEquals("custom/history.txt", named.lastPath);
    }

    static Object acceptanceActual(Object input) {
        Command command = switch ((String) input) {
            case "RangeCommand" -> new RangeCommand();
            case "BaseCommand" -> new BaseCommand();
            case "DescribeCommand" -> new DescribeCommand();
            default -> throw new IllegalArgumentException("unexpected command name");
        };
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("name", command.name());
        actual.put("usage", command.usage());
        actual.put("args", command.args());
        actual.put("max", command.max());
        actual.put("specs", command.specs().stream().sorted().toList());
        return actual;
    }

    private static void assertResult(Command command, List<String> args, Set<String> flags,
                                     FakeIo io, String expectedOutput, int expectedCode) {
        assertEquals(new Command.Result(expectedOutput, expectedCode), command.run(args, flags, io));
        assertNoWrites(io);
    }

    private static void assertFailure(Command command, List<String> args, FakeIo io,
                                      String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> command.run(args, Set.of(), io));
        assertEquals(expectedMessage, failure.getMessage());
        assertNoWrites(io);
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

        private FakeIo(String fileText) {
            this(new byte[0], fileText.getBytes(StandardCharsets.UTF_8));
        }

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
