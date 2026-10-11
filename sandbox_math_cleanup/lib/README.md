# Pinned Regelsuche optimization SDK

Source revision: `e3e795e009a35c88bcc7cdb4151408c147be0951`.
Standalone JAR SHA-256: `26f90e5493a68564a89fb04c740fcb2f744fa032246b97ebbaa75ff5d6752b36`.

The repository-owned SDK release profile and distribution contract passed two clean
builds with 2,320 tests per build, including 110 SDK tests. Two freshly compiled
consumers verified optimization, selected-path replay, and checked overflow.
All ten distribution files matched byte for byte. Receipts are qualification.json
and reproducibility.json; the latter also records a transient discovery failure
and the unchanged clean retry. The SDK includes both selected search paths and
the modular-square generator required by the integrated cleanup.

This is a source-bound development distribution, not a public Maven release.
Generated Java needs no SDK runtime. Existing license notices remain applicable.
