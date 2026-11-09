# Mathematics tests

JUnit 5 tests run as a fragment of the cleanup bundle through Tycho. Options,
report, argument, job and edit-guard contracts also run without a workbench
against the pinned SDK JAR. Core tests cover binding, proof, Java emission and
numerical counterexamples.

## Compiler diagnostics

`StrictCompilerOptions` is shared test/QA infrastructure. Its common profile
explicitly enables type/cast, boxing, unused-code, control-flow, null and resource
diagnostics, preserving compiler errors. `MathTestSupport` and the callable-context
test helpers retain the same per-AST `SourceEnvironment` for input parsing and
generated-source validation; stale AST fixtures are not reparsed into fresh input.
The source-archive QA runners use this same profile. `ProjectMathScan` records its
exact compiler options and before/after diagnostics.
The profile starts from complete ECJ defaults because headless `JavaCore` preferences
can expose only a partial map. It changes neither workspace preferences nor the
caller's option map.

Additional contracts enable every severity-valued optional compiler diagnostic,
including NLS and unused parameters, and exercise warning and error severities.
They verify real compiler problems, useful successful rewrites, existing-warning
provenance, and complete generated-source rejection. Native workspace tests retain
the effective project's options through Preview/Apply/Undo. These test profiles
do not replace the user's production project settings.

The `swtbot` Maven profile adds real workbench tests for independent numerical
choices, checked consent and persistence, normal JDT file preview, atomic group
selection, apply and byte-exact Undo, asynchronous assist, stale source/compiler/
classpath/profile rejection, progress-dialog cancellation, and the registered
analysis-only application. Fixtures use actual workspace projects and the host
JDK container, with a Java 17 project target preserved by the cleanup.

Tests run outside the display thread. Screenshots are captured from the verified
real dialog clients at 1280 by 900, only after the relevant assertions; consecutive
captures must match. The profile writes them to `target/screenshots`. These are
disposable local fixture captures, not evidence of an Oomph-provisioned upstream
workspace or a measured performance gain.

Run from the repository root on a display:

```sh
./mvnw -pl sandbox_target,sandbox_math_cleanup_test -am -Pswtbot verify
```

The `verify` phase is required when mixed Maven/Tycho dependencies need their
OSGi JARs packaged. Headless Linux additionally needs a working Xvfb display.
The existing `-Dsandbox.tycho.linux-only=true` profile scopes this native UI run;
other platform packaging gates remain separate.

For a later installed-product headless launch, explicitly add
`-Dsandbox.math.retainHeadlessProbe=true`. Only after every workbench test passes,
the suite retains a separate disposable `MathematicsHeadlessQualification`
Java 17 project, saves its workspace and writes `target/headless-probe/receipt.json`.
The receipt gives workspace/project/source/configuration paths and hashes and
the embedded SDK hash. It does not assert that an installed application launch
has passed. A new attempt removes the previous receipt before any tests run.
