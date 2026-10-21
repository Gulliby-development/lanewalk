package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.graph.Bases;
import io.github.gulliby.lanewalk.graph.Listing;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Prints the best common ancestors of revisions. */
public final class BaseCommand implements Command {
    private static final List<String> ARGS = List.of("FILE", "REV", "REV");

    public BaseCommand() {
    }

    @Override
    public String name() {
        return "base";
    }

    @Override
    public String usage() {
        return "lanewalk base FILE REV REV...";
    }

    @Override
    public List<String> args() {
        return ARGS;
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
        List<Integer> revisions = new ArrayList<>(args.size() - 1);
        for (int i = 1; i < args.size(); i++) {
            revisions.add(Revs.resolve(history, args.get(i)));
        }

        List<Integer> ordered = Listing.order(history, Bases.best(history, revisions));
        if (ordered.isEmpty()) {
            return new Result("", 1);
        }

        StringBuilder out = new StringBuilder();
        for (int index : ordered) {
            out.append(history.get(index).id()).append('\n');
        }
        return new Result(out.toString(), 0);
    }
}
