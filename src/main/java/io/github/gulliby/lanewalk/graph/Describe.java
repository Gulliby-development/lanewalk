package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.History;

import java.util.BitSet;
import java.util.List;

/** Describes a commit by its nearest labelled ancestor. */
public final class Describe {
    private Describe() {
    }

    public static String describe(History history, int rev) {
        BitSet revisionReach = Reach.reach(history, List.of(rev));
        int bestDistance = Integer.MAX_VALUE;
        int bestIndex = -1;
        String bestLabel = null;

        for (int candidate = revisionReach.nextSetBit(0);
             candidate >= 0;
             candidate = revisionReach.nextSetBit(candidate + 1)) {
            List<String> labels = history.get(candidate).labels();
            if (labels.isEmpty()) {
                continue;
            }

            BitSet distanceSet = (BitSet) revisionReach.clone();
            distanceSet.andNot(Reach.reach(history, List.of(candidate)));
            int distance = distanceSet.cardinality();

            if (distance < bestDistance || (distance == bestDistance && candidate > bestIndex)) {
                bestDistance = distance;
                bestIndex = candidate;
                bestLabel = labels.stream().min(String::compareTo).orElseThrow();
            }
        }

        if (bestLabel == null) {
            return null;
        }
        if (bestDistance == 0) {
            return bestLabel;
        }
        return bestLabel + "-" + bestDistance + "-g" + history.get(rev).id();
    }
}
