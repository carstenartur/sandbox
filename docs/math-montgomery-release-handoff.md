# Regelsuche 0.5.4 qualification for the Montgomery cleanup

The Sandbox reactor and standalone mathematics QA both target
`io.github.carstenartur:regelsuche-optimization-sdk:0.5.4`. This branch awaits
the release and the four checks below. For development on Java 25, install the
matching 0.5.4-SNAPSHOT reactor locally and run the adapter tests with:

```sh
./mvnw -f qa/math-optimization/pom.xml -Dregelsuche.version=0.5.4-SNAPSHOT \
  -Dtest=JavaMontgomeryDotProductTest,BouncyCastleMontgomeryCandidateTest \
  -Dsandbox.math.bc.montgomery.upstream=true test
```

The final unfiltered development run on Sandbox `781e9f4` used the approved
Regelsuche source `5261ed91` and immutable SDK JAR SHA-256
`87117dd60b06b3470d183ba52446ac824d8d312ba61d62de11daf33a543a6131`.
It counted 1,360 tests: 1,346 passed, 13 were skipped, and the only failure
was the expected mismatch with the still-qualified 0.5.3 SDK receipt.
There were no test errors. All 18 focused adapter, upstream-source and
corpus regression tests passed in the preceding fresh-JVM check. These
development results do not qualify the future released artifact.

1. **Publish the prerequisite.** Integrate the reviewed core in Regelsuche
   `main`, prepare `docs/releases/0.5.4.md`, and run its regular release workflow
   with tests and `publish_maven_central=true`. Confirm the release tag and
   resolve the 0.5.4 SDK and its transitive dependencies from Maven Central
   using a fresh local repository.
2. **Bind the released bytes to Sandbox.** Compute SHA-256 of the resolved
   Central `regelsuche-optimization-sdk-0.5.4.jar`. Obtain its source commit
   from the release tag's commit. Build the plug-in and compare the actual
   embedded `lib/regelsuche-optimization-sdk.jar` to those bytes. Record the
   Maven coordinate, release commit and measured digest in
   `sandbox_math_cleanup/lib/qualification.json` for workbench qualification.
   The checked-in receipt and gallery currently document 0.5.3. Replace that
   evidence only with the results of qualification against the released bytes.
3. **Capture the workbench evidence.** Run the existing
   `captureRealMathematicsExamples` SWTBot scenario through the
   [Eclipse Help screenshot workflow](../.github/workflows/eclipse-help-screenshots.yml).
   Review all five captures under `sandbox_math_cleanup_help/images`:
   `01-bouncy-castle-choice`, `02-bouncy-castle-majority`,
   `03-bouncy-castle-long`, `04-bouncy-castle-swapped-choice`, and
   `05-bouncy-castle-modular-square`. Adopt the generated provenance for each
   image and any reviewed image changes. The capture reads the embedded SDK,
   fingerprints the installed adapter classes, and hashes the actual PNG.
4. **Verify the default build and installation.** With a fresh Maven cache,
   run `./mvnw -f qa/math-optimization/pom.xml clean test` with the default
   release coordinate, including the Bouncy Castle receipt and gallery checks.
   Reproduce the committed Help evidence and run the
   [distribution smoke workflow](../.github/workflows/distribution-smoke.yml).
   Its `verify-installed-mathematics` step compares the installed SDK and
   adapter bundle with the fresh successful workbench fixture and exercises
   analysis, apply, compilation and idempotence.
