# Pinned Regelsuche optimization SDK

The mathematics plug-in and headless QA use `0.5.4-SNAPSHOT` built from the
exact Regelsuche commit in [qualification.json](qualification.json). The
coupled quadratic-root API requires this source build. There is no claim
that these APIs are available in the published Maven Central 0.5.3 artifact.
The receipt binds the source revision, Java compiler, reproducible archive
timestamp and checksums of all six Regelsuche runtime artifacts and their
seven Maven descriptors, including the parent POM.

The current pin `016eaeab18e7f50afa6de9e6db1ae0704c27751a` adds a typed
byte-table XOR reduction, a verifier for binary-field multiplication tables,
and a checked input-prefix schedule. Sandbox maps Java loop bounds, bindings
and the order of array accesses to this API. The Java front end verifies the
generated loop bodies against the original source.
[Regelsuche PR 1425](https://github.com/carstenartur/Regelsuche/pull/1425)

CI uses [the source provisioning action](../../.github/actions/provision-regelsuche/action.yml)
before resolving the plug-in dependencies. The action checks out that full
commit, installs the SDK reactor with the pinned compiler and compares all
artifact checksums. It does not resolve a mutable source branch or assume
that a matching SNAPSHOT is present in a shared Maven cache.

For a local build, use Temurin 25.0.5+8 and the checked source bootstrap.
The previous pin uses the same `0.5.4-SNAPSHOT` coordinates with different
checksums. If you already installed that pin, keep its cache intact and choose
a fresh, empty local Maven repository at an absolute path. From the checkout
root, use the same path for the bootstrap and every subsequent Maven build or
test of this checkout.

```bash
sdk_repo="$HOME/.m2/sandbox-regelsuche-016eaeab"  # Choose an unused absolute path on upgrade.
./mvnw -Dmaven.repo.local="$sdk_repo" -f qa/regelsuche-sdk-bootstrap/pom.xml compile exec:java
./mvnw -Dmaven.repo.local="$sdk_repo" -f qa/math-optimization/pom.xml test
```

The bootstrap uses Maven's effective local repository and settings. It reuses
an installation only after all thirteen files match the receipt. Otherwise
it fetches the exact source revision and tree, verifies the fresh build before
installation, and rejects conflicting cached artifacts. Oomph runs this same
bootstrap before importing the Maven projects and checks it again on restart.

For an existing Eclipse workbench, create a separate Maven settings file,
copying any existing user settings. Set `<localRepository>` to the same
absolute path and select the file as m2e's Maven user settings. If the Eclipse
JVM already sets `maven.repo.local`, point it at the same path as well because
the Oomph launch passes that property to the bootstrap as an override. Restart
the workbench and update the Maven projects after the bootstrap succeeds.

The plug-in's `generate-resources` step copies the Maven runtime closure
into `lib/` for its private OSGi classpath. Eclipse m2e runs the same copy
step after the source SDK is installed. The packaged plug-in works offline;
the generated consumer Java has no Regelsuche runtime dependency.

The preceding source receipt is retained byte-for-byte in
[qualification-2026-11-27-parity-observations.json](qualification-2026-11-27-parity-observations.json). Earlier receipts include
[qualification-2026-11-26-guarded-field.json](qualification-2026-11-26-guarded-field.json)
and the original release in [qualification-0.5.3.json](qualification-0.5.3.json).
Screenshot validation accepts only complete qualified revision/artifact pairs;
mixed or unknown identities fail. Screenshots retain the
SDK identity recorded by their genuine capture; a dependency upgrade does
not relabel a historical capture.
Source availability does not automatically authorize another cleanup:
each source adapter still needs effect checks and replayed consumer tests.

## Previous qualified snapshot for polynomial denominators and projective handoffs

The standalone JAR and its [receipt](qualification-2026-11-16-polynomial-denominators.json) came from
[SDK transfer run 43952122987](https://github.com/carstenartur/sandbox/actions/runs/43952122987)
for Regelsuche main commit `51564131a16609f496eca18d7dc4347c8ca6d054`.
The receipt version is `0.5.3-issue1657-51564131a166`. The JAR SHA-256 is
`e27c4861f42c75feef50f2251655143933f8bc166b56b1f40ae7bba1199202c1`.
The SDK discovers and replays common polynomial denominators over large
modular coefficients and verifies weighted projective tuples and their unit
precondition. The qualification rebuilt the SDK twice from clean checkouts
and compared the resulting distribution files byte for byte.

## Previous snapshot for weighted projective handoffs

The preceding JAR and its receipt came from [SDK transfer run 43848881086](https://github.com/carstenartur/sandbox/actions/runs/43848881086).
The Regelsuche source is `f0b65289f0254424f61ac94a04b6194ef56c5b11`, the
receipt version is `0.5.3-issue1657-f0b65289f025`, and the JAR SHA-256 is
`8cafc3008ddac7c4c69a0fa2f25fc9a7dc25b505b84b9ce79e8d16428d91885d`.
The SDK independently checks weighted projective tuples and exposes the
unit precondition to Java source adapters.

## Qualified Regelsuche snapshot for consistent factor placement

The previous bundled optimizer SDK and `qualification.json` came from the
[SDK transfer run](https://github.com/carstenartur/sandbox/actions/runs/42583063561)
for Regelsuche commit `5ae21899f77c8994413204b62832a9e725d324bb`.

- Standalone JAR SHA-256 `d5b078bbd4780278bb443be3610bcdad058c993a5b3b36ebf5902cb85d082b7a`.
- Receipt version `0.5.3-issue1657-5ae21899f77c`.
- Staging commit `83a8591800bbe0fca6c75e1c033a62562fcdc586`.

Two clean builds ran 312 SDK tests each and produced ten byte-identical
distribution files. The SWT corpus test checks that ordinary Apply preserves
the right-hand factor in both JPEG index expressions.

## Previously bundled snapshot for field inversion planning

The previous optimizer SDK and `qualification.json` came from the
[SDK transfer run](https://github.com/carstenartur/sandbox/actions/runs/42451249155)
for Regelsuche commit `aed4b76461c2b5131712abf7f2cf451f25cf87a0`.

- Standalone JAR SHA-256: `4bf864c8a042e5716458d5e122fed05da46ce4f48a0f6903654773e9367fd4ca`.
- Receipt version: `0.5.3-issue1657-aed4b76461c2`.
- Staging commit: `fc008614a1c43cf6481601a6bdc66f543f807401`.

The Java 25 external consumer checked the typed field inversion plan and its
replay. The receipt identifies this source-qualified snapshot with
`publiclyPublished: false`. This snapshot enabled verified byte-field operations
in the optional mathematics cleanup. Historical screenshots retain
their original capture metadata.

## Previously bundled Regelsuche 0.5.2 optimizer SDK

The previous mathematics plug-in bundled the standalone JAR and its unchanged
`qualification.json` from the
[public optimizer SDK archive](https://github.com/carstenartur/Regelsuche/releases/download/v0.5.2/regelsuche-optimization-sdk-0.5.2.zip).

- Regelsuche release commit: `ca38d64cca3e415e7c7823e39cadbf762b908ef6`.
- Standalone JAR SHA-256: `d7cdb5944d600dc8d24e251f602adf85ee2ee66a9d54b409018b86f82d91af97`.
- Receipt version: `0.5.2-issue1657-ca38d64cca3e`.

The receipt records the pre-publication qualification, including
`publiclyPublished: false`. The [published GitHub Release](https://github.com/carstenartur/Regelsuche/releases/tag/v0.5.2)
establishes distribution of those bytes. The external Java 25 example verifies
optimization, selected-path replay, checked overflow, domains, telescoping,
runtime series and one-pass pair reduction. Sandbox's native adapter and product
checks exercise the vendored SDK separately. Historical receipts and captures
retain their original source and SDK bindings.

## Preserved SDK70 integration candidate

Published source revision: `70c13fb580062b1fd653f35b81b2fb7ed1903c28`.
Standalone JAR SHA-256: `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`.
The exact artifact and `qualification.json` were recovered from Sandbox staging
commit `1573e539f14313409ed5e16e1d80ea95b97493b5`. Their Git blob IDs are
`43f02adbaacc2db5028f2eb80ef7f8dae8423673` and
`9029f42ef1464afc68167ee3457df2d3b32c2e22`, respectively.

The retained qualification records its original external consumer output; it is
not a fresh test run of the reconstructed Java/native adapter. That adapter needs
its own Maven/JUnit and native lifecycle qualification. No missing double-build
receipt or test count is inferred. The distribution retains
`publiclyPublished: false`; this transfer is not a new Regelsuche release.

This SDK includes the ordered matrix-chain model and its ordered/factor rules.
The Java integration uses the existing source-proved affine-state transfer and
finite-series route. It does not add a general Java consumer for
`OrderedMatrixChain` rules. Historical Help capture fingerprints remain tied to
their original captured adapter and SDK until genuine public recapture.

## Preserved historical verified-value SDK integration

Published source revision: `93f0f35abaea58ea492441776be7c9d2a9be1695`.
Source tree: `673af46945cf0389af0c0521e23e52616ef49351`.
Standalone JAR SHA-256: `5ad54860d828a738d4df8dba87659b8a14009bfb208f79705a4b8529b9c58884`.

The existing Maven `MavenOptimizationSdkDistributionTest` completed two clean
six-module dependency/SDK builds, 302 SDK tests per build, and two fresh external
consumers with empty dependency caches. All ten distribution files matched byte
for byte. `qualification-2026-11-01-published-value.json` is the unchanged generated qualification for this
exact artifact, including the actual consumer output. The unchanged double-build
receipt is `reproducibility-2026-11-01-published-value.json`.

This is a local source-bound qualification of the published SDK revision, not a
public Maven release or an Eclipse consumer/product qualification. The artifact
retains `publiclyPublished: false`. This prerequisite updates the vendored SDK,
artifact metadata and retry-budget test coverage; the Java adapter remains the
baseline implementation from main `b24f806f35dc1ca121f13ae13dfb61cad9ad2749`.
Later Java adapter features require their own review and qualification.

## Preserved historical finite-series integration candidate

Published source revision: `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef` on
[`codex/verified-finite-sums`](https://github.com/carstenartur/regelsuche/tree/codex/verified-finite-sums).
Source tree: `4deea321c5fe2ae6441357f3c1d6c8ac8bdf3aa0`, identical to the
original reviewed local source `f7f73638e8b291a761aaa37dba44f3766f8e3608`.
Standalone JAR SHA-256: `50076c27b7a0c483dd2299ae40cb529e057a58d8324f273f03d15a00f69e2527`.

The existing Maven `MavenOptimizationSdkDistributionTest` passed locally with
Java 25.0.5 and Maven 3.9.16. It ran two clean six-module dependency/SDK builds,
compiled and executed two fresh external consumers with empty dependency caches,
and compared all ten distribution files byte for byte. The receipt is
`reproducibility-2026-10-31-published-series.json`;
`qualification-2026-10-31-published-series.json` records this
exact artifact, its inputs and fresh consumer output. This is a local qualification,
not a GitHub Actions result or an Eclipse product/distribution gate.

The distribution adds independently checked runtime polynomial sums in a binomial
basis, exact nested/periodic/piecewise compositions, a bounded rational telescoping
fragment, and affine/small linear transitions with division-free binary powering.
The external consumer checks the runtime sum 1..50000 against 1250025000, an affine
recurrence against 2047, and rejection of altered initial states and outputs.

The source revision is published on the Regelsuche feature branch. The standalone
artifact remains a source-bound development distribution, not a public Maven
release; `publiclyPublished: false` in its generated receipt refers to the artifact
distribution. Generated Java remains independent of the SDK at runtime. Review the
Java adapter's admission and runtime fallback contracts separately from these
mathematical proofs.

The original local qualification is retained without relabelling in
`reproducibility-2026-10-31-extended-series.json` and
`qualification-2026-10-31-extended-series.json`. It bound source
`f7f73638e8b291a761aaa37dba44f3766f8e3608` and standalone JAR SHA-256
`8314ebdd784421264bd50c3e98af02a5abf76458630784381c8c6bea5e964a7c`.
The published commit has the same full source tree, but its different revision and
commit timestamp produce a different source-bound artifact; that receipt
comes from a fresh qualification of the published revision.

## Preserved historical shared-rule qualification

Source revision: `2dbf8e4a11e2aabc78e5d5c51c10cfe23d8a86a3`.
Standalone JAR SHA-256: `5160ddecb4c4d4d521676d37c4dc639ae9b81f17fa37b6eaa3eead70f6a46efa`.

The committed upstream six-module dependency/SDK reactor passed with Java
25.0.5+8 and Maven 3.9.16. The existing deterministic SDK packager produced this
standalone JAR and its original qualification receipt. Its fresh external
consumer verified optimization, selected-path replay and checked overflow. The [native SDK qualification](https://github.com/carstenartur/sandbox/actions/runs/41171211843)
also passed two clean builds, 227 SDK tests per build and two fresh consumers; all
ten distribution files were identical. The unchanged native receipt is retained
in `reproducibility-2026-10-31.json`. Eclipse consumer CI is a separate gate.

This revision adds independent low-word polynomial congruence, disjoint-word
addition proofs, magnitude-aware numeric estimates, metered consumer ranking,
and complete explanation verification/replay/presentation accounting. Original
source execution and emitted candidate costs have separate ranking entry points;
runtime work is distinct from a weighted storage/readability score. The default
SDK optimizer remains compatible. Checker revision v12 and generator revision
v11 reject superseded evidence.

Sandbox uses the shared search and proof engine, with source-specific magnitude
bounds and actual emitted capture/guard/local costs. Generic receiver safety
ceilings are not treated as operand-size measurements. No second proof engine,
mandatory CAS process or measured speedup is introduced. Fresh full corpus and
consumer test results are recorded in the October 30 implementation qualification.
Historical screenshots and benchmark receipts retain their original provenance.

## Preserved historical qualification (previous SDK)

The following results and the unchanged `native-qualification.json` and
`reproducibility.json` refer only to the previous source revision
`8f39dfe5f52fb0610164e18dac624a4bbfaaa49d`, not to the current JAR. They are retained
as historical receipts, not relabelled as a new measurement.

Previous standalone JAR SHA-256:
`2bbb776435a09a0bc61339312ae4f58c3b4f8dde67c2925f7b1c8ee686aee52c`.
The existing native SDK qualification completed two clean dependency-reactor
builds and two freshly compiled external consumers in
[run 38200207089](https://github.com/carstenartur/sandbox/actions/runs/38200207089).
Each build ran all 176 SDK tests in 33 suites with no failures, errors or skips.
The fixed 1,092-work final-preparation regression passed in both builds.
The consumers verified optimization, selected-path replay, checked overflow,
and the public original/candidate domain API. All ten distribution files matched
byte for byte. This qualification used Temurin 25.0.4.1 and Maven 3.10.0.
The historical `native-qualification.json` records its source tree, native run,
artifact hashes, counted test results, toolchain and original EPL-2.0 notices.
These old test counts and budget results must not be attributed to the new SDK.

This is a source-bound development distribution, not a public Maven release.
Generated Java needs no SDK runtime. Cost improvements are estimates, not timings.
