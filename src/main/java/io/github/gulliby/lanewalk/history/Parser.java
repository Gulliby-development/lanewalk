package io.github.gulliby.lanewalk.history;

import io.github.gulliby.lanewalk.base.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parses the ordered, line-oriented history format. */
public final class Parser {
    private Parser() {
    }

    public static History parse(String text) {
        List<Commit> commits = new ArrayList<>();
        Map<String, Integer> ids = new HashMap<>();
        Set<String> labels = new HashSet<>();

        int lineNumber = 1;
        int lineStart = 0;
        while (lineStart < text.length()) {
            int newline = text.indexOf('\n', lineStart);
            boolean hasNewline = newline >= 0;
            int lineEnd = hasNewline ? newline : text.length();
            if (lineEnd > lineStart && text.charAt(lineEnd - 1) == '\r') {
                lineEnd--;
            }

            parseLine(text.substring(lineStart, lineEnd), lineNumber, commits, ids, labels);

            if (!hasNewline) {
                break;
            }
            lineStart = newline + 1;
            lineNumber++;
        }
        return new History(commits);
    }

    private static void parseLine(String line, int lineNumber, List<Commit> commits,
                                  Map<String, Integer> ids, Set<String> knownLabels) {
        int firstContent = 0;
        while (firstContent < line.length() && line.charAt(firstContent) == ' ') {
            firstContent++;
        }
        if (firstContent == line.length() || line.charAt(firstContent) == '#') {
            return;
        }

        int pipe = line.indexOf('|');
        int headEnd = pipe < 0 ? line.length() : pipe;
        int tokenStart = 0;
        while (tokenStart < headEnd && line.charAt(tokenStart) == ' ') {
            tokenStart++;
        }
        if (tokenStart == headEnd) {
            throw new Problem("HG002", "missing commit id", lineNumber,
                    pipe < 0 ? headEnd + 1 : pipe + 1);
        }

        int tokenEnd = tokenEnd(line, tokenStart, headEnd);
        if (line.charAt(tokenStart) == '@') {
            throw new Problem("HG002", "missing commit id", lineNumber, tokenStart + 1);
        }
        validateName(line, tokenStart, tokenEnd, lineNumber);
        String id = line.substring(tokenStart, tokenEnd);
        if (ids.containsKey(id)) {
            throw new Problem("HG003", "duplicate commit " + Text.show(id), lineNumber, tokenStart + 1);
        }

        List<Integer> parents = new ArrayList<>();
        List<String> lineLabels = new ArrayList<>();
        Set<Integer> lineParents = new HashSet<>();
        Set<String> lineLabelNames = new HashSet<>();

        tokenStart = tokenEnd;
        while (tokenStart < headEnd) {
            while (tokenStart < headEnd && line.charAt(tokenStart) == ' ') {
                tokenStart++;
            }
            if (tokenStart == headEnd) {
                break;
            }
            tokenEnd = tokenEnd(line, tokenStart, headEnd);
            if (line.charAt(tokenStart) == '@') {
                if (tokenEnd == tokenStart + 1) {
                    throw new Problem("HG007", "empty label", lineNumber, tokenStart + 1);
                }
                validateName(line, tokenStart + 1, tokenEnd, lineNumber);
                String label = line.substring(tokenStart + 1, tokenEnd);
                if (knownLabels.contains(label) || !lineLabelNames.add(label)) {
                    throw new Problem("HG006", "duplicate label " + Text.show(line.substring(tokenStart, tokenEnd)),
                            lineNumber, tokenStart + 1);
                }
                lineLabels.add(label);
            } else {
                validateName(line, tokenStart, tokenEnd, lineNumber);
                String parent = line.substring(tokenStart, tokenEnd);
                Integer parentIndex = ids.get(parent);
                if (parentIndex == null) {
                    throw new Problem("HG004", "unknown parent " + Text.show(parent), lineNumber, tokenStart + 1);
                }
                if (!lineParents.add(parentIndex)) {
                    throw new Problem("HG005", "repeated parent " + Text.show(parent), lineNumber, tokenStart + 1);
                }
                parents.add(parentIndex);
            }
            tokenStart = tokenEnd;
        }

        int subjectStart = pipe < 0 ? line.length() : pipe + 1;
        int subjectEnd = line.length();
        while (subjectStart < subjectEnd && line.charAt(subjectStart) == ' ') {
            subjectStart++;
        }
        while (subjectEnd > subjectStart && line.charAt(subjectEnd - 1) == ' ') {
            subjectEnd--;
        }
        String subject = line.substring(subjectStart, subjectEnd);

        int index = commits.size();
        commits.add(new Commit(index, id, parents, lineLabels, subject));
        ids.put(id, index);
        knownLabels.addAll(lineLabelNames);
    }

    private static int tokenEnd(String line, int start, int headEnd) {
        int end = start;
        while (end < headEnd && line.charAt(end) != ' ') {
            end++;
        }
        return end;
    }

    private static void validateName(String line, int start, int end, int lineNumber) {
        for (int offset = start; offset < end;) {
            int codePoint = line.codePointAt(offset);
            if (!isNameCharacter(codePoint)) {
                String character = line.substring(offset, offset + Character.charCount(codePoint));
                throw new Problem("HG001", "unexpected character " + Text.show(character),
                        lineNumber, offset + 1);
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static boolean isNameCharacter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '_' || codePoint == '-';
    }
}
