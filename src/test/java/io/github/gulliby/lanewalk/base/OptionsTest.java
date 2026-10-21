package io.github.gulliby.lanewalk.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class OptionsTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-005:1", list(list(), list()), map("args", list(), "flags", list())),
        row("AC-005:2", list(list("--reverse", "a", "--reverse", "-", "-5", "b"), list("--reverse")), map("args", list("a", "-", "-5", "b"), "flags", list("--reverse"))),
        row("AC-005:3", list(list("a", "--reverse"), list("--reverse")), map("args", list("a"), "flags", list("--reverse"))),
        row("AC-005:4", list(list("--"), list()), map("failure", "unknown option \"--\"")),
        row("AC-005:5", list(list("--help"), list()), map("failure", "unknown option \"--help\"")),
        row("AC-005:6", list(list("--reverse=true"), list("--reverse")), map("failure", "unknown option \"--reverse=true\"")),
        row("AC-005:7", list(list("--write", "--other"), list()), map("failure", "unknown option \"--write\"")),
        row("AC-005:8", list(list("--😀"), list()), map("failure", "unknown option \"--?\"")));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(OptionsTest.acceptanceActual(input))));
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

class OptionsTest {
    static Object acceptanceActual(Object input) {
        List<?> values = (List<?>) input;
        List<String> argv = new ArrayList<>();
        for (Object argument : (List<?>) values.get(0)) {
            argv.add((String) argument);
        }
        Set<String> specs = new LinkedHashSet<>();
        for (Object spec : (List<?>) values.get(1)) {
            specs.add((String) spec);
        }

        try {
            Options.Parsed parsed = Options.parse(argv, specs);
            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("flags", new ArrayList<>(new TreeSet<>(parsed.flags())));
            actual.put("args", parsed.args());
            return actual;
        } catch (Failure failure) {
            Map<String, Object> actual = new LinkedHashMap<>();
            actual.put("failure", failure.getMessage());
            return actual;
        }
    }

    @Test
    void parsesEmptyInput() {
        Options.Parsed parsed = Options.parse(List.of(), Set.of());

        assertEquals(Set.of(), parsed.flags());
        assertEquals(List.of(), parsed.args());
    }

    @Test
    void parsesFlagsBeforeArguments() {
        Options.Parsed parsed = Options.parse(List.of("--reverse", "history.txt"), Set.of("--reverse"));

        assertEquals(Set.of("--reverse"), parsed.flags());
        assertEquals(List.of("history.txt"), parsed.args());
    }

    @Test
    void parsesFlagsBetweenArguments() {
        Options.Parsed parsed = Options.parse(
                List.of("history.txt", "--reverse", "c1"), Set.of("--reverse"));

        assertEquals(Set.of("--reverse"), parsed.flags());
        assertEquals(List.of("history.txt", "c1"), parsed.args());
    }

    @Test
    void parsesFlagsAfterArguments() {
        Options.Parsed parsed = Options.parse(
                List.of("history.txt", "c1", "--reverse"), Set.of("--reverse"));

        assertEquals(Set.of("--reverse"), parsed.flags());
        assertEquals(List.of("history.txt", "c1"), parsed.args());
    }

    @Test
    void countsDuplicateOptionsOnce() {
        Options.Parsed parsed = Options.parse(
                List.of("--reverse", "history.txt", "--reverse", "--reverse"), Set.of("--reverse"));

        assertEquals(Set.of("--reverse"), parsed.flags());
        assertEquals(List.of("history.txt"), parsed.args());
    }

    @Test
    void keepsDashAndNegativeLookingValuesAsArguments() {
        Options.Parsed parsed = Options.parse(List.of("-", "-5"), Set.of("--reverse"));

        assertEquals(Set.of(), parsed.flags());
        assertEquals(List.of("-", "-5"), parsed.args());
    }

    @Test
    void reportsTheFirstUnknownOption() {
        Failure failure = assertThrows(
                Failure.class,
                () -> Options.parse(List.of("--write", "--other"), Set.of()));

        assertEquals("unknown option \"--write\"", failure.getMessage());
    }

    @Test
    void rejectsSpecialOptionSpellingsAndQuotesTheirWholeArgument() {
        assertUnknownOption("--", "unknown option \"--\"");
        assertUnknownOption("--help", "unknown option \"--help\"");
        assertUnknownOption("--reverse=true", "unknown option \"--reverse=true\"");
        assertUnknownOption("--😀", "unknown option \"--?\"");
    }

    private static void assertUnknownOption(String argument, String expectedMessage) {
        Failure failure = assertThrows(
                Failure.class,
                () -> Options.parse(List.of(argument), Set.of("--reverse")));

        assertEquals(expectedMessage, failure.getMessage());
    }
}
