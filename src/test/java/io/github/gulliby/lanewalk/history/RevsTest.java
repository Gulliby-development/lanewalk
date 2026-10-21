package io.github.gulliby.lanewalk.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import org.junit.jupiter.api.Test;

class RevsTest {
    private static final History HISTORY = Parser.parse(
            "c1 @v1\n"
                    + "c2 c1\n"
                    + "c3 c1\n"
                    + "c4 c2\n"
                    + "c5 c3 @v2\n"
                    + "m6 c4 c5\n"
                    + "c7 m6\n"
                    + "c8 c5\n"
                    + "m9 c7 c8 @rc");

    @Test
    void resolvesTheFirstAndSecondMergeParents() {
        assertEquals(6, Revs.resolve(HISTORY, "m9^"));
        assertEquals(7, Revs.resolve(HISTORY, "m9^2"));
    }

    @Test
    void appliesSuffixesFromLeftToRight() {
        assertEquals(4, Revs.resolve(HISTORY, "m9^2~1"));
        assertEquals(1, Revs.resolve(HISTORY, "m6^^"));
    }

    @Test
    void selectsTheRequestedParentAndFollowsTheFirstParent() {
        assertEquals(4, Revs.resolve(HISTORY, "m6^2"));
        assertEquals(3, Revs.resolve(HISTORY, "c7~2"));
    }

    @Test
    void resolvesLabelsBeforeApplyingSuffixes() {
        assertEquals(2, Revs.resolve(HISTORY, "@v2~1"));
        assertEquals(4, Revs.resolve(HISTORY, "@rc^2^"));
    }

    @Test
    void resolvesCommitIdsAndLabelsAsBases() {
        assertEquals(0, Revs.resolve(HISTORY, "c1"));
        assertEquals(0, Revs.resolve(HISTORY, "@v1"));
    }

    @Test
    void zeroSuffixCountsLeaveTheCurrentCommitUnchanged() {
        assertEquals(8, Revs.resolve(HISTORY, "m9~0"));
        assertEquals(0, Revs.resolve(HISTORY, "c1^0"));
    }

    @Test
    void permitsLeadingZeroesInSuffixCounts() {
        assertEquals(3, Revs.resolve(HISTORY, "m9~3"));
        assertEquals(0, Revs.resolve(HISTORY, "c4~002"));
    }

    @Test
    void acceptsSixDigitCountsIncludingSixZeroes() {
        assertEquals(7, Revs.resolve(HISTORY, "m9^000002"));
        assertEquals(0, Revs.resolve(HISTORY, "c1~000000^000000"));
    }

    @Test
    void rejectsInvalidRevisionSyntaxBeforeLookingUpTheBase() {
        assertFailure("", "invalid revision \"\"");
        assertFailure(" ", "invalid revision \" \"");
        assertFailure("@", "invalid revision \"@\"");
        assertFailure("c1~x", "invalid revision \"c1~x\"");
        assertFailure("c1~1234567", "invalid revision \"c1~1234567\"");
        assertFailure("c1^0000000", "invalid revision \"c1^0000000\"");
        assertFailure("c1~١", "invalid revision \"c1~?\"");
        assertFailure("c1.", "invalid revision \"c1.\"");
        assertFailure("😀", "invalid revision \"?\"");
        assertFailure("zz~x", "invalid revision \"zz~x\"");
        assertFailure("c1~^x", "invalid revision \"c1~^x\"");
        assertFailure("@rc@", "invalid revision \"@rc@\"");
        assertFailure("c1 ^", "invalid revision \"c1 ^\"");
    }

    @Test
    void reportsUnknownBasesAndCaseSensitiveNames() {
        assertFailure("zz", "unknown revision \"zz\"");
        assertFailure("@zz", "unknown revision \"@zz\"");
        assertFailure("C1", "unknown revision \"C1\"");
        assertFailure("@V1", "unknown revision \"@V1\"");
    }

    @Test
    void reportsSuffixesThatFallOffTheHistory() {
        assertFailure("m9^3", "unknown revision \"m9^3\"");
        assertFailure("c1~", "unknown revision \"c1~\"");
        assertFailure("c1^", "unknown revision \"c1^\"");
        assertFailure("m9~999999", "unknown revision \"m9~999999\"");
        assertFailure("c1^999999", "unknown revision \"c1^999999\"");
    }

    private static void assertFailure(String revision, String expectedMessage) {
        Failure failure = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, revision));

        assertEquals(expectedMessage, failure.getMessage());
    }
}
