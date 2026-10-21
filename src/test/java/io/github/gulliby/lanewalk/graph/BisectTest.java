package io.github.gulliby.lanewalk.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BisectTest {
    private static final String EXAMPLE_HISTORY = """
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

    @Test
    void suppliedHistoryExamplesChooseTheExpectedNextCommits() {
        History history = history(EXAMPLE_HISTORY);

        assertEquals(new Bisect.Plan(false, 7, 8, 3),
                Bisect.plan(history, history.indexOf("m9"), List.of(history.indexOf("c1"))));
        assertEquals(new Bisect.Plan(false, 5, 6, 3),
                Bisect.plan(history, history.indexOf("m9"),
                        List.of(history.indexOf("c2"), history.indexOf("c3"))));
    }

    @Test
    void aSingleSuspectIsReportedAsDoneAtBad() {
        History history = history(EXAMPLE_HISTORY);

        assertEquals(new Bisect.Plan(true, 3, 1, 0),
                Bisect.plan(history, history.indexOf("c4"), List.of(history.indexOf("c2"))));
    }

    @Test
    void badEqualToGoodIsRejectedWithQuotedIds() {
        History history = history("a");

        Failure failure = assertThrows(Failure.class,
                () -> Bisect.plan(history, 0, List.of(0)));

        assertEquals("bad commit \"a\" is reachable from good commit \"a\"", failure.getMessage());
    }

    @Test
    void firstInvalidGoodIsReportedAfterEarlierValidGoods() {
        History history = history("a\nb a\nc b\nd c\nx");

        Failure failure = assertThrows(Failure.class,
                () -> Bisect.plan(history, history.indexOf("b"),
                        List.of(history.indexOf("x"), history.indexOf("d"))));

        assertEquals("bad commit \"b\" is reachable from good commit \"d\"", failure.getMessage());
    }

    @Test
    void invalidGoodSelectionFollowsArgumentOrder() {
        History history = history("a\nb a\nc b\nd c");

        Failure laterFirst = assertThrows(Failure.class,
                () -> Bisect.plan(history, history.indexOf("b"),
                        List.of(history.indexOf("d"), history.indexOf("c"))));
        Failure earlierFirst = assertThrows(Failure.class,
                () -> Bisect.plan(history, history.indexOf("b"),
                        List.of(history.indexOf("c"), history.indexOf("d"))));

        assertEquals("bad commit \"b\" is reachable from good commit \"d\"", laterFirst.getMessage());
        assertEquals("bad commit \"b\" is reachable from good commit \"c\"", earlierFirst.getMessage());
    }

    @Test
    void disconnectedGoodDoesNotRemoveBadOrItsAncestors() {
        History history = history("a\nb a\nx");

        assertEquals(new Bisect.Plan(false, 0, 2, 1),
                Bisect.plan(history, history.indexOf("b"), List.of(history.indexOf("x"))));
    }

    @Test
    void duplicateAndOverlappingGoodReachesAreSafe() {
        History history = history("a\nb a\nc b");

        assertEquals(new Bisect.Plan(false, 1, 2, 1),
                Bisect.plan(history, history.indexOf("c"),
                        List.of(history.indexOf("a"), history.indexOf("a"))));
        assertEquals(new Bisect.Plan(true, 2, 1, 0),
                Bisect.plan(history, history.indexOf("c"),
                        List.of(history.indexOf("a"), history.indexOf("b"))));
    }

    @Test
    void mergeWeightsCountOnlySuspectAncestors() {
        History history = history("a\ng a\nx g\ny g\nz x\nm z y");

        assertEquals(new Bisect.Plan(false, 4, 4, 2),
                Bisect.plan(history, history.indexOf("m"), List.of(history.indexOf("g"))));
    }

    @Test
    void equalMinimaxScoresChooseTheHigherIndex() {
        History history = history("a\nb a\nc a\nm b c");

        assertEquals(new Bisect.Plan(false, 2, 3, 2),
                Bisect.plan(history, history.indexOf("m"), List.of(history.indexOf("a"))));
    }

    @Test
    void twoSuspectsUseTheBalancedCandidate() {
        History history = history("a\nb a\nc b");

        assertEquals(new Bisect.Plan(false, 1, 2, 1),
                Bisect.plan(history, history.indexOf("c"), List.of(history.indexOf("a"))));
    }

    @Test
    void ceilingLogTwoIsCorrectAtAndBesidePowersOfTwo() {
        History history = history("a\nb a\nc b\nd c\ne d\nf e\ng f\nh g\ni h\nj i");
        int good = history.indexOf("a");

        assertEquals(new Bisect.Plan(true, 1, 1, 0),
                Bisect.plan(history, history.indexOf("b"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 2, 3, 2),
                Bisect.plan(history, history.indexOf("d"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 2, 4, 2),
                Bisect.plan(history, history.indexOf("e"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 3, 5, 3),
                Bisect.plan(history, history.indexOf("f"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 4, 7, 3),
                Bisect.plan(history, history.indexOf("h"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 4, 8, 3),
                Bisect.plan(history, history.indexOf("i"), List.of(good)));
        assertEquals(new Bisect.Plan(false, 5, 9, 4),
                Bisect.plan(history, history.indexOf("j"), List.of(good)));
    }

    @Test
    void repeatedPlanningPreservesGoodsAndHistory() {
        History history = history("a\nb a\nc a\nm b c");
        List<Integer> goods = new ArrayList<>(List.of(history.indexOf("a")));
        List<?> commitsBefore = history.commits();
        Bisect.Plan expected = new Bisect.Plan(false, 2, 3, 2);

        assertEquals(expected, Bisect.plan(history, history.indexOf("m"), goods));
        assertEquals(expected, Bisect.plan(history, history.indexOf("m"), goods));

        assertEquals(List.of(0), goods);
        assertEquals(commitsBefore, history.commits());
        assertEquals(List.of(1, 2), history.get(history.indexOf("m")).parents());
        assertEquals(List.of("a", "b", "c", "m"),
                history.commits().stream().map(commit -> commit.id()).toList());
    }

    private static History history(String text) {
        return Parser.parse(text);
    }
}
