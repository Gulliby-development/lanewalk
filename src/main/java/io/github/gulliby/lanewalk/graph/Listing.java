package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.History;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Orders selected commits before their selected parents. */
public final class Listing {
    private Listing() {
    }

    public static List<Integer> order(History history, BitSet set) {
        int[] waiting = new int[history.size()];
        for (int commitIndex = set.nextSetBit(0);
             commitIndex >= 0;
             commitIndex = set.nextSetBit(commitIndex + 1)) {
            for (int parent : history.get(commitIndex).parents()) {
                if (set.get(parent)) {
                    waiting[parent]++;
                }
            }
        }

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        for (int commitIndex = set.nextSetBit(0);
             commitIndex >= 0;
             commitIndex = set.nextSetBit(commitIndex + 1)) {
            if (waiting[commitIndex] == 0) {
                ready.push(commitIndex);
            }
        }

        List<Integer> result = new ArrayList<>(set.cardinality());
        while (!ready.isEmpty()) {
            int commitIndex = ready.pop();
            result.add(commitIndex);

            List<Integer> parents = history.get(commitIndex).parents();
            for (int parentIndex = parents.size() - 1; parentIndex >= 0; parentIndex--) {
                int parent = parents.get(parentIndex);
                if (set.get(parent)) {
                    waiting[parent]--;
                    if (waiting[parent] == 0) {
                        ready.push(parent);
                    }
                }
            }
        }

        return result;
    }
}
