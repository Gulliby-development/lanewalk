package io.github.gulliby.lanewalk.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import java.util.List;
import org.junit.jupiter.api.Test;

class DescribeTest {
    @Test
    void returnsNullForAnUnlabelledRootAndChain() {
        History history = parse("a\nb a\nc b");

        assertNull(Describe.describe(history, 0));
        assertNull(Describe.describe(history, 2));
    }

    @Test
    void ignoresDisconnectedAndDescendantLabels() {
        History history = parse("a\nb a @descendant\nx @disconnected");

        assertNull(Describe.describe(history, 0));
    }

    @Test
    void returnsTheBareLabelForALabelledRevision() {
        History history = parse("a @z @a");

        assertEquals("a", Describe.describe(history, 0));
    }

    @Test
    void formatsDistanceOneFromItsLabel() {
        History history = parse("a @base\nb a");

        assertEquals("base-1-gb", Describe.describe(history, 1));
    }

    @Test
    void countsUniqueMergeAncestorsInTheReachDifference() {
        History history = parse("a @root\nb a @left\nc a\nm b c");

        assertEquals("left-2-gm", Describe.describe(history, 3));
    }

    @Test
    void prefersLowerDistanceOverAHigherIndexedCandidate() {
        History history = parse("a\nb a @near\nc @later\nd b c");

        assertEquals("near-2-gd", Describe.describe(history, 3));
    }

    @Test
    void prefersHigherIndexOnEqualDistanceBeforeComparingLabels() {
        History history = parse("a\nb a @aaa\nc a @zzz\nd b c");

        assertEquals("zzz-2-gd", Describe.describe(history, 3));
    }

    @Test
    void choosesTheSmallestAsciiLabelRegardlessOfSourceOrder() {
        History history = parse("root @v2 @v1 @V1 @1 @a_b @a-b @a");

        assertEquals("1", Describe.describe(history, 0));
    }

    @Test
    void matchesTheSuppliedHistoryDescriptionsAndUsesTheQueriedId() {
        History history = parse("c1 @v1\nc2 c1\nc3 c1\nc4 c2\nc5 c3 @v2\nm6 c4 c5\nc7 m6\nc8 c5\nm9 c7 c8 @rc");

        assertEquals("v2-4-gc7", Describe.describe(history, 6));
        assertEquals("v1-2-gc4", Describe.describe(history, 3));
        assertEquals("rc", Describe.describe(history, 8));
    }

    @Test
    void repeatedCallsPreserveHistoryAndLabelOrder() {
        History history = parse("a @v2 @v1\nb a");
        List<String> originalLabels = history.get(0).labels();

        assertEquals("v1-1-gb", Describe.describe(history, 1));
        assertEquals("v1-1-gb", Describe.describe(history, 1));
        assertEquals(List.of("v2", "v1"), originalLabels);
        assertEquals(List.of("v2", "v1"), history.get(0).labels());
        assertEquals("a", history.get(0).id());
        assertEquals(List.of(0), history.get(1).parents());
    }

    private static History parse(String text) {
        return Parser.parse(text);
    }
}
