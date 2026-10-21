package io.github.gulliby.lanewalk.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ParserTest {
    @Test
    void reportsInvalidCharactersInParentAndIdTokens() {
        assertProblem("c1\nc2 c1 b!d",
                "HG001", "unexpected character \"!\"", 2, 8);
        assertProblem("c1\nc2 c1 x😀",
                "HG001", "unexpected character \"?\"", 2, 8);
        assertProblem("c1\tx",
                "HG001", "unexpected character \"?\"", 1, 3);
    }

    @Test
    void reportsInvalidCharactersInOtherHeadTokens() {
        assertProblem("c1\nc2 c1 # note",
                "HG001", "unexpected character \"#\"", 2, 7);
        assertProblem("c1 @v!",
                "HG001", "unexpected character \"!\"", 1, 6);
        assertProblem("c1\nc2 c1@v1",
                "HG001", "unexpected character \"@\"", 2, 6);
    }

    @Test
    void reportsMissingIds() {
        assertProblem("@v1 c1",
                "HG002", "missing commit id", 1, 1);
        assertProblem("  | note",
                "HG002", "missing commit id", 1, 3);
    }

    @Test
    void reportsDuplicateIdsAndUnknownParents() {
        assertProblem("c1\nc1",
                "HG003", "duplicate commit \"c1\"", 2, 1);
        assertProblem("c1\nc2 zz",
                "HG004", "unknown parent \"zz\"", 2, 4);
        assertProblem("c1 c1",
                "HG004", "unknown parent \"c1\"", 1, 4);
    }

    @Test
    void reportsRepeatedParentsAndDuplicateLabels() {
        assertProblem("c1\nc2 c1 c1",
                "HG005", "repeated parent \"c1\"", 2, 7);
        assertProblem("c1 @v1\nc2 c1 @v1",
                "HG006", "duplicate label \"@v1\"", 2, 7);
        assertProblem("c1 @v1 @v1",
                "HG006", "duplicate label \"@v1\"", 1, 8);
    }

    @Test
    void reportsEmptyLabelsAndForwardParents() {
        assertProblem("c1 @",
                "HG007", "empty label", 1, 4);
        assertProblem("c1 c2\nc2",
                "HG004", "unknown parent \"c2\"", 1, 4);
        assertProblem("c1\rc2",
                "HG001", "unexpected character \"?\"", 1, 3);
    }

    @Test
    void reportsAsciiAndUnicodeNameViolations() {
        assertProblem("c1 .",
                "HG001", "unexpected character \".\"", 1, 4);
        assertProblem("c1 @é",
                "HG001", "unexpected character \"?\"", 1, 5);
    }

    @Test
    void countsPhysicalLinesAcrossBlankAndCommentLines() {
        assertProblem(" \n # comment\nc1 zz",
                "HG004", "unknown parent \"zz\"", 3, 4);
    }

    @Test
    void reportsProblemContract() {
        Problem problem = new Problem("HG004", "unknown parent \"zz\"", 2, 4);

        assertEquals("unknown parent \"zz\"", problem.getMessage());
        assertEquals("HG004", problem.code());
        assertEquals(2, problem.line());
        assertEquals(4, problem.col());
        assertEquals("HG004 unknown parent \"zz\" at 2:4", problem.render());
    }

    @Test
    void reportsFirstCompetingProblemInTokenOrder() {
        assertProblem("c1 zz b!d",
                "HG004", "unknown parent \"zz\"", 1, 4);
        assertProblem("c1 zz!",
                "HG001", "unexpected character \"!\"", 1, 6);
        assertProblem("c1\nc1 b!d",
                "HG003", "duplicate commit \"c1\"", 2, 1);
        assertProblem("c1\nc1!",
                "HG001", "unexpected character \"!\"", 2, 3);
        assertProblem("c1\nc2 c1 c1 @",
                "HG005", "repeated parent \"c1\"", 2, 7);
        assertProblem("c1 @v\nc2 @v zz",
                "HG006", "duplicate label \"@v\"", 2, 4);
        assertProblem("c1 @ b!d",
                "HG007", "empty label", 1, 4);
        assertProblem("@! c1",
                "HG002", "missing commit id", 1, 1);
    }

    private static void assertProblem(String input, String code, String message, int line, int col) {
        Problem problem = assertThrows(Problem.class, () -> Parser.parse(input));

        assertEquals(code, problem.code());
        assertEquals(message, problem.getMessage());
        assertEquals(line, problem.line());
        assertEquals(col, problem.col());
        assertEquals(code + " " + message + " at " + line + ":" + col, problem.render());
    }
}
