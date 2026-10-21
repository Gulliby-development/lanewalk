package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.graph.Listing;
import io.github.gulliby.lanewalk.graph.Ranges;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;

import java.util.List;
import java.util.Set;

/** Prints commits selected by a revision range. */
public final class RangeCommand implements Command {
    private static final List<String> ARGS = List.of("FILE", "SPEC");
    private static final Set<String> SPECS = Set.of("--reverse");

    public RangeCommand() {
    }

    @Override
    public String name() {
        return "range";
    }

    @Override
    public String usage() {
        return "lanewalk range FILE SPEC [--reverse]";
    }

    @Override
    public List<String> args() {
        return ARGS;
    }

    @Override
    public int max() {
        return 2;
    }

    @Override
    public Set<String> specs() {
        return SPECS;
    }

    @Override
    public Result run(List<String> args, Set<String> flags, Io io) {
        History history = Loader.load(io, args.get(0));
        List<Integer> ordered = Listing.order(history, Ranges.select(history, args.get(1)));
        if (ordered.isEmpty()) {
            return new Result("", 1);
        }

        StringBuilder out = new StringBuilder();
        if (flags.contains("--reverse")) {
            for (int i = ordered.size() - 1; i >= 0; i--) {
                out.append(history.get(ordered.get(i)).id()).append('\n');
            }
        } else {
            for (int index : ordered) {
                out.append(history.get(index).id()).append('\n');
            }
        }
        return new Result(out.toString(), 0);
    }
}
