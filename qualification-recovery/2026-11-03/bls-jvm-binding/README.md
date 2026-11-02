# BLS preparation-JVM review correction

This checkpoint preserves the final baseline regression, focused green and complete standalone Maven logs for the correction in [PR #1793](https://github.com/carstenartur/sandbox/pull/1793).

Published commit: `9ae38a5be286d7b637f2c4ae6c857f7a2f94527a`.
Qualified source tree: `1a762c9a25b1dee4826c01e94c52773961585425`, identical to local commit `9980373e61e44a0da312f5dfc330122ecb6c1933`.

The final regression on unchanged public production ran three cases: the unchanged-JDK control passed and both foreign-JDK operations failed their assertions because the vulnerable code accepted them. After correction, all 15 focused cases passed. The complete standalone mathematics suite recorded 1,147 tests, zero failures/errors and three existing opt-in skips. All 130 suite totals and exact log hashes are in `qualification.json`.

The existing skipped tests require the upstream BLS flag, the current-SWT flag, or a verified historical Bouncy Castle mirror. No test was newly disabled by this change. Independent review approved the source and portable subprocess regression.

The work log distinguishes the earlier process-introspection environment error from the final meaningful regression. The logs retain their actual source and execution context. This checkpoint contains the complete Maven output logs and a summary derived from the full Surefire reports; it does not contain the separate 19 MB full XML archive. The source regression is committed and reproducible with the existing Maven/JUnit commands in the work log.

No new corpus scan, JMH timing run, native distribution run or release qualification is claimed. Historical measurements remain under their original qualified source in the preceding recovery checkpoint.
