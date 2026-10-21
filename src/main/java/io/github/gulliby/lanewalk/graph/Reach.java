package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.History;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.List;

/** Computes ancestor sets for commit indexes in a history. */
public final class Reach {
    private Reach() {
    }

    public static BitSet reach(History history, List<Integer> tips) {
        BitSet reached = new BitSet(history.size());
        ArrayDeque<Integer> pending = new ArrayDeque<>();

        for (int tip : tips) {
            if (!reached.get(tip)) {
                reached.set(tip);
                pending.push(tip);
            }
        }

        while (!pending.isEmpty()) {
            int commitIndex = pending.pop();
            for (int parent : history.get(commitIndex).parents()) {
                if (!reached.get(parent)) {
                    reached.set(parent);
                    pending.push(parent);
                }
            }
        }

        return reached;
    }

    public static BitSet all(History history) {
        BitSet commits = new BitSet(history.size());
        commits.set(0, history.size());
        return commits;
    }
}
