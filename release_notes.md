# Sandbox 1.3.11 — release notes

The cleanup Maven plug-in can process ordinary Maven projects with their source roots, compiler settings and resolved dependencies. It also supports m2e project metadata and existing Eclipse workspaces. Check and diff preview edits in memory and preserve source bytes and modification times. [#1922](https://github.com/carstenartur/sandbox/pull/1922)

- The mining CLI and commit miner join the public Maven artifacts as `io.github.carstenartur:sandbox-mining-cli` and `io.github.carstenartur:sandbox-mining-core`. Each publishes a library JAR with transitive dependencies and a runnable `all` JAR. [#1923](https://github.com/carstenartur/sandbox/pull/1923)
- The encoding cleanup preserves `PrintWriter` constructors and the encoding of supplied writers. Regression tests compile the original and transformed Java with strict diagnostics. [#1924](https://github.com/carstenartur/sandbox/issues/1924)

[Previous release notes for 1.3.10](https://github.com/carstenartur/sandbox/blob/v1.3.10/release_notes.md)
