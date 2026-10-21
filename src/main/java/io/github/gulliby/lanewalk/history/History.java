package io.github.gulliby.lanewalk.history;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Commits in accepted order, with indexes for names and child relationships. */
public final class History {
    private final List<Commit> commits;
    private final Map<String, Integer> ids;
    private final Map<String, Integer> labels;
    private final List<List<Integer>> children;

    public History(List<Commit> commits) {
        this.commits = List.copyOf(commits);

        Map<String, Integer> idIndexes = new HashMap<>();
        Map<String, Integer> labelIndexes = new HashMap<>();
        List<List<Integer>> childIndexes = new ArrayList<>(this.commits.size());
        for (int i = 0; i < this.commits.size(); i++) {
            childIndexes.add(new ArrayList<>());
        }

        for (int i = 0; i < this.commits.size(); i++) {
            Commit commit = this.commits.get(i);
            idIndexes.put(commit.id(), i);
            for (String label : commit.labels()) {
                labelIndexes.put(label, i);
            }
            for (int parent : commit.parents()) {
                childIndexes.get(parent).add(i);
            }
        }

        this.ids = Map.copyOf(idIndexes);
        this.labels = Map.copyOf(labelIndexes);
        List<List<Integer>> immutableChildren = new ArrayList<>(childIndexes.size());
        for (List<Integer> childList : childIndexes) {
            immutableChildren.add(List.copyOf(childList));
        }
        this.children = List.copyOf(immutableChildren);
    }

    public int size() {
        return commits.size();
    }

    public Commit get(int index) {
        return commits.get(index);
    }

    public List<Commit> commits() {
        return commits;
    }

    public int indexOf(String id) {
        return ids.getOrDefault(id, -1);
    }

    public int indexOfLabel(String label) {
        return labels.getOrDefault(label, -1);
    }

    public List<Integer> children(int index) {
        return children.get(index);
    }
}
