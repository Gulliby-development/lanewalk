package io.github.gulliby.lanewalk;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class MainTest {
    private static final String HISTORY_BLOCK = """
```text
# a small project
c1 @v1 | start
c2 c1 | parser
c3 c1 | lexer
c4 c2 | parser fix
c5 c3 @v2 | lexer fix
m6 c4 c5 | merge lexer work
c7 m6 | docs
c8 c5 | experiment
m9 c7 c8 @rc | merge experiment
```
""";

    private static final String DRAW_BLOCK = """
```text
*-+ m9 (@rc) merge experiment
* | c7 docs
*-|-+ m6 merge lexer work
* | | c4 parser fix
* | | c2 parser
| * | c8 experiment
| *-+ c5 (@v2) lexer fix
| * c3 lexer
*-+ c1 (@v1) start
```
""";

    private static final String BISECT_BLOCK = """
```text
next        c8
left        8 commits
steps       about 3
```
""";

    private static final String HELP_BLOCK = """
```text
usage: lanewalk check FILE
       lanewalk order FILE [REV...] [--reverse]
       lanewalk range FILE SPEC [--reverse]
       lanewalk base FILE REV REV...
       lanewalk describe FILE REV
       lanewalk bisect FILE BAD GOOD...
       lanewalk draw FILE [REV...]
```
""";

    @Test
    void documentationContainsExamplesAndListsActualJavaTrees() throws IOException {
        Path repository = Path.of(System.getProperty("user.dir")).toRealPath();
        String readme = Files.readString(repository.resolve("README.md"), StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n');

        assertTrue(readme.contains("## Install"));
        assertTrue(readme.contains("## Usage"));
        assertTrue(readme.contains("## Commands"));
        assertTrue(readme.contains("## Exit codes"));
        assertTrue(readme.contains("## Development"));
        assertTrue(readme.contains("## Layout"));
        assertTrue(readme.contains(HISTORY_BLOCK), "the documented history must match the accepted example");
        assertTrue(readme.contains(DRAW_BLOCK), "the documented drawing must match the accepted output");
        assertTrue(readme.contains(BISECT_BLOCK), "the documented bisect output must match the accepted example");
        assertTrue(readme.contains(HELP_BLOCK), "the documented help block must match the accepted usage");

        List<String> actualPaths = new ArrayList<>();
        actualPaths.addAll(pathsUnder(repository, repository.resolve("src/main/java")));
        actualPaths.addAll(pathsUnder(repository, repository.resolve("src/test/java")));
        Collections.sort(actualPaths);

        List<String> listedPaths = readme.lines()
                .filter(line -> line.startsWith("- `src/") && line.endsWith("`"))
                .map(line -> line.substring(3, line.length() - 1))
                .toList();
        assertEquals(actualPaths, listedPaths);
    }

    @Test
    void malformedUtf8OnStandardInputProducesExactFailureBytes() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(
                java.toString(),
                "-cp",
                "target/classes",
                "io.github.gulliby.lanewalk.Main",
                "check",
                "-")
                .directory(Path.of(System.getProperty("user.dir")).toFile())
                .start();

        try {
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(new byte[]{0x41, (byte) 0xFF, 0x0A});
            }

            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "the CLI process must exit within 30 seconds");

            byte[] stdout;
            byte[] stderr;
            try (InputStream processStdout = process.getInputStream();
                    InputStream processStderr = process.getErrorStream()) {
                stdout = processStdout.readAllBytes();
                stderr = processStderr.readAllBytes();
            }

            assertEquals(2, process.exitValue());
            assertArrayEquals(new byte[0], stdout);
            assertArrayEquals(
                    "lanewalk: <stdin>: input is not valid UTF-8\n".getBytes(StandardCharsets.UTF_8),
                    stderr);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(30, TimeUnit.SECONDS);
            }
        }
    }

    private static List<String> pathsUnder(Path repository, Path tree) throws IOException {
        try (Stream<Path> paths = Files.walk(tree.toRealPath())) {
            return paths
                    .map(path -> repository.relativize(path).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }
}
