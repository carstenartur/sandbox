# Mathematics tests

JUnit 5 tests run as a fragment of the cleanup bundle through Tycho. Options,
report, argument, job and edit-guard contracts also run without a workbench
against the pinned SDK JAR. Core tests cover binding, proof, Java emission and
numerical counterexamples.

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
