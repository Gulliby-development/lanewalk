package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.graph.Describe;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.List;
import java.util.Set;

/** Prints the nearest labelled ancestor description of a revision. */
public final class DescribeCommand implements Command {
    private static final List<String> ARGS = List.of("FILE", "REV");

    public DescribeCommand() {
    }

    @Override
    public String name() {
        return "describe";
    }

    @Override
    public String usage() {
        return "lanewalk describe FILE REV";
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
        return Set.of();
    }

    @Override
    public Result run(List<String> args, Set<String> flags, Io io) {
        History history = Loader.load(io, args.get(0));
        int revision = Revs.resolve(history, args.get(1));
        String description = Describe.describe(history, revision);
        if (description == null) {
            return new Result("", 1);
        }
        return new Result(description + '\n', 0);
    }
}
