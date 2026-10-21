package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.Commit;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Literal examples for lane allocation, rendering, and cleanup. */
class LanesTest {
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

    @Test
    void emptyOrderReturnsNoRows() {
        assertEquals(List.of(), Lanes.rows(Parser.parse("r1\n"), List.of()));
    }

    @Test
    void singleRootUsesOneStar() {
        assertEquals(List.of("* root"), Lanes.rows(Parser.parse("root\n"), List.of(0)));
    }

    @Test
    void linearHistoryReusesItsLane() {
        History history = Parser.parse("root\nchild root\nleaf child\n");
        assertEquals(List.of("* leaf", "* child", "* root"),
                Lanes.rows(history, List.of(2, 1, 0)));
    }

    @Test
    void drawsTheCompleteNineCommitExample() {
        History history = Parser.parse(HISTORY);
        assertEquals(List.of(
                "*-+ m9 (@rc) merge experiment",
                "* | c7 docs",
                "*-|-+ m6 merge lexer work",
                "* | | c4 parser fix",
                "* | | c2 parser",
                "| * | c8 experiment",
                "| *-+ c5 (@v2) lexer fix",
                "| * c3 lexer",
                "*-+ c1 (@v1) start"),
                Lanes.rows(history, List.of(8, 6, 5, 3, 1, 7, 4, 2, 0)));
    }

    @Test
    void drawsTheCompleteOctopusAndDisconnectedRootsExample() {
        History history = Parser.parse("""
                r1 | root one
                r2 | root two
                a3 r1 | on one
                b4 r2 | on two
                x5 r1 | side
                o6 a3 b4 x5 | octopus
                t7 o6
                """);
        assertEquals(List.of(
                "* t7",
                "*-+-+ o6 octopus",
                "* | | a3 on one",
                "| * | b4 on two",
                "| * | r2 root two",
                "|   * x5 side",
                "*- -+ r1 root one"),
                Lanes.rows(history, List.of(6, 5, 2, 3, 1, 4, 0)));
    }

    @Test
    void joinsDuplicateWaitingLanesAndKeepsTheLeftmostColumn() {
        History history = Parser.parse("root\nleft root\nright root\n");
        assertEquals(List.of("* left", "| * right", "*-+ root"),
                Lanes.rows(history, List.of(1, 2, 0)));
    }

    @Test
    void reusesTheLeftmostInteriorHole() {
        History history = Parser.parse("r1\nr2\nr3\nmerge r1 r2 r3\nnew\n");
        assertEquals(List.of(
                "*-+-+ merge",
                "* | | r1",
                "  | * r3",
                "* | new"),
                Lanes.rows(history, List.of(3, 0, 2, 4)));
    }

    @Test
    void aMatchingLaneIsPreferredToAnEarlierHole() {
        History history = Parser.parse("r1\nr2\nr3\nmerge r1 r2 r3\n");
        assertEquals(List.of(
                "*-+-+ merge",
                "* | | r1",
                "  | * r3",
                "  * r2"),
                Lanes.rows(history, List.of(3, 0, 2, 1)));
    }

    @Test
    void secondaryParentGetsANewLaneEvenWhenItAlreadyHasOne() {
        History history = Parser.parse("p\nq\na p\nx q p\n");
        assertEquals(List.of("* a", "| *-+ x"), Lanes.rows(history, List.of(2, 3)));
    }

    @Test
    void trailingLanesAreCleanedAndCallsAreIndependent() {
        History history = Parser.parse("root\nchild root\n");
        List<Integer> order = List.of(1, 0);
        assertEquals(List.of("* child", "* root"), Lanes.rows(history, order));
        assertEquals(List.of("* child", "* root"), Lanes.rows(history, order));
        assertEquals(List.of(1, 0), order);
    }

    @Test
    void suppliedPartialOrderDoesNotAddAncestors() {
        History history = Parser.parse("root\nchild root\nleaf child\n");
        assertEquals(List.of("* leaf"), Lanes.rows(history, List.of(2)));
    }

    @Test
    void preservesLabelOrderAndExactSubjectText() {
        History history = new History(List.of(
                new Commit(0, "id", List.of(), List.of("z", "a"), "\t café | edge  "),
                new Commit(1, "empty", List.of(), List.of(), "")));
        assertEquals(List.of("* id (@z, @a) \t café | edge  ", "* empty"),
                Lanes.rows(history, List.of(0, 1)));
    }
}
