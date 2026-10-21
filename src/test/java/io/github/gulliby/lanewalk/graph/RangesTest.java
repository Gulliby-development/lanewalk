package io.github.gulliby.lanewalk.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class RangesTest {
    private static final History HISTORY = Parser.parse(
            "c1 @v1\n"
                    + "c2 c1\n"
                    + "c3 c1\n"
                    + "c4 c2\n"
                    + "c5 c3 @v2\n"
                    + "m6 c4 c5\n"
                    + "c7 m6\n"
                    + "c8 c5\n"
                    + "m9 c7 c8 @rc\n");

    private static final History DISCONNECTED = Parser.parse(
            "r1\n"
                    + "r2\n"
                    + "a3 r1\n"
                    + "b4 r2\n");

    @Test
    void selectsPlainReachAndLabelSuffixRanges() {
        assertRange("c4", List.of(0, 1, 3));
        assertRange("@v2~1", List.of(0, 2));
    }

    @Test
    void appliesSuffixesAndDirectionalDifference() {
        assertRange("m9^2~1", List.of(0, 2, 4));
        assertRange("c4..m9", List.of(2, 4, 5, 6, 7, 8));
        assertRange("m9..c4", List.of());
    }

    @Test
    void symmetricDifferenceIsOrderIndependent() {
        assertRange("c7...c8", List.of(1, 3, 5, 6, 7));
        assertRange("c8...c7", List.of(1, 3, 5, 6, 7));
    }

    @Test
    void identicalEndpointsHaveEmptyDifferenceAndSymmetricDifference() {
        assertRange("c4..c4", List.of());
        assertRange("c4...c4", List.of());
    }

    @Test
    void disconnectedEndpointsProduceDifferenceAndSymmetricDifference() {
        assertRange(DISCONNECTED, "a3..b4", List.of(1, 3));
        assertRange(DISCONNECTED, "a3...b4", List.of(0, 1, 2, 3));
    }

    @Test
    void rejectsEmptyPlainAndRangeOperands() {
        assertFailure("c4..", "invalid revision \"\"");
        assertFailure("..c4", "invalid revision \"\"");
        assertFailure("...c4", "invalid revision \"\"");
        assertFailure("c4...", "invalid revision \"\"");
        assertFailure("", "invalid revision \"\"");
    }

    @Test
    void splitsAtTheFirstApplicableDelimiter() {
        assertFailure("c1....c2", "invalid revision \".c2\"");
        assertFailure("c1..c2...c3", "invalid revision \"c1..c2\"");
        assertFailure("c1...c2...c3", "invalid revision \"c2...c3\"");
        assertFailure("c1..c2..c3", "invalid revision \"c2..c3\"");
    }

    @Test
    void resolvesLeftRevisionBeforeRightRevision() {
        assertFailure("zz..@", "unknown revision \"zz\"");
        assertFailure("@...zz", "invalid revision \"@\"");
        assertFailure("m9^3...@", "unknown revision \"m9^3\"");
    }

    @Test
    void reportsUnknownRightRevision() {
        assertFailure("c1..zz", "unknown revision \"zz\"");
    }

    @Test
    void returnedBitSetsAreIndependentAndHistoryIsUnchanged() {
        BitSet first = Ranges.select(HISTORY, "c4");
        BitSet second = Ranges.select(HISTORY, "c4");

        assertEquals(List.of(0, 1, 3), indexes(first));
        assertEquals(List.of(0, 1, 3), indexes(second));

        first.clear();
        first.set(8);

        assertEquals(List.of(0, 1, 3), indexes(second));
        assertEquals(List.of(0, 1, 3), indexes(Ranges.select(HISTORY, "c4")));
        assertEquals(List.of("c1", "c2", "c3", "c4", "c5", "m6", "c7", "c8", "m9"),
                HISTORY.commits().stream().map(commit -> commit.id()).toList());
        assertEquals(List.of(1), HISTORY.get(3).parents());
        assertEquals(List.of(3), HISTORY.children(1));
    }

    private static void assertRange(String spec, List<Integer> expected) {
        assertRange(HISTORY, spec, expected);
    }

    private static void assertRange(History history, String spec, List<Integer> expected) {
        assertEquals(expected, indexes(Ranges.select(history, spec)), spec);
    }

    private static void assertFailure(String spec, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Ranges.select(HISTORY, spec));

        assertEquals(expectedMessage, failure.getMessage(), spec);
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
