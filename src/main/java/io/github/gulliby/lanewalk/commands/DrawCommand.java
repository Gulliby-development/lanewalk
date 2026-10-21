package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.graph.Lanes;
import io.github.gulliby.lanewalk.graph.Listing;
import io.github.gulliby.lanewalk.graph.Reach;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

/** Renders a deterministic drawing of all commits or a selected revision reach. */
public final class DrawCommand implements Command {
    public DrawCommand() {
    }

    @Override
    public String name() {
        return "draw";
    }

    @Override
    public String usage() {
        return "lanewalk draw FILE [REV...]";
    }

    @Override
    public List<String> args() {
        return List.of("FILE");
    }

    @Override
    public int max() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Set<String> specs() {
        return Set.of();
    }

    @Override
    public Result run(List<String> args, Set<String> flags, Io io) {
        History history = Loader.load(io, args.get(0));

        BitSet selected;
        if (args.size() == 1) {
            selected = Reach.all(history);
        } else {
            List<Integer> revisions = new ArrayList<>(args.size() - 1);
            for (int i = 1; i < args.size(); i++) {
                revisions.add(Revs.resolve(history, args.get(i)));
            }
            selected = Reach.reach(history, revisions);
        }

        List<Integer> order = Listing.order(history, selected);
        List<String> rows = Lanes.rows(history, order);
        StringBuilder out = new StringBuilder();
        for (String row : rows) {
            out.append(row).append('\n');
        }
        return new Result(out.toString(), 0);
    }
}
