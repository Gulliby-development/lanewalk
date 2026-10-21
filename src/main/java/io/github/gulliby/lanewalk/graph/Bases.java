package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.History;

import java.util.BitSet;
import java.util.List;

/** Finds the best common ancestors of a set of commits. */
public final class Bases {
    private Bases() {
    }

    public static BitSet best(History history, List<Integer> revs) {
        BitSet common = Reach.reach(history, List.of(revs.get(0)));
        for (int i = 1; i < revs.size(); i++) {
            common.and(Reach.reach(history, List.of(revs.get(i))));
        }

        BitSet best = (BitSet) common.clone();
        for (int index = common.nextSetBit(0); index >= 0; index = common.nextSetBit(index + 1)) {
            best.andNot(Reach.reach(history, history.get(index).parents()));
        }
        return best;
    }
}
