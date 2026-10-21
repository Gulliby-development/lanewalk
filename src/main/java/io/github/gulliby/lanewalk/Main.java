package io.github.gulliby.lanewalk;

import io.github.gulliby.lanewalk.base.Io;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/** Production entry point for the lanewalk command-line application. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Io io = new Io() {
            @Override
            public byte[] readStdin() throws IOException {
                try {
                    return System.in.readAllBytes();
                } catch (SecurityException exception) {
                    throw new IOException(exception);
                }
            }

            @Override
            public byte[] readFile(String path) throws IOException {
                try {
                    return Files.readAllBytes(Path.of(path));
                } catch (InvalidPathException | SecurityException exception) {
                    throw new IOException(exception);
                }
            }

            @Override
            public void out(String text) {
                write(System.out, text);
            }

            @Override
            public void err(String text) {
                write(System.err, text);
            }
        };

        System.exit(Cli.run(List.of(args), io));
    }

    private static void write(PrintStream stream, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        stream.write(bytes, 0, bytes.length);
        stream.flush();
    }
}
