package io.github.gulliby.lanewalk.base;

/** An expected user-facing failure in lanewalk. */
public final class Failure extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public Failure(String message) {
        super(message);
    }
}
