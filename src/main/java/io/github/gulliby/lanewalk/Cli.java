package io.github.gulliby.lanewalk;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.base.Options;
import io.github.gulliby.lanewalk.base.Text;
import io.github.gulliby.lanewalk.commands.BaseCommand;
import io.github.gulliby.lanewalk.commands.BisectCommand;
import io.github.gulliby.lanewalk.commands.CheckCommand;
import io.github.gulliby.lanewalk.commands.Command;
import io.github.gulliby.lanewalk.commands.DescribeCommand;
import io.github.gulliby.lanewalk.commands.DrawCommand;
import io.github.gulliby.lanewalk.commands.OrderCommand;
import io.github.gulliby.lanewalk.commands.RangeCommand;

import java.util.List;

/** Dispatches command-line arguments to the concrete lanewalk commands. */
public final class Cli {
    private static final List<Command> COMMANDS = List.of(
            new CheckCommand(),
            new OrderCommand(),
            new RangeCommand(),
            new BaseCommand(),
            new DescribeCommand(),
            new BisectCommand(),
            new DrawCommand());

    private Cli() {
    }

    public static int run(List<String> argv, Io io) {
        try {
            if (argv.isEmpty()) {
                throw new Failure("missing command (check, order, range, base, describe, bisect or draw)");
            }

            if (argv.size() == 1 && argv.get(0).equals("--help")) {
                io.out(help());
                return 0;
            }

            Command command = findCommand(argv.get(0));
            if (command == null) {
                throw new Failure("unknown command " + Text.show(argv.get(0)));
            }

            Options.Parsed parsed = Options.parse(argv.subList(1, argv.size()), command.specs());
            List<String> args = parsed.args();
            if (args.size() < command.args().size()) {
                throw new Failure("missing " + command.args().get(args.size()));
            }
            if (args.size() > command.max()) {
                throw new Failure("too many arguments");
            }

            Command.Result result = command.run(args, parsed.flags(), io);
            io.out(result.out());
            return result.code();
        } catch (Failure failure) {
            io.err("lanewalk: " + failure.getMessage() + "\n");
            return 2;
        }
    }

    private static Command findCommand(String name) {
        for (Command command : COMMANDS) {
            if (command.name().equals(name)) {
                return command;
            }
        }
        return null;
    }

    private static String help() {
        StringBuilder output = new StringBuilder();
        for (int index = 0; index < COMMANDS.size(); index++) {
            output.append(index == 0 ? "usage: " : "       ")
                    .append(COMMANDS.get(index).usage())
                    .append('\n');
        }
        return output.toString();
    }
}
