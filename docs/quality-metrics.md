# Published test and coverage metrics

The README test and coverage badges are generated from machine-readable evidence produced by the authoritative Maven verification. They are not source-file counts, and the workflow does not infer values from console output.

## Test totals

`sandbox_quality_metrics` walks the completed repository build for Surefire and Failsafe `TEST-*.xml` reports. It counts individual `<testcase>` elements and classifies every case as passed, skipped, failed, or errored. The public test badge includes both the exact registered-test total and the skipped count represented by that run.

For every `<testsuite>` element, the generator requires the declared `tests` value and verifies it against the contained test cases. Optional failure, error, and skipped summaries on those suite elements are checked when present. Conflicting testcase outcomes, missing reports, empty reports, and contradictory suite summaries fail the publication instead of silently producing zero or incomplete metrics.

## Coverage

The coverage value comes from the single root `INSTRUCTION` counter in the aggregate JaCoCo XML at:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

Selecting only the direct aggregate counter avoids adding nested package or class counters a second time. Both `covered` and `missed` are required and retained in the machine-readable summary. The complete JaCoCo HTML report remains available beside the badge value.

## Report discovery

The Maven `reports` profile creates `target/site/surefire-report.html` for test-bearing modules. Publication discovers those files across the whole repository rather than assuming that every test-bearing module name ends in `_test`. The generated test index therefore also links reports from plain Maven modules such as core libraries.

## Build isolation

The generator and its regression tests live in the plain Maven module `sandbox_quality_metrics`, which sets `tycho.mode=maven`. The workflow invokes its own POM directly, so validating badge logic does not require packaging or resolving the Eclipse/OSGi test bundles. This prevents focused metric tests from depending on unrelated Tycho reactor artifacts such as `sandbox_common_core`.

## Verification, publication, and provenance

Pull requests first run the isolated Maven/JUnit regression tests and then exercise the complete coverage reactor, report staging, and badge-generation path. A pull-request or manually dispatched branch run does **not** update GitHub Pages; its workflow summary points to the retained run evidence instead. This keeps public values bound to verified `main` commits while still testing the entire publication pipeline before merge.

A successful push to `main`, a successful manual run on `main`, or an applicable scheduled build publishes the generated site. Scheduled runs with no commit in the preceding 24 hours leave the last verified publication unchanged. The `gh-pages` branch receives:

```text
badges/tests.json
badges/coverage.json
quality-summary.json
tests/index.html
coverage/index.html
```

`quality-summary.json` records the source commit, generation timestamp, exact test totals, and raw aggregate coverage counters. Failed or incomplete builds retain any available JUnit and JaCoCo XML as a workflow artifact but cannot replace the last verified public values. Artifact names include both the workflow run number and run attempt, so reruns preserve independent evidence instead of colliding with immutable artifacts from an earlier attempt.

The headline test total intentionally describes the canonical reactor verification. Specialized upstream, screenshot, distribution, or compatibility workflows may repeat some of the same tests and are therefore not added to this number. Their independent workflow status remains the appropriate signal for those additional environments.
