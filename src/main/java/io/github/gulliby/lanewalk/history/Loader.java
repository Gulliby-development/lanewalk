package io.github.gulliby.lanewalk.history;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;
import io.github.gulliby.lanewalk.base.Sources;

/** Loads and validates a history from a named source or standard input. */
public final class Loader {
    private Loader() {
    }

    public static History load(Io io, String arg) {
        Sources.Source source = Sources.read(io, arg);
        History history;
        try {
            history = Parser.parse(source.text());
        } catch (Problem problem) {
            throw new Failure(source.display() + ": " + problem.render());
        }
        if (history.size() == 0) {
            throw new Failure(source.display() + ": no commits");
        }
        return history;
    }
}
