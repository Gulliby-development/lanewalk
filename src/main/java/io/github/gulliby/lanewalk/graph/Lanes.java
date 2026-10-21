package io.github.gulliby.lanewalk.graph;

import io.github.gulliby.lanewalk.history.Commit;
import io.github.gulliby.lanewalk.history.History;

import java.util.ArrayList;
import java.util.List;

/** Renders a supplied commit order as a deterministic lane drawing. */
public final class Lanes {
    private Lanes() {
    }

    public static List<String> rows(History history, List<Integer> order) {
        List<Integer> lanes = new ArrayList<>();
        List<String> rows = new ArrayList<>(order.size());

        for (int commitIndex : order) {
            Commit commit = history.get(commitIndex);
            int column = leftmostLane(lanes, history, commit.id());
            if (column < 0) {
                column = lanes.indexOf(null);
                if (column < 0) {
                    column = lanes.size();
                    lanes.add(commitIndex);
                } else {
                    lanes.set(column, commitIndex);
                }
            }

            List<Integer> joins = new ArrayList<>();
            for (int lane = 0; lane < lanes.size(); lane++) {
                if (lane != column && holds(lanes, history, lane, commit.id())) {
                    joins.add(lane);
                }
            }

            List<Integer> newLanes = new ArrayList<>();
            for (int parentPosition = 1; parentPosition < commit.parents().size(); parentPosition++) {
                int parent = commit.parents().get(parentPosition);
                newLanes.add(lanes.size());
                lanes.add(parent);
            }

            rows.add(render(commit, lanes, column, joins, newLanes));

            for (int join : joins) {
                lanes.set(join, null);
            }
            lanes.set(column, commit.parents().isEmpty() ? null : commit.parents().get(0));
            while (!lanes.isEmpty() && lanes.get(lanes.size() - 1) == null) {
                lanes.remove(lanes.size() - 1);
            }
        }

        return List.copyOf(rows);
    }

    private static int leftmostLane(List<Integer> lanes, History history, String id) {
        for (int lane = 0; lane < lanes.size(); lane++) {
            if (holds(lanes, history, lane, id)) {
                return lane;
            }
        }
        return -1;
    }

    private static boolean holds(List<Integer> lanes, History history, int lane, String id) {
        Integer commitIndex = lanes.get(lane);
        return commitIndex != null && history.get(commitIndex).id().equals(id);
    }

    private static String render(Commit commit, List<Integer> lanes, int column,
                                List<Integer> joins, List<Integer> newLanes) {
        int minEndpoint = column;
        int maxEndpoint = column;
        for (int join : joins) {
            minEndpoint = Math.min(minEndpoint, join);
            maxEndpoint = Math.max(maxEndpoint, join);
        }
        for (int newLane : newLanes) {
            minEndpoint = Math.min(minEndpoint, newLane);
            maxEndpoint = Math.max(maxEndpoint, newLane);
        }

        StringBuilder diagram = new StringBuilder();
        for (int lane = 0; lane < lanes.size(); lane++) {
            if (lane == column) {
                diagram.append('*');
            } else if (joins.contains(lane) || newLanes.contains(lane)) {
                diagram.append('+');
            } else if (lanes.get(lane) != null) {
                diagram.append('|');
            } else {
                diagram.append(' ');
            }

            if (lane + 1 < lanes.size()) {
                diagram.append(lane >= minEndpoint && lane < maxEndpoint ? '-' : ' ');
            }
        }

        int diagramEnd = diagram.length();
        while (diagramEnd > 0 && diagram.charAt(diagramEnd - 1) == ' ') {
            diagramEnd--;
        }
        diagram.setLength(diagramEnd);

        StringBuilder row = new StringBuilder(diagram);
        row.append(' ').append(commit.id());
        if (!commit.labels().isEmpty()) {
            row.append(" (@");
            for (int label = 0; label < commit.labels().size(); label++) {
                if (label > 0) {
                    row.append(", @");
                }
                row.append(commit.labels().get(label));
            }
            row.append(')');
        }
        if (!commit.subject().isEmpty()) {
            row.append(' ').append(commit.subject());
        }
        return row.toString();
    }
}
