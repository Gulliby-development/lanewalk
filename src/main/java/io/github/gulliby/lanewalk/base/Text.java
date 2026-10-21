package io.github.gulliby.lanewalk.base;

/** Deterministic formatting helpers for user-facing text. */
public final class Text {
    private Text() {
    }

    public static String show(String text) {
        StringBuilder result = new StringBuilder(text.length() + 2);
        result.append('"');
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            if (codePoint >= 0x20 && codePoint <= 0x7e) {
                result.appendCodePoint(codePoint);
            } else {
                result.append('?');
            }
            offset += Character.charCount(codePoint);
        }
        return result.append('"').toString();
    }

    public static String line(String key, String value) {
        return key + " ".repeat(Math.max(0, 10 - key.length())) + "  " + value + "\n";
    }

    public static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
