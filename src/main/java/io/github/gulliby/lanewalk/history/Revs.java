package io.github.gulliby.lanewalk.history;

import io.github.gulliby.lanewalk.base.Failure;
import io.github.gulliby.lanewalk.base.Text;

import java.util.ArrayList;
import java.util.List;

/** Validates revision expressions and resolves them against an ordered history. */
public final class Revs {
    private Revs() {
    }

    private record Suffix(char operator, int count) {
    }

    public static int resolve(History history, String text) {
        int offset = 0;
        String base;

        if (offset < text.length() && text.charAt(offset) == '@') {
            int start = offset++;
            int nameStart = offset;
            while (offset < text.length() && isNameCharacter(text.charAt(offset))) {
                offset++;
            }
            if (offset == nameStart) {
                throw invalid(text);
            }
            base = text.substring(start, offset);
        } else {
            int start = offset;
            while (offset < text.length() && isNameCharacter(text.charAt(offset))) {
                offset++;
            }
            if (offset == start) {
                throw invalid(text);
            }
            base = text.substring(start, offset);
        }

        List<Suffix> suffixes = new ArrayList<>();
        while (offset < text.length()) {
            char operator = text.charAt(offset);
            if (operator != '~' && operator != '^') {
                throw invalid(text);
            }
            offset++;

            int digitStart = offset;
            int count = 0;
            while (offset < text.length() && isDigit(text.charAt(offset))) {
                if (offset - digitStart == 6) {
                    throw invalid(text);
                }
                count = count * 10 + text.charAt(offset) - '0';
                offset++;
            }
            if (offset == digitStart) {
                count = 1;
            }
            suffixes.add(new Suffix(operator, count));
        }

        int current = base.charAt(0) == '@'
                ? history.indexOfLabel(base.substring(1))
                : history.indexOf(base);
        if (current < 0) {
            throw unknown(text);
        }

        for (Suffix suffix : suffixes) {
            if (suffix.count() == 0) {
                continue;
            }
            if (suffix.operator() == '^') {
                List<Integer> parents = history.get(current).parents();
                if (suffix.count() > parents.size()) {
                    throw unknown(text);
                }
                current = parents.get(suffix.count() - 1);
            } else {
                for (int step = 0; step < suffix.count(); step++) {
                    List<Integer> parents = history.get(current).parents();
                    if (parents.isEmpty()) {
                        throw unknown(text);
                    }
                    current = parents.get(0);
                }
            }
        }
        return current;
    }

    private static boolean isNameCharacter(char character) {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || (character >= '0' && character <= '9')
                || character == '_' || character == '-';
    }

    private static boolean isDigit(char character) {
        return character >= '0' && character <= '9';
    }

    private static Failure invalid(String text) {
        return new Failure("invalid revision " + Text.show(text));
    }

    private static Failure unknown(String text) {
        return new Failure("unknown revision " + Text.show(text));
    }
}
