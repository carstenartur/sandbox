# BLS JVM identity review fix

## Scope and source identities

- Public PR 1793 branch: `codex/bls-whole-call-qualification-20261102`.
- Exact baseline: `e7616695a9b5a70b26df9b58c8eb7891ffc08e5f`.
- Checkout: `/workspace/scratch/4ed3a04e028b/sandbox-bls`.
- Recovered read-only candidate: `/tmp/sandbox-bls-runtime-identity-review` at `a226360ade62e962bacd053c07166158e9a0f3fa`.
- Four target blobs and all relevant standalone math-module source inputs are byte-identical between these bases. Candidate was preserved as `recovered-candidate.patch`.
- Read repository `AGENTS.md`, `.github/copilot-instructions.md`, and `.github/copilot-ref-testing.md` before edits.
- No push, GitHub comment, PR mutation, or changes to the recovered checkout.

## Root cause and intended correction

Preparation binds `java.runtime.version` and the implementation/classpath hashes. The reporter currently derives executable/JDK/VM identity from its own runtime. A second JDK installation with identical version strings and classpath can therefore consume the first installation's qualification.

Recovered correction records `jvm`, `jdkVersion`, `vmName`, and `vmVersion` in `cohort.json` before its hash is placed in `benchmark.properties`. Existing runner, setup, and teardown validation will require that persisted runtime. The reporter will compare all raw JMH rows against the same persisted identity.

## Regression design

The test prepares a real pinned five-class BLS cohort on JDK A, then starts real child JVMs from either the original installation (positive control) or an alternate JDK home with a distinct executable path. The alternate installation preserves version strings, classpath, and implementation hashes. Its setup operation invokes real benchmark trial setup; its reporter creates internally consistent synthetic JMH rows from its own child runtime and invokes the production reporter.

Recovered test corrections:

1. Evidence copying uses `REPLACE_EXISTING` so repeated Maven runs do not fail on retained output.
2. OS-level `ProcessHandle.Info.command()` is optional; the probe records it when available and checks it when present. Mandatory child JMH executable and home real-path checks still establish the alternate installation. Behavioral cases are never skipped.

## Runs to date

Runtime: Temurin OpenJDK `25.0.5+8-LTS`, executable `/tmp/jdt-pr5614-tools/jdk-25.0.5+8/bin/java`. Maven: `/tmp/jdt-pr5614-tools/apache-maven-3.9.16/bin/mvn`. Commands use `JAVA_HOME` for that JDK and prepend its `bin` to `PATH`.

Focused test-only command against unchanged public production:

```sh
mvn -o -B -f qa/math-optimization/pom.xml \
  '-Dtest=BlsSourceCohortTest#unchangedQualificationRuntimeStillSetsUpAndReports+differentExecutableAndHomeCannotConsumeSourceQualification' test
```

- Initial restricted run: compiled all 131 main and 131 test sources; 3 errors because optional OS command metadata was unavailable, before the tested behavior. Log: `public-baseline-red.log`.
- Same command with unrestricted process introspection: expected red, 3 cases / 2 assertion failures / 0 errors / 0 skipped, 25.829 seconds. Both foreign-runtime operations incorrectly exited zero; same-runtime setup/report passed. Log: `public-baseline-red-unrestricted.log`; full reports copied to `public-baseline-red-reports`.
- Portable test harness, restricted environment, unchanged public production: same expected red, 3 cases / 2 assertion failures / 0 errors / 0 skipped, 26.488 seconds. Log: `public-baseline-portable-red.log`; full reports: `public-baseline-portable-red-reports`.
- Applied recovered production/docs unchanged after the behavioral red. Final diff: 4 files, 114 insertions and 10 deletions.
- Focused post-fix gate: `mvn -o -B -f qa/math-optimization/pom.xml -Dtest=BlsSourceCohortTest clean test` passed, 15 tests / 0 failures / 0 errors / 0 skipped, 38.086 seconds. Log: `focused-green.log`; full reports: `focused-green-reports`.
- Full standalone suite command `mvn -o -B -f qa/math-optimization/pom.xml test` passed: 1,147 tests / 0 failures / 0 errors / 3 skips in 4:18 minutes. Log: `full-standalone-tests.log`. Full reports remain at `/workspace/scratch/4ed3a04e028b/sandbox-bls/qa/math-optimization/target/surefire-reports` and are preserved in `full-standalone-surefire.tar.gz`.
- Existing opt-in skips: `BlsUpstreamSourceIdentityTest` requires `sandbox.math.bc.upstream`; `SwtCurrentSourceRefinementTest` requires `sandbox.math.swt.current`; `BouncyCastleMathReferenceTest` requires a verified JGit mirror via `math.qa.bcMirror`.
- The full run repeats all 15 BLS tests successfully (0 failures/errors/skips). No JMH measurement profile or additional network/upstream qualification was invoked.

Pinned local inputs verified with SHA-256:

- SDK: `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`.
- BC binary: `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`.
- BC sources: `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`.

## Review

Independent read-only reviewer `identity_review` checked receipt binding, reuse by runner/setup/teardown, and all-row report comparison. Optional OS-command metadata issue was accepted and corrected as above. Reviewer inspected the final authoritative diff and portable red receipts, then approved with no remaining blocking findings. Full build/CI/integration verification remains separate from this code-review approval.

## Final local commit and limits

- Commit: `9980373e61e44a0da312f5dfc330122ecb6c1933` — `Bind BLS measurements to the preparation JVM`.
- Parent: `e7616695a9b5a70b26df9b58c8eb7891ffc08e5f`.
- Tree: `1a762c9a25b1dee4826c01e94c52773961585425`.
- Working tree clean after commit; `git diff --check` and staged diff check both passed.
- Existing PR branch retained; no GitHub mutation performed by this agent.
- Linux/JDK 25 test evidence only; no Windows/macOS execution or new performance measurements claimed.
- Older cohort receipts without `qualificationRuntime` are deliberately rejected and require fresh preparation.
- Runtime identity binds the JMH executable path and reported JDK/VM fields, alongside the existing runtime-input hashes; it is not a cryptographic attestation of every installed JDK binary.
