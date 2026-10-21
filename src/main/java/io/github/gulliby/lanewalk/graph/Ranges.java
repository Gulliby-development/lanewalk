package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.BitSet;
import java.util.List;

/** Selects commit sets from revision range expressions. */
public final class Ranges {
    private Ranges() {
    }

    public static BitSet select(History history, String spec) {
        int separator = spec.indexOf("...");
        if (separator >= 0) {
            String leftRevision = spec.substring(0, separator);
            String rightRevision = spec.substring(separator + 3);
            int left = Revs.resolve(history, leftRevision);
            int right = Revs.resolve(history, rightRevision);
            BitSet selected = Reach.reach(history, List.of(left));
            selected.xor(Reach.reach(history, List.of(right)));
            return selected;
        }

        separator = spec.indexOf("..");
        if (separator >= 0) {
            String leftRevision = spec.substring(0, separator);
            String rightRevision = spec.substring(separator + 2);
            int left = Revs.resolve(history, leftRevision);
            int right = Revs.resolve(history, rightRevision);
            BitSet selected = Reach.reach(history, List.of(right));
            selected.andNot(Reach.reach(history, List.of(left)));
            return selected;
        }

        int revision = Revs.resolve(history, spec);
        return Reach.reach(history, List.of(revision));
    }
}
