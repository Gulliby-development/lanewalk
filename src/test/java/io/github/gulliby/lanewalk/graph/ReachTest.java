package io.github.gulliby.lanewalk.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReachTest {
    @Test
    void reachReturnsEmptyForEmptyHistoryAndEmptyTips() {
        assertEquals(List.of(), indexes(Reach.reach(Parser.parse(""), List.of())));
        assertEquals(List.of(), indexes(Reach.reach(Parser.parse("a\nb a\nc b"), List.of())));
    }

    @Test
    void reachIncludesTheRootAndEveryCommitInAChain() {
        History history = Parser.parse("a\nb a\nc b");

        assertEquals(List.of(0), indexes(Reach.reach(history, List.of(0))));
        assertEquals(List.of(0, 1, 2), indexes(Reach.reach(history, List.of(2))));
    }

    @Test
    void reachUnionsRepeatedAndOverlappingMergeTips() {
        History history = Parser.parse("a\nb a\nc a\nm b c");

        assertEquals(List.of(0, 1, 2, 3), indexes(Reach.reach(history, List.of(3, 1, 3))));
    }

    @Test
    void reachCombinesDisconnectedTipsAndExcludesUnrelatedComponents() {
        History history = Parser.parse("a\nb\nc a\nd b");

        assertEquals(List.of(0, 1, 2, 3), indexes(Reach.reach(history, List.of(2, 3))));
        assertEquals(List.of(0, 2), indexes(Reach.reach(history, List.of(2))));
    }

    @Test
    void allSelectsEmptySingletonAndDisconnectedHistories() {
        assertEquals(List.of(), indexes(Reach.all(Parser.parse(""))));
        assertEquals(List.of(0), indexes(Reach.all(Parser.parse("a"))));
        assertEquals(List.of(0, 1, 2, 3),
                indexes(Reach.all(Parser.parse("a\nb\nc a\nd b"))));
    }

    @Test
    void traversalPreservesTipsSelectionAndHistoryData() {
        History history = Parser.parse("a\nb a\nc a\nm b c");
        List<Integer> tips = new ArrayList<>(List.of(3, 1, 3));
        BitSet selection = new BitSet();
        selection.set(0, 4);

        assertEquals(List.of(0, 1, 2, 3), indexes(Reach.reach(history, tips)));
        assertEquals(List.of(3, 1, 2, 0), Listing.order(history, selection));

        assertEquals(List.of(3, 1, 3), tips);
        assertEquals(List.of(0, 1, 2, 3), indexes(selection));
        assertEquals(List.of("a", "b", "c", "m"),
                history.commits().stream().map(commit -> commit.id()).toList());
        assertEquals(List.of(1, 2), history.get(3).parents());
    }

    @Test
    void returnedReachAndAllBitSetsAreIndependent() {
        History history = Parser.parse("a\nb a\nc a\nm b c");
        BitSet firstReach = Reach.reach(history, List.of(3));
        BitSet secondReach = Reach.reach(history, List.of(3));
        BitSet firstAll = Reach.all(history);
        BitSet secondAll = Reach.all(history);

        firstReach.clear();
        firstAll.clear();

        assertEquals(List.of(0, 1, 2, 3), indexes(secondReach));
        assertEquals(List.of(0, 1, 2, 3), indexes(secondAll));
        assertEquals(List.of(0, 1, 2, 3), indexes(Reach.reach(history, List.of(3))));
        assertEquals(List.of(0, 1, 2, 3), indexes(Reach.all(history)));
    }

    @Test
    void repeatedListingHasTheSameOrderAndPreservesItsSelection() {
        History history = Parser.parse("a\nb a\nc a\nm b c");
        BitSet selection = new BitSet();
        selection.set(0, 4);

        assertEquals(List.of(3, 1, 2, 0), Listing.order(history, selection));
        assertEquals(List.of(3, 1, 2, 0), Listing.order(history, selection));
        assertEquals(List.of(0, 1, 2, 3), indexes(selection));
    }

    static Object acceptanceActual(Object input) {
        List<?> fields = (List<?>) input;
        History history = Parser.parse((String) fields.get(0));
        List<?> selectedIndexes = (List<?>) fields.get(1);
        BitSet selected = new BitSet();
        for (Object index : selectedIndexes) {
            selected.set(((Number) index).intValue());
        }
        return Listing.order(history, selected);
    }

    private static List<Integer> indexes(BitSet selected) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = selected.nextSetBit(0);
             index >= 0;
             index = selected.nextSetBit(index + 1)) {
            indexes.add(index);
        }
        return indexes;
    }
}

// BEGIN acceptance rows: host-written from the audited spec table. Edit acceptanceActual, not this block.
class ReachTestAcceptanceRowsTest {
  @org.junit.jupiter.api.TestFactory
  java.util.stream.Stream<org.junit.jupiter.api.DynamicTest> acceptanceRows() {
    return java.util.stream.Stream.of(
        row("AC-004:1", list("", list()), list()),
        row("AC-004:2", list("a\012b a", list()), list()),
        row("AC-004:3", list("a", list(0L)), list(0L)),
        row("AC-004:4", list("a\012b\012c a\012d b", list(0L, 1L, 2L, 3L)), list(3L, 1L, 2L, 0L)),
        row("AC-004:5", list("a\012b a\012c a\012m b c", list(0L, 1L, 2L, 3L)), list(3L, 1L, 2L, 0L)),
        row("AC-004:6", list("a\012b a\012c a\012m c b", list(0L, 1L, 2L, 3L)), list(3L, 2L, 1L, 0L)),
        row("AC-004:7", list("a\012b a\012c b", list(0L, 2L)), list(2L, 0L)),
        row("AC-004:8", list("a\012b a\012c a\012m b c", list(0L, 1L)), list(1L, 0L)),
        row("AC-004:9", list("a\012b\012c\012m a b c", list(0L, 1L, 2L, 3L)), list(3L, 0L, 1L, 2L)),
        row("AC-004:10", list("c1\012c2 c1\012c3 c1\012c4 c2\012c5 c3\012m6 c4 c5\012c7 m6\012c8 c5\012m9 c7 c8", list(0L, 1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)), list(8L, 6L, 5L, 3L, 1L, 7L, 4L, 2L, 0L)));
  }

  private static org.junit.jupiter.api.DynamicTest row(String id, Object input, Object expected) {
    return org.junit.jupiter.api.DynamicTest.dynamicTest(
        id, () -> org.junit.jupiter.api.Assertions.assertEquals(normal(expected), normal(ReachTest.acceptanceActual(input))));
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
