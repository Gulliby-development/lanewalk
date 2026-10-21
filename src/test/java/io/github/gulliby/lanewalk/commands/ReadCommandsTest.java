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

class ReadCommandsTest {
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
    void checkReportsStatisticsForTheNineCommitHistory() {
        FakeIo io = FakeIo.file(HISTORY);

        assertEquals(new Command.Result(
                "ok, 9 commits\nroots       1\nheads       1\nmerges      2\nlabels      3\nlongest     6\n",
                0), check(io, "history.txt"));
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals("history.txt", io.lastPath);
    }

    @Test
    void checkCountsACommitAndUsesSingularPluralFormatting() {
        FakeIo io = FakeIo.file("r\n");

        assertEquals(new Command.Result(
                "ok, 1 commit\nroots       1\nheads       1\nmerges      0\nlabels      0\nlongest     1\n",
                0), check(io, "history.txt"));
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
        assertEquals("history.txt", io.lastPath);
    }

    @Test
    void checkFindsTheLongestChainThroughASecondParent() {
        FakeIo io = FakeIo.file("r\na r\nb a\ns\nm s b @x @y\n");

        assertEquals(new Command.Result(
                "ok, 5 commits\nroots       2\nheads       1\nmerges      1\nlabels      2\nlongest     4\n",
                0), check(io, "history.txt"));
        assertEquals(1, io.fileReads);
        assertEquals(0, io.stdinReads);
    }

    @Test
    void checkReturnsOnlyTheFirstParserProblemAndOrderLoadsBeforeResolvingRevisions() {
        FakeIo checkIo = FakeIo.file("c1\nc2 zz\nc3 !\n");
        assertEquals(new Command.Result(
                "HG004 unknown parent \"zz\" at 2:4\n1 problem\n", 1),
                check(checkIo, "bad.txt"));
        assertEquals(1, checkIo.fileReads);
        assertEquals("bad.txt", checkIo.lastPath);

        FakeIo orderIo = FakeIo.file("c1\nc2 zz\nc3 !\n");
        assertFailure(orderIo, "\"bad.txt\": HG004 unknown parent \"zz\" at 2:4",
                () -> order(orderIo, Set.of(), "bad.txt", "@"));
        assertEquals(1, orderIo.fileReads);
        assertEquals(0, orderIo.stdinReads);
        assertEquals("bad.txt", orderIo.lastPath);
    }

    @Test
    void readAndEmptyHistoryFailuresArePreservedByBothCommands() {
        FakeIo failedCheck = FakeIo.failedFile();
        assertFailure(failedCheck, "\"nowhere.txt\": cannot read the file",
                () -> check(failedCheck, "nowhere.txt"));
        assertEquals(1, failedCheck.fileReads);
        assertEquals(0, failedCheck.stdinReads);

        FakeIo failedOrder = FakeIo.failedFile();
        assertFailure(failedOrder, "\"nowhere.txt\": cannot read the file",
                () -> order(failedOrder, Set.of(), "nowhere.txt"));
        assertEquals(1, failedOrder.fileReads);
        assertEquals(0, failedOrder.stdinReads);

        FakeIo emptyCheck = FakeIo.file("");
        assertFailure(emptyCheck, "\"empty.txt\": no commits",
                () -> check(emptyCheck, "empty.txt"));
        assertEquals(1, emptyCheck.fileReads);

        FakeIo emptyOrder = FakeIo.file("");
        assertFailure(emptyOrder, "\"empty.txt\": no commits",
                () -> order(emptyOrder, Set.of(), "empty.txt"));
        assertEquals(1, emptyOrder.fileReads);

        FakeIo commentCheck = FakeIo.file("  \n# comment\n");
        assertFailure(commentCheck, "\"empty.txt\": no commits",
                () -> check(commentCheck, "empty.txt"));
        assertEquals(1, commentCheck.fileReads);

        FakeIo commentOrder = FakeIo.file("  \n# comment\n");
        assertFailure(commentOrder, "\"empty.txt\": no commits",
                () -> order(commentOrder, Set.of(), "empty.txt"));
        assertEquals(1, commentOrder.fileReads);
    }

    @Test
    void bothCommandsRejectExactMalformedUtf8StdinBytes() {
        FakeIo checkIo = FakeIo.stdin(new byte[] {65, (byte) 255, 10});
        assertFailure(checkIo, "<stdin>: input is not valid UTF-8",
                () -> check(checkIo, "-"));
        assertEquals(1, checkIo.stdinReads);
        assertEquals(0, checkIo.fileReads);

        FakeIo orderIo = FakeIo.stdin(new byte[] {65, (byte) 255, 10});
        assertFailure(orderIo, "<stdin>: input is not valid UTF-8",
                () -> order(orderIo, Set.of(), "-"));
        assertEquals(1, orderIo.stdinReads);
        assertEquals(0, orderIo.fileReads);
    }

    @Test
    void bothCommandsRemoveOneLeadingBomAndRetainASecondBomAsHistoryText() {
        byte[] oneBom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 114, 10};
        FakeIo checkOneBom = FakeIo.stdin(oneBom);
        assertEquals(new Command.Result(
                "ok, 1 commit\nroots       1\nheads       1\nmerges      0\nlabels      0\nlongest     1\n",
                0), check(checkOneBom, "-"));
        assertEquals(1, checkOneBom.stdinReads);
        assertEquals(0, checkOneBom.fileReads);

        FakeIo orderOneBom = FakeIo.stdin(oneBom);
        assertEquals(new Command.Result("r\n", 0), order(orderOneBom, Set.of(), "-"));
        assertEquals(1, orderOneBom.stdinReads);
        assertEquals(0, orderOneBom.fileReads);

        byte[] twoBoms = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF,
                (byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 114, 10};
        FakeIo checkTwoBoms = FakeIo.stdin(twoBoms);
        assertEquals(new Command.Result(
                "HG001 unexpected character \"?\" at 1:1\n1 problem\n", 1),
                check(checkTwoBoms, "-"));
        assertEquals(1, checkTwoBoms.stdinReads);
        assertEquals(0, checkTwoBoms.fileReads);

        FakeIo orderTwoBoms = FakeIo.stdin(twoBoms);
        assertFailure(orderTwoBoms, "<stdin>: HG001 unexpected character \"?\" at 1:1",
                () -> order(orderTwoBoms, Set.of(), "-"));
        assertEquals(1, orderTwoBoms.stdinReads);
        assertEquals(0, orderTwoBoms.fileReads);
    }

    @Test
    void orderListsEveryCommitByDefaultAndIncludesDisconnectedHistory() {
        FakeIo allIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result(
                "m9\nc7\nm6\nc4\nc2\nc8\nc5\nc3\nc1\n", 0),
                order(allIo, Set.of(), "history.txt"));
        assertEquals(1, allIo.fileReads);
        assertEquals(0, allIo.stdinReads);

        FakeIo disconnectedIo = FakeIo.file("r1\nr2\na r1\n");
        assertEquals(new Command.Result("a\nr1\nr2\n", 0),
                order(disconnectedIo, Set.of(), "two.txt"));
        assertEquals(1, disconnectedIo.fileReads);
        assertEquals("two.txt", disconnectedIo.lastPath);
    }

    @Test
    void orderCombinesSelectedRepeatedAndOverlappingTipsAndResolvesSuffixes() {
        FakeIo selectedIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result("c4\nc2\nc1\n", 0),
                order(selectedIo, Set.of(), "history.txt", "c4"));

        FakeIo repeatedIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result("c4\nc2\nc1\n", 0),
                order(repeatedIo, Set.of(), "history.txt", "c4", "c2", "c4"));

        FakeIo suffixIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result("c5\nc3\nc1\n", 0),
                order(suffixIo, Set.of(), "history.txt", "@rc^2~1"));
        assertEquals(1, selectedIo.fileReads);
        assertEquals(1, repeatedIo.fileReads);
        assertEquals(1, suffixIo.fileReads);
    }

    @Test
    void orderReversesTheCompleteDefaultOrSelectedListing() {
        FakeIo defaultIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result(
                "c1\nc3\nc5\nc8\nc2\nc4\nm6\nc7\nm9\n", 0),
                order(defaultIo, Set.of("--reverse"), "history.txt"));

        FakeIo selectedIo = FakeIo.file(HISTORY);
        assertEquals(new Command.Result("c1\nc2\nc4\n", 0),
                order(selectedIo, Set.of("--reverse"), "history.txt", "c4"));
        assertEquals(1, defaultIo.fileReads);
        assertEquals(1, selectedIo.fileReads);
    }

    @Test
    void orderResolvesRevisionsLeftToRightWithoutReturningPartialOutput() {
        FakeIo firstErrorIo = FakeIo.file(HISTORY);
        assertFailure(firstErrorIo, "unknown revision \"zz\"",
                () -> order(firstErrorIo, Set.of(), "history.txt", "zz", "@"));

        FakeIo secondErrorIo = FakeIo.file(HISTORY);
        assertFailure(secondErrorIo, "invalid revision \"@\"", 
                () -> order(secondErrorIo, Set.of(), "history.txt", "@", "zz"));

        FakeIo laterErrorIo = FakeIo.file(HISTORY);
        assertFailure(laterErrorIo, "unknown revision \"zz\"",
                () -> order(laterErrorIo, Set.of(), "history.txt", "c4", "zz"));
        assertEquals(1, firstErrorIo.fileReads);
        assertEquals(1, secondErrorIo.fileReads);
        assertEquals(1, laterErrorIo.fileReads);
    }

    @Test
    void orderReportsEverySpecifiedSingleRevisionError() {
        FakeIo invalidSyntax = FakeIo.file(HISTORY);
        assertFailure(invalidSyntax, "invalid revision \"c1~x\"",
                () -> order(invalidSyntax, Set.of(), "history.txt", "c1~x"));

        FakeIo missingParent = FakeIo.file(HISTORY);
        assertFailure(missingParent, "unknown revision \"m9^3\"",
                () -> order(missingParent, Set.of(), "history.txt", "m9^3"));

        FakeIo rootWalk = FakeIo.file(HISTORY);
        assertFailure(rootWalk, "unknown revision \"c1~\"",
                () -> order(rootWalk, Set.of(), "history.txt", "c1~"));

        FakeIo unknownLabel = FakeIo.file(HISTORY);
        assertFailure(unknownLabel, "unknown revision \"@zz\"",
                () -> order(unknownLabel, Set.of(), "history.txt", "@zz"));

        FakeIo nonAscii = FakeIo.file(HISTORY);
        assertFailure(nonAscii, "invalid revision \"?\"",
                () -> order(nonAscii, Set.of(), "history.txt", "😀"));
    }

    static Object acceptanceActual(Object input) {
        String className = (String) input;
        Command command = className.equals("CheckCommand") ? new CheckCommand() : new OrderCommand();
        Map<String, Object> actual = new LinkedHashMap<>();
        actual.put("name", command.name());
        actual.put("usage", command.usage());
        actual.put("args", command.args());
        actual.put("max", command.max());
        List<String> specs = new ArrayList<>(command.specs());
        specs.sort(String::compareTo);
        actual.put("specs", specs);
        return actual;
    }

    private static Command.Result check(FakeIo io, String file) {
        try {
            return new CheckCommand().run(List.of(file), Set.of(), io);
        } finally {
            assertNoWrites(io);
        }
    }

    private static Command.Result order(FakeIo io, Set<String> flags, String... args) {
        try {
            return new OrderCommand().run(List.of(args), flags, io);
        } finally {
            assertNoWrites(io);
        }
    }

    private static void assertFailure(FakeIo io, String message, Runnable action) {
        Failure failure = assertThrows(Failure.class, action::run);
        assertEquals(message, failure.getMessage());
        assertNoWrites(io);
    }

    private static void assertNoWrites(FakeIo io) {
        assertEquals(0, io.outCalls);
        assertEquals(0, io.errCalls);
    }

    private static final class FakeIo implements Io {
        private final byte[] stdinBytes;
        private final byte[] fileBytes;
        private final boolean failFile;
        private int stdinReads;
        private int fileReads;
        private int outCalls;
        private int errCalls;
        private String lastPath;

        private FakeIo(byte[] stdinBytes, byte[] fileBytes, boolean failFile) {
            this.stdinBytes = stdinBytes;
            this.fileBytes = fileBytes;
            this.failFile = failFile;
        }

        private static FakeIo file(String content) {
            return new FakeIo(new byte[0], content.getBytes(StandardCharsets.UTF_8), false);
        }

        private static FakeIo failedFile() {
            return new FakeIo(new byte[0], new byte[0], true);
        }

        private static FakeIo stdin(byte[] bytes) {
            return new FakeIo(bytes, new byte[0], false);
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

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class ReadCommandsTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-001:1", "CheckCommand", map("args", list("FILE"), "max", 1L, "name", "check", "specs", list(), "usage", "lanewalk check FILE")),
        row("AC-001:2", "OrderCommand", map("args", list("FILE"), "max", 2147483647L, "name", "order", "specs", list("--reverse"), "usage", "lanewalk order FILE [REV...] [--reverse]")));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(ReadCommandsTest.acceptanceActual(input))));
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
