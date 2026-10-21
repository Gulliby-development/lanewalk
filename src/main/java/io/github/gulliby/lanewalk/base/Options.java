package io.github.gulliby.lanewalk.base;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Scans command-line options while retaining ordinary arguments in order. */
public final class Options {
    private Options() {
    }

    public record Parsed(Set<String> flags, List<String> args) {
    }

    public static Parsed parse(List<String> argv, Set<String> specs) {
        Set<String> flags = new LinkedHashSet<>();
        List<String> args = new ArrayList<>();
        for (String arg : argv) {
            if (arg.startsWith("--")) {
                if (!specs.contains(arg)) {
                    throw new Failure("unknown option " + Text.show(arg));
                }
                flags.add(arg);
            } else {
                args.add(arg);
            }
        }
        return new Parsed(flags, args);
    }
}
