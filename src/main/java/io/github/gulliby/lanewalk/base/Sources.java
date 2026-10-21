package io.github.gulliby.lanewalk.base;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Reads and strictly decodes command input sources. */
public final class Sources {
    private Sources() {
    }

    public record Source(String display, String text) {
    }

    public static Source read(Io io, String arg) {
        boolean stdin = arg.equals("-");
        String display = stdin ? "<stdin>" : Text.show(arg);
        byte[] bytes;
        try {
            bytes = stdin ? io.readStdin() : io.readFile(arg);
        } catch (IOException exception) {
            throw new Failure(display + ": cannot read the file");
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new Failure(display + ": input is not valid UTF-8");
        }

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        return new Source(display, text);
    }
}
