package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.base.Sources;
import io.github.gulliby.lanewalk.base.Text;
import io.github.gulliby.lanewalk.history.Commit;
import io.github.gulliby.lanewalk.history.History;
import io.github.gulliby.lanewalk.history.Parser;
import io.github.gulliby.lanewalk.history.Problem;

import java.util.List;
import java.util.Set;

/** Validates a history and reports its summary statistics. */
public final class CheckCommand implements Command {
    private static final List<String> ARGUMENTS = List.of("FILE");
    private static final Set<String> OPTIONS = Set.of();

    public CheckCommand() {
    }

    @Override
    public String name() {
        return "check";
    }

    @Override
    public String usage() {
        return "lanewalk check FILE";
    }

    @Override
    public List<String> args() {
        return ARGUMENTS;
    }

    @Override
    public int max() {
        return 1;
    }

    @Override
    public Set<String> specs() {
        return OPTIONS;
    }

    @Override
    public Result run(List<String> args, Set<String> flags, Io io) {
        Sources.Source source = Sources.read(io, args.get(0));
        History history;
        try {
            history = Parser.parse(source.text());
        } catch (Problem problem) {
            return new Result(problem.render() + "\n1 problem\n", 1);
        }
        if (history.size() == 0) {
            throw new Failure(source.display() + ": no commits");
        }

        int roots = 0;
        int merges = 0;
        int labels = 0;
        boolean[] hasChild = new boolean[history.size()];
        int[] longestEndingAt = new int[history.size()];
        int longest = 0;

        for (int index = 0; index < history.size(); index++) {
            Commit commit = history.get(index);
            if (commit.parents().isEmpty()) {
                roots++;
                longestEndingAt[index] = 1;
            } else {
                int depth = 0;
                for (int parent : commit.parents()) {
                    hasChild[parent] = true;
                    depth = Math.max(depth, longestEndingAt[parent]);
                }
                longestEndingAt[index] = depth + 1;
            }
            if (commit.parents().size() >= 2) {
                merges++;
            }
            labels += commit.labels().size();
            longest = Math.max(longest, longestEndingAt[index]);
        }

        int heads = 0;
        for (boolean child : hasChild) {
            if (!child) {
                heads++;
            }
        }

        StringBuilder output = new StringBuilder()
                .append("ok, ")
                .append(Text.plural(history.size(), "commit"))
                .append('\n');
        output.append(Text.line("roots", Integer.toString(roots)));
        output.append(Text.line("heads", Integer.toString(heads)));
        output.append(Text.line("merges", Integer.toString(merges)));
        output.append(Text.line("labels", Integer.toString(labels)));
        output.append(Text.line("longest", Integer.toString(longest)));
        return new Result(output.toString(), 0);
    }
}
