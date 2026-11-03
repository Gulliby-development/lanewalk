package io.github.gulliby.lanewalk.history;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.gulliby.lanewalk.base.Failure;
import org.junit.jupiter.api.Test;

class RevsLabelPrefixTest {
    private static final History HISTORY = Parser.parse(
            "c1 @v1 | start\n"
                    + "c2 c1 @release | second\n"
                    + "c3 c2 @rc1 | third\n"
                    + "c4 c3 @rc10 | fourth\n"
                    + "c5 c4 @stable | fifth\n");

    @Test
    void aUniqueLabelPrefixResolvesToItsCommit() {
        assertEquals(0, Revs.resolve(HISTORY, "@v"));
        assertEquals(1, Revs.resolve(HISTORY, "@rel"));
        assertEquals(4, Revs.resolve(HISTORY, "@s"));
        assertEquals(4, Revs.resolve(HISTORY, "@stable"));
    }

    @Test
    void anExactLabelWinsOverLongerLabels() {
        assertEquals(2, Revs.resolve(HISTORY, "@rc1"));
        assertEquals(3, Revs.resolve(HISTORY, "@rc10"));
    }

    @Test
    void aPrefixSharedByLabelsIsAmbiguous() {
        Failure shared = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, "@r"));
        assertEquals("ambiguous revision \"@r\"", shared.getMessage());
        Failure withSuffix = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, "@rc^"));
        assertEquals("ambiguous revision \"@rc^\"", withSuffix.getMessage());
    }

    @Test
    void aPrefixOfNoLabelIsUnknown() {
        Failure none = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, "@x"));
        assertEquals("unknown revision \"@x\"", none.getMessage());
        Failure longer = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, "@stable2"));
        assertEquals("unknown revision \"@stable2\"", longer.getMessage());
    }

    @Test
    void suffixesApplyToTheAbbreviatedLabel() {
        assertEquals(0, Revs.resolve(HISTORY, "@rel^"));
        assertEquals(2, Revs.resolve(HISTORY, "@st~2"));
        assertEquals(4, Revs.resolve(HISTORY, "@st~0"));
    }

    @Test
    void aCommitIdIsNeverReadAsALabelPrefix() {
        assertEquals(0, Revs.resolve(HISTORY, "c1"));
        Failure id = assertThrows(Failure.class, () -> Revs.resolve(HISTORY, "rel"));
        assertEquals("unknown revision \"rel\"", id.getMessage());
    }
}
