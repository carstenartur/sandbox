# Sandbox 1.3.9 — release notes

Eclipse Marketplace installations of Sandbox 1.3.8 could load the AST API JAR from 1.3.2. Those releases used the same OSGi bundle version for different JARs. Sandbox 1.3.9 gives the bundle its own version. Existing users can update Sandbox through Eclipse. [#1837](https://github.com/carstenartur/sandbox/issues/1837) · [#1856](https://github.com/carstenartur/sandbox/pull/1856)

- Container Analysis shows which properties a migration preserves and which still require investigation. [#1854](https://github.com/carstenartur/sandbox/pull/1854)
- The mathematics cleanup recognizes additional counted sums and byte-field inversion patterns. Its qualification now checks generated Java with stricter compiler diagnostics and exercises the updated Regelsuche SDK. [#1843](https://github.com/carstenartur/sandbox/pull/1843) · [#1844](https://github.com/carstenartur/sandbox/pull/1844) · [#1845](https://github.com/carstenartur/sandbox/pull/1845) · [#1846](https://github.com/carstenartur/sandbox/pull/1846) · [#1847](https://github.com/carstenartur/sandbox/pull/1847) · [#1848](https://github.com/carstenartur/sandbox/pull/1848)
- Both JGit Storage Hibernate consumers use 0.12.1. The author report preserves exact repository identity. [#1829](https://github.com/carstenartur/sandbox/pull/1829) · [#1849](https://github.com/carstenartur/sandbox/pull/1849)

[Changes since 1.3.8](https://github.com/carstenartur/sandbox/compare/v1.3.8...v1.3.9)
