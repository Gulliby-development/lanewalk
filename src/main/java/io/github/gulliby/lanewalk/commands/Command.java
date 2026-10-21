package io.github.gulliby.lanewalk.commands;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Io;

import java.util.List;
import java.util.Set;

/**
 * A command contract that buffers its complete output before it is emitted.
 */
public interface Command {
    /** Returns this command's name. */
    String name();

    /** Returns the complete usage line for this command. */
    String usage();

    /** Returns the names of the command's required ordinary arguments. */
    List<String> args();

    /**
     * Returns the maximum number of ordinary arguments accepted, or
     * {@link Integer#MAX_VALUE} when there is no limit.
     */
    int max();

    /** Returns the option names accepted by this command. */
    Set<String> specs();

    /**
     * Runs the command with ordinary arguments and parsed flags.
     *
     * @throws Failure when the command cannot complete successfully
     */
    Result run(List<String> args, Set<String> flags, Io io) throws Failure;

    /** A complete buffered standard-output value and its exit code (0 or 1). */
    record Result(String out, int code) {
    }
}
