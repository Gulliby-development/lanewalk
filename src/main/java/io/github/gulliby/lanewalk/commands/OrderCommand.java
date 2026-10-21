package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.graph.Listing;
import io.github.gulliby.lanewalk.graph.Reach;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Lists all commits or the ancestors selected by revision expressions. */
public final class OrderCommand implements Command {
    private static final List<String> ARGUMENTS = List.of("FILE");
    private static final Set<String> OPTIONS = Set.of("--reverse");

    public OrderCommand() {
    }

    @Override
    public String name() {
        return "order";
    }

    @Override
    public String usage() {
        return "lanewalk order FILE [REV...] [--reverse]";
    }

    @Override
    public List<String> args() {
        return ARGUMENTS;
    }

    @Override
    public int max() {
        return Integer.MAX_VALUE;
    }

    @Override
    public Set<String> specs() {
        return OPTIONS;
    }

    @Override
    public Result run(List<String> args, Set<String> flags, Io io) {
        History history = Loader.load(io, args.get(0));
        List<Integer> tips = new ArrayList<>(Math.max(0, args.size() - 1));
        for (int index = 1; index < args.size(); index++) {
            tips.add(Revs.resolve(history, args.get(index)));
        }

        BitSet selected = tips.isEmpty() ? Reach.all(history) : Reach.reach(history, tips);
        List<Integer> commits = Listing.order(history, selected);
        if (flags.contains("--reverse")) {
            Collections.reverse(commits);
        }

        StringBuilder output = new StringBuilder();
        for (int commitIndex : commits) {
            output.append(history.get(commitIndex).id()).append('\n');
        }
        return new Result(output.toString(), 0);
    }
}
