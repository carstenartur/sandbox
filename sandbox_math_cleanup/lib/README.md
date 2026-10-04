# Pinned Regelsuche optimization SDK

The private SDK archive is built from published source revision
[`b99787c55af6536b8782382d705f18b00657252f`](https://github.com/carstenartur/Regelsuche/commit/b99787c55af6536b8782382d705f18b00657252f),
version `0.5.0-issue1657-b99787c55af6`.
Its SHA-256 is `4549d75c7b673c230bed5f45c45473746308862ef443d206a8d3476e636d8428`.
The source tree is `8d37a72d8b271ae11f04bce4028a4e0649a1be22`.

Two clean Java 25.0.2+10 / Maven 3.9.16 builds with
`project.build.outputTimestamp=1791146677` produced byte-identical distributions,
including the runtime archive, source archive, Javadoc, POM and distribution ZIP.
Each build passed 55 focused tests: 43 SDK, eight search and four mathematics
tests. Each distribution also compiled and ran the existing standalone Java
consumer in a fresh directory with an empty dependency cache, confirming
`optimization=VERIFIED` and `checked=ORIGINAL_OVERFLOW_DETECTED`.

Reproduce from the pinned source with the `sdk-release` Maven profile and the
repository's `scripts/package-optimization-sdk.py` command documented in
`regelsuche-optimization-sdk/README.md`. The machine-readable
[qualification record](qualification.json) includes every runtime input digest,
the source identity and distribution digest. The same source tree passed the
existing public API compatibility, SDK coverage and repository complexity gates.

This archive is privately embedded in the cleanup plugin; generated Java has no
Regelsuche runtime dependency. It is not a Maven Central release. SDK artifact
qualification does not substitute for Sandbox's native workbench and installed
product checks. `Regelsuche-LICENSE` and the dependency license resources inside
the archive retain their original license terms.
