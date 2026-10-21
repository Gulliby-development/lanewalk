package io.github.gulliby.lanewalk.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class TextTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-002:1", "", "\"\""),
        row("AC-002:2", " ~", "\" ~\""),
        row("AC-002:3", "a\011b\012c\015", "\"a?b?c?\""),
        row("AC-002:4", "é😀", "\"??\""),
        row("AC-002:5", "\037\177", "\"??\""),
        row("AC-002:6", "a\"b\\c", "\"a\"b\\c\""),
        row("AC-003:1", list("roots", "1"), "roots       1\012"),
        row("AC-003:2", list("abcdefghij", "x"), "abcdefghij  x\012"),
        row("AC-003:3", list("abcdefghijk", "x"), "abcdefghijk  x\012"),
        row("AC-003:4", list("", ""), "            \012"),
        row("AC-004:1", list(1L, "commit"), "1 commit"),
        row("AC-004:2", list(0L, "commit"), "0 commits"),
        row("AC-004:3", list(2L, "commit"), "2 commits"),
        row("AC-004:4", list(-1L, "commit"), "-1 commits"));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(TextTest.acceptanceActual(input))));
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

class TextTest {
    static Object acceptanceActual(Object input) {
        if (input instanceof String text) {
            return Text.show(text);
        }

        List<?> values = (List<?>) input;
        if (values.get(0) instanceof String key) {
            return Text.line(key, (String) values.get(1));
        }
        return Text.plural(((Number) values.get(0)).intValue(), (String) values.get(1));
    }

    @Test
    void showQuotesEmptyText() {
        assertEquals("\"\"", Text.show(""));
    }

    @Test
    void showPreservesPrintableAsciiAtBoundaries() {
        assertEquals("\" ~\"", Text.show(" ~"));
    }

    @Test
    void showReplacesSupplementaryCharactersOnceEach() {
        assertEquals("\"??\"", Text.show("😀𐐷"));
    }

    @Test
    void showReplacesControlsAndNonAsciiCodePoints() {
        assertEquals("\"???\"", Text.show("\u0000\u007fé"));
    }

    @Test
    void showLeavesQuotesAndBackslashesLiteral() {
        assertEquals("\"a\"b\\c\"", Text.show("a\"b\\c"));
    }

    @Test
    void linePadsShortKeysAndEndsWithLf() {
        assertEquals("roots       1\n", Text.line("roots", "1"));
    }

    @Test
    void lineKeepsLongKeysWithoutTruncation() {
        assertEquals("abcdefghijk  x\n", Text.line("abcdefghijk", "x"));
    }

    @Test
    void pluralAddsSuffixOnlyWhenCountIsNotOne() {
        assertEquals("-1 commits", Text.plural(-1, "commit"));
        assertEquals("0 commits", Text.plural(0, "commit"));
        assertEquals("1 commit", Text.plural(1, "commit"));
        assertEquals("2 commits", Text.plural(2, "commit"));
    }
}
