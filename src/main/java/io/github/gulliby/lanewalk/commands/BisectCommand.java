package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.base.Text;
import io.github.gulliby.lanewalk.graph.Bisect;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Loader;
import io.github.gulliby.lanewalk.history.Revs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Renders a deterministic next-step report for a commit bisect. */
public final class BisectCommand implements Command {
    public BisectCommand() {
    }

    @Override
    public String name() {
        return "bisect";
    }

    @Override
    public String usage() {
        return "lanewalk bisect FILE BAD GOOD...";
    }

    @Override
    public List<String> args() {
        return List.of("FILE", "BAD", "GOOD");
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
        int bad = Revs.resolve(history, args.get(1));

        List<Integer> goods = new ArrayList<>(args.size() - 2);
        for (int i = 2; i < args.size(); i++) {
            goods.add(Revs.resolve(history, args.get(i)));
        }

        Bisect.Plan plan = Bisect.plan(history, bad, goods);
        if (plan.done()) {
            return new Result(Text.line("first", history.get(plan.commit()).id()), 0);
        }

        String out = Text.line("next", history.get(plan.commit()).id())
                + Text.line("left", Text.plural(plan.left(), "commit"))
                + Text.line("steps", "about " + plan.steps());
        return new Result(out, 0);
    }
}
