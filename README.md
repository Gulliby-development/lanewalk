<p align="center"><img src="docs/logo.svg" width="96" alt="" /></p>

# lanewalk

lanewalk is an offline command-line tool, written in Java 21 with the standard library only, for people who reason about commit graphs: it answers where branches fork, what a revision range contains, which release a commit is closest to and which commit to test next in a bisect, and it draws the history as ASCII lanes.

[![CI](https://github.com/Gulliby-development/lanewalk/actions/workflows/ci.yml/badge.svg)](https://github.com/Gulliby-development/lanewalk/actions/workflows/ci.yml) [![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

## Why

Git answers these questions only inside a repository. lanewalk works on a small text file that lists commits, parents, labels and subjects (the shape of `git log --pretty=%h %p`), written by hand or by a script. That makes it handy for teaching, for write-ups, for tests of release tooling and for checking an idea about a history that does not exist yet. It never runs Git, reads no environment variable and makes no network call.

## Install

Install a Java 21 JDK and Apache Maven, then build from a checkout:

```text
mvn -B -q package
```

Run it with the Java runtime:

```text
java -cp target/classes io.github.gulliby.lanewalk.Main --help
```

Below, `lanewalk` stands for `java -cp target/classes io.github.gulliby.lanewalk.Main`.

## Usage

`FILE` names a history file, or `-` to read the history from standard input. A revision is a commit id or a label such as `@rc`, optionally followed by parent suffixes: `m9^2~1` selects the first parent of the second parent of `m9`. The range spec `c4` selects a revision and its ancestors, `c4..m9` selects commits reachable from `m9` but not from `c4`, and `c7...c8` selects commits reachable from exactly one of the two revisions.

A commit id may be abbreviated: when no commit has exactly the written id and the id (without its suffixes) is the beginning of exactly one commit id, that commit is meant (`m` selects `m6` when no other id starts with `m`). An exact id always wins (`c1` is `c1` even when `c10` exists). A beginning shared by several ids is the error `ambiguous revision "X"`, and one shared by none is `unknown revision "X"`. Labels are never abbreviated, and commands always print the full ids.

Run `lanewalk --help` to display:

```text
usage: lanewalk check FILE
       lanewalk order FILE [REV...] [--reverse]
       lanewalk range FILE SPEC [--reverse]
       lanewalk base FILE REV REV...
       lanewalk describe FILE REV
       lanewalk bisect FILE BAD GOOD...
       lanewalk draw FILE [REV...]
```

The history file used in the examples, `history.txt`. Each line is `ID [PARENT...] [@LABEL...] [| SUBJECT]`; parents must come on earlier lines, and lines starting with `#` are comments:

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

The command `lanewalk draw history.txt` displays that history as:

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

A bisect example is `lanewalk bisect history.txt m9 c1`:

```text
next        c8
left        8 commits
steps       about 3
```

More examples on the same file:

```text
$ lanewalk check history.txt
ok, 9 commits
roots       1
heads       1
merges      2
labels      3
longest     6

$ lanewalk order history.txt c4
c4
c2
c1

$ lanewalk range history.txt c4..m9
m9
c7
m6
c8
c5
c3

$ lanewalk base history.txt c7 c8
c5

$ lanewalk describe history.txt c7
v2-4-gc7

$ lanewalk order history.txt zz
lanewalk: unknown revision "zz"
```

A history with an octopus merge, `two.txt`, and its drawing:

```text
r1 | root one
r2 | root two
a3 r1 | on one
b4 r2 | on two
x5 r1 | side
o6 a3 b4 x5 | octopus
t7 o6
```

`lanewalk draw two.txt` prints:

```text
* t7
*-+-+ o6 octopus
* | | a3 on one
| * | b4 on two
| * | r2 root two
|   * x5 side
*- -+ r1 root one
```

## Commands

- `check FILE` validates the complete history and reports commit counts, roots, heads, merges, labels, and longest-chain length. A malformed history prints its first problem and exits with code 1.
- `order FILE [REV...] [--reverse]` lists the commits reachable from the supplied revisions, or all commits when no revision is supplied. `--reverse` reverses the listing order.
- `range FILE SPEC [--reverse]` lists the commits selected by a single revision, a two-dot range, or a three-dot range. `--reverse` reverses the listing order.
- `base FILE REV REV...` finds the best common ancestors of two or more revisions.
- `describe FILE REV` reports the nearest reachable label, with a distance and commit id when the label is not on the revision itself.
- `bisect FILE BAD GOOD...` selects a deterministic next commit to test and reports the remaining suspects and estimated steps, or reports that the bad commit is already the first known bad commit.
- `draw FILE [REV...]` draws the commits reachable from the supplied revisions, or the whole history when none are supplied.

## Exit codes

- `0` means success.
- `1` means a negative answer: `check` found a history problem, or `range`, `base`, or `describe` found no answer. A check problem is printed; an empty query answer writes no output.
- `2` means an error, such as invalid arguments, an unreadable file, invalid UTF-8, an empty history, or an invalid revision. Errors go to standard error and write nothing to standard output.

## Development

Run the full Maven verification, which compiles the project and runs its tests (212 of them, on JUnit Jupiter):

```text
mvn -B -q verify
```

The project targets Java 21 and uses only the Java standard library at runtime. CI runs the same command on Linux, Windows and macOS.

## License

MIT, see [LICENSE](LICENSE).

## Layout

The Java source and test trees are organized as follows:

- `src/main/java`
- `src/main/java/.gitkeep`
- `src/main/java/io`
- `src/main/java/io/github`
- `src/main/java/io/github/gulliby`
- `src/main/java/io/github/gulliby/lanewalk`
- `src/main/java/io/github/gulliby/lanewalk/Cli.java`
- `src/main/java/io/github/gulliby/lanewalk/Main.java`
- `src/main/java/io/github/gulliby/lanewalk/base`
- `src/main/java/io/github/gulliby/lanewalk/base/Failure.java`
- `src/main/java/io/github/gulliby/lanewalk/base/Io.java`
- `src/main/java/io/github/gulliby/lanewalk/base/Options.java`
- `src/main/java/io/github/gulliby/lanewalk/base/Sources.java`
- `src/main/java/io/github/gulliby/lanewalk/base/Text.java`
- `src/main/java/io/github/gulliby/lanewalk/commands`
- `src/main/java/io/github/gulliby/lanewalk/commands/BaseCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/BisectCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/CheckCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/Command.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/DescribeCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/DrawCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/OrderCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/commands/RangeCommand.java`
- `src/main/java/io/github/gulliby/lanewalk/graph`
- `src/main/java/io/github/gulliby/lanewalk/graph/Bases.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Bisect.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Describe.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Lanes.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Listing.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Ranges.java`
- `src/main/java/io/github/gulliby/lanewalk/graph/Reach.java`
- `src/main/java/io/github/gulliby/lanewalk/history`
- `src/main/java/io/github/gulliby/lanewalk/history/Commit.java`
- `src/main/java/io/github/gulliby/lanewalk/history/History.java`
- `src/main/java/io/github/gulliby/lanewalk/history/Loader.java`
- `src/main/java/io/github/gulliby/lanewalk/history/Parser.java`
- `src/main/java/io/github/gulliby/lanewalk/history/Problem.java`
- `src/main/java/io/github/gulliby/lanewalk/history/Revs.java`
- `src/test/java`
- `src/test/java/.gitkeep`
- `src/test/java/io`
- `src/test/java/io/github`
- `src/test/java/io/github/gulliby`
- `src/test/java/io/github/gulliby/lanewalk`
- `src/test/java/io/github/gulliby/lanewalk/CliTest.java`
- `src/test/java/io/github/gulliby/lanewalk/MainTest.java`
- `src/test/java/io/github/gulliby/lanewalk/base`
- `src/test/java/io/github/gulliby/lanewalk/base/OptionsTest.java`
- `src/test/java/io/github/gulliby/lanewalk/base/SourcesTest.java`
- `src/test/java/io/github/gulliby/lanewalk/base/TextTest.java`
- `src/test/java/io/github/gulliby/lanewalk/commands`
- `src/test/java/io/github/gulliby/lanewalk/commands/QueryCommandsTest.java`
- `src/test/java/io/github/gulliby/lanewalk/commands/ReadCommandsTest.java`
- `src/test/java/io/github/gulliby/lanewalk/commands/ReportCommandsTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph`
- `src/test/java/io/github/gulliby/lanewalk/graph/BasesTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph/BisectTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph/DescribeTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph/LanesTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph/RangesTest.java`
- `src/test/java/io/github/gulliby/lanewalk/graph/ReachTest.java`
- `src/test/java/io/github/gulliby/lanewalk/history`
- `src/test/java/io/github/gulliby/lanewalk/history/ParserShapeTest.java`
- `src/test/java/io/github/gulliby/lanewalk/history/ParserTest.java`
- `src/test/java/io/github/gulliby/lanewalk/history/RevsPrefixTest.java`
- `src/test/java/io/github/gulliby/lanewalk/history/RevsTest.java`

---

Built with BAKED
