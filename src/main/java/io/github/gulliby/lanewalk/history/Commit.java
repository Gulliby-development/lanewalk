package io.github.gulliby.lanewalk.history;

import java.util.List;

/** A commit and the parent, label, and subject data written for it. */
public record Commit(int index, String id, List<Integer> parents, List<String> labels, String subject) {
    public Commit {
        parents = List.copyOf(parents);
        labels = List.copyOf(labels);
    }
}
