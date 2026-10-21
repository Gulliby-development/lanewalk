package io.github.gulliby.lanewalk.base;

import java.io.IOException;

/** Input and output boundary used by commands. */
public interface Io {
    byte[] readStdin() throws IOException;

    byte[] readFile(String path) throws IOException;

    void out(String text);

    void err(String text);
}
