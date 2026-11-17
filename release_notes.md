# Sandbox 1.3.10 — release notes

The mathematics cleanup now uses Regelsuche 0.5.3 from Maven Central. Eclipse installations include the SDK and its runtime dependencies. The Sandbox release workflow adds Central publication for the AST API, JDT adapter, functional converter core, common core and Maven plug-in under `io.github.carstenartur`. [#1919](https://github.com/carstenartur/sandbox/pull/1919)

- The encoding cleanup preserves `PrintStream` and its auto-flush setting when it selects a constructor that accepts a `Charset`. File and path constructors retain their original checked-exception contracts. [#1917](https://github.com/carstenartur/sandbox/pull/1917)
- The mathematics cleanup can reuse one modular inverse across two verified polynomial denominators. It can also generate primitive matrix operations for supported `int` and `long` recurrences, preserving Java overflow behavior. [#1913](https://github.com/carstenartur/sandbox/pull/1913) · [#1910](https://github.com/carstenartur/sandbox/pull/1910)
- Two opt-in container cleanups convert locally scoped parallel arrays to records and repeated list scans to map lookups when the record key is stable. [#1897](https://github.com/carstenartur/sandbox/pull/1897)
- JUnit migration can update a suite, its JUnit 4 members and the managed test dependencies together in supported standalone Maven projects. [#1907](https://github.com/carstenartur/sandbox/pull/1907)
- The README now links directly to the advisory Java duplication report and shows its current count alongside the build status badges. [#1916](https://github.com/carstenartur/sandbox/pull/1916) · [#1918](https://github.com/carstenartur/sandbox/pull/1918)

[Previous release notes for 1.3.9](https://github.com/carstenartur/sandbox/blob/v1.3.9/release_notes.md)
