package io.github.gulliby.lanewalk.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class BasesTest {
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

    private static final History CRISS_CROSS = Parser.parse(
            "r\n"
                    + "a r\n"
                    + "b r\n"
                    + "x a b\n"
                    + "y b a\n");

    @Test
    void retainsTheSharedRootAsTheOnlyBestBase() {
        assertBases(HISTORY, List.of(3, 7), List.of(0));
    }

    @Test
    void selectsTheNearestCommonAncestor() {
        assertBases(HISTORY, List.of(6, 7), List.of(4));
    }

    @Test
    void keepsAnAncestorEndpointAndACommitAsItsOwnBase() {
        assertBases(HISTORY, List.of(1, 3), List.of(1));
        assertBases(HISTORY, List.of(8, 8), List.of(8));
    }

    @Test
    void duplicateRevisionDoesNotChangeTheBestBase() {
        assertBases(HISTORY, List.of(6, 7, 7), List.of(4));
    }

    @Test
    void intersectsEveryRevisionIndependentOfInputOrder() {
        assertBases(HISTORY, List.of(6, 7, 3), List.of(0));
        assertBases(HISTORY, List.of(3, 6, 7), List.of(0));
    }

    @Test
    void disconnectedRevisionsHaveNoCommonBase() {
        assertBases(DISCONNECTED, List.of(2, 3), List.of());
        assertBases(DISCONNECTED, List.of(0, 2, 3), List.of());
    }

    @Test
    void preservesIncomparableBasesAndNarrowsThemForAnAdditionalRevision() {
        assertBases(CRISS_CROSS, List.of(3, 4), List.of(1, 2));
        assertBases(CRISS_CROSS, List.of(3, 4, 1), List.of(1));
    }

    @Test
    void returnedBitSetsCallerRevisionsAndHistoryRemainIndependent() {
        List<Integer> revisions = new ArrayList<>(List.of(6, 7));
        BitSet first = Bases.best(HISTORY, revisions);
        BitSet second = Bases.best(HISTORY, revisions);

        assertEquals(List.of(4), indexes(first));
        assertEquals(List.of(4), indexes(second));

        first.clear();
        first.set(8);

        assertEquals(List.of(4), indexes(second));
        assertEquals(List.of(4), indexes(Bases.best(HISTORY, revisions)));
        assertEquals(List.of(6, 7), revisions);
        assertEquals(List.of("c1", "c2", "c3", "c4", "c5", "m6", "c7", "c8", "m9"),
                HISTORY.commits().stream().map(commit -> commit.id()).toList());
        assertEquals(List.of(3, 4), HISTORY.get(5).parents());
        assertEquals(List.of(5, 7), HISTORY.children(4));
    }

    private static void assertBases(History history, List<Integer> revisions, List<Integer> expected) {
        assertEquals(expected, indexes(Bases.best(history, revisions)), revisions.toString());
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
