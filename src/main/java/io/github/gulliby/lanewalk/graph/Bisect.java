package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Text;
import io.github.gulliby.lanewalk.history.History;

import java.util.BitSet;
import java.util.List;

/** Computes a deterministic next commit to test in a bisect. */
public final class Bisect {
    private Bisect() {
    }

    /** A bisect result, including the number of remaining suspects and search steps. */
    public record Plan(boolean done, int commit, int left, int steps) {
    }

    public static Plan plan(History history, int bad, List<Integer> goods) {
        BitSet suspects = Reach.reach(history, List.of(bad));

        for (int good : goods) {
            BitSet goodReach = Reach.reach(history, List.of(good));
            if (goodReach.get(bad)) {
                throw new Failure("bad commit " + Text.show(history.get(bad).id())
                        + " is reachable from good commit " + Text.show(history.get(good).id()));
            }
            suspects.andNot(goodReach);
        }

        int count = suspects.cardinality();
        if (count == 1) {
            return new Plan(true, bad, 1, 0);
        }

        int bestCommit = -1;
        int bestScore = Integer.MAX_VALUE;
        for (int candidate = suspects.nextSetBit(0);
             candidate >= 0;
             candidate = suspects.nextSetBit(candidate + 1)) {
            BitSet candidateReach = Reach.reach(history, List.of(candidate));
            candidateReach.and(suspects);
            int reachableSuspects = candidateReach.cardinality();
            int score = Math.max(reachableSuspects, count - reachableSuspects);
            if (score < bestScore || (score == bestScore && candidate > bestCommit)) {
                bestCommit = candidate;
                bestScore = score;
            }
        }

        int steps = Integer.SIZE - Integer.numberOfLeadingZeros(count - 1);
        return new Plan(false, bestCommit, count, steps);
    }
}
