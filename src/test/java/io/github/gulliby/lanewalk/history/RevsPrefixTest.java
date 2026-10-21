package io.github.gulliby.lanewalk.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.gulliby.lanewalk.Cli;
import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RevsPrefixTest {
    private static final String TEXT = "a1b2c3d @v1 | start\n"
            + "a1f0e9c a1b2c3d | second\n"
            + "b7c8d9e a1b2c3d | side\n"
            + "c1 b7c8d9e\n"
            + "c10 c1 | next\n"
            + "m3 a1f0e9c b7c8d9e | merge\n";
    private static final History HISTORY = Parser.parse(TEXT);
    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";

    @Test
    void aUniquePrefixResolvesToTheCommit() {
        assertEquals(0, Revs.resolve(HISTORY, "a1b"));
        assertEquals(1, Revs.resolve(HISTORY, "a1f"));
        assertEquals(2, Revs.resolve(HISTORY, "b"));
        assertEquals(5, Revs.resolve(HISTORY, "m"));

        assertEquals(List.of(0, 1), HISTORY.indexesOfPrefix("a1"));
        assertEquals(List.of(0, 1), HISTORY.indexesOfPrefix("a"));
        assertEquals(List.of(3, 4), HISTORY.indexesOfPrefix("c1"));
        assertEquals(List.of(5), HISTORY.indexesOfPrefix("m"));
        assertEquals(List.of(), HISTORY.indexesOfPrefix("z"));
        assertEquals(List.of(), HISTORY.indexesOfPrefix("A1B"));

        Random random = new Random(1234);
        List<String> prefixes = new ArrayList<>();
        prefixes.add("");
        for (Commit commit : HISTORY.commits()) {
            prefixes.add(commit.id());
        }
        while (prefixes.size() < 300) {
            if (random.nextBoolean()) {
                String id = HISTORY.get(random.nextInt(HISTORY.size())).id();
                prefixes.add(id.substring(0, random.nextInt(id.length() + 1)));
            } else {
                StringBuilder builder = new StringBuilder();
                int length = random.nextInt(5);
                for (int i = 0; i < length; i++) {
                    builder.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
                }
                prefixes.add(builder.toString());
            }
        }
        for (String prefix : prefixes) {
            List<Integer> expected = new ArrayList<>();
            for (int i = 0; i < HISTORY.size(); i++) {
                if (HISTORY.get(i).id().startsWith(prefix)) {
                    expected.add(i);
                }
            }
            assertEquals(expected, HISTORY.indexesOfPrefix(prefix), "prefix \"" + prefix + "\"");
        }
    }

    @Test
    void anExactIdWinsOverLongerIds() {
        assertEquals(3, Revs.resolve(HISTORY, "c1"));
        assertEquals(4, Revs.resolve(HISTORY, "c10"));
        assertEquals(4, Revs.resolve(HISTORY, "c10~0"));
    }

    @Test
    void suffixesApplyToTheCommitFoundByPrefix() {
        assertEquals(2, Revs.resolve(HISTORY, "m^2"));
        assertEquals(1, Revs.resolve(HISTORY, "m~1"));
        assertEquals(0, Revs.resolve(HISTORY, "a1f^"));
    }

    @Test
    void aPrefixOfSeveralIdsIsAmbiguous() {
        assertFailure("a1", "ambiguous revision \"a1\"");
        assertFailure("a", "ambiguous revision \"a\"");
        assertFailure("a1^2", "ambiguous revision \"a1^2\"");
        assertFailure("c", "ambiguous revision \"c\"");
    }

    @Test
    void aPrefixOfNoIdIsUnknownAndCaseMatters() {
        assertFailure("z", "unknown revision \"z\"");
        assertFailure("a1x", "unknown revision \"a1x\"");
        assertFailure("A1B", "unknown revision \"A1B\"");
    }

    @Test
    void labelsAreNeverAbbreviated() {
        assertEquals(0, Revs.resolve(HISTORY, "@v1"));
        assertFailure("@v", "unknown revision \"@v\"");
    }

    @Test
    void theOrderOfTheChecksIsSyntaxThenBaseThenSuffixes() {
        assertFailure("a1~x", "invalid revision \"a1~x\"");
        assertFailure("a1~9", "ambiguous revision \"a1~9\"");
        assertFailure("m~9", "unknown revision \"m~9\"");
    }

    @Test
    void commandsAcceptAbbreviatedIdsAndPrintFullIds() {
        assertOk("m3\na1f0e9c\nb7c8d9e\na1b2c3d\n", "order", "abbr.txt", "m");
        assertOk("v1-3-gm3\n", "describe", "abbr.txt", "m");
        assertOk("a1b2c3d\n", "base", "abbr.txt", "a1f", "b7");
        assertOk("m3\na1f0e9c\nb7c8d9e\n", "range", "abbr.txt", "a1b..m");
        assertOk("c1\nb7c8d9e\na1b2c3d\n", "order", "abbr.txt", "c10~1");

        assertSame(List.of("bisect", "abbr.txt", "m", "a1b"),
                List.of("bisect", "abbr.txt", "m3", "a1b2c3d"));
        assertSame(List.of("draw", "abbr.txt", "m"), List.of("draw", "abbr.txt", "m3"));
        assertSame(List.of("range", "abbr.txt", "a1f...b7"),
                List.of("range", "abbr.txt", "a1f0e9c...b7c8d9e"));
    }

    @Test
    void ambiguousAndUnknownRevisionsAreErrorsOfTheCommand() {
        assertError("lanewalk: ambiguous revision \"a1\"\n", "a1");
        assertError("lanewalk: unknown revision \"z\"\n", "z");
        assertError("lanewalk: ambiguous revision \"a\"\n", "a");
        assertError("lanewalk: ambiguous revision \"c\"\n", "c");
        assertError("lanewalk: unknown revision \"A1B\"\n", "A1B");
        assertError("lanewalk: unknown revision \"a1x\"\n", "a1x");
        assertError("lanewalk: unknown revision \"@v\"\n", "@v");
        assertError("lanewalk: invalid revision \"a1~x\"\n", "a1~x");
    }

    @Test
    void anExactIdIsNotAnAbbreviation() {
        assertOk("c1\nb7c8d9e\na1b2c3d\n", "order", "abbr.txt", "c1");
    }

    private static void assertFailure(String revision, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, revision));

        assertEquals(expectedMessage, failure.getMessage());
    }

    private static void assertOk(String expectedOut, String... args) {
        MemoryIo io = new MemoryIo();

        assertEquals(0, Cli.run(List.of(args), io));
        assertEquals(List.of(expectedOut), io.outs);
        assertEquals(List.of(), io.errs);
    }

    private static void assertError(String expectedErr, String revision) {
        MemoryIo io = new MemoryIo();

        assertEquals(2, Cli.run(List.of("order", "abbr.txt", revision), io));
        assertTrue(io.outs.isEmpty());
        assertEquals(List.of(expectedErr), io.errs);
    }

    private static void assertSame(List<String> abbreviated, List<String> full) {
        MemoryIo first = new MemoryIo();
        MemoryIo second = new MemoryIo();

        int firstCode = Cli.run(abbreviated, first);
        int secondCode = Cli.run(full, second);

        assertEquals(secondCode, firstCode);
        assertEquals(second.outs, first.outs);
        assertEquals(1, first.outs.size());
        assertTrue(first.errs.isEmpty());
    }

    private static final class MemoryIo implements Io {
        final List<String> outs = new ArrayList<>();
        final List<String> errs = new ArrayList<>();

        @Override
        public byte[] readStdin() {
            return new byte[0];
        }

        @Override
        public byte[] readFile(String path) {
            return TEXT.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public void out(String text) {
            outs.add(text);
        }

        @Override
        public void err(String text) {
            errs.add(text);
        }
    }
}
