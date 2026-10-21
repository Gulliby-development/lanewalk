package io.github.gulliby.lanewalk.history;

/** A structured history parsing problem with its source position. */
public final class Problem extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int line;
    private final int col;

    public Problem(String code, String text, int line, int col) {
        super(text);
        this.code = code;
        this.line = line;
        this.col = col;
    }

    public String code() {
        return code;
    }

    public int line() {
        return line;
    }

    public int col() {
        return col;
    }

    public String render() {
        return code + " " + getMessage() + " at " + line + ":" + col;
    }
}
