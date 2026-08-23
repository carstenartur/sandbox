# Published test and coverage metrics

The README test and coverage badges are generated from machine-readable evidence produced by the authoritative Maven verification. They are not source-file counts, and the workflow does not infer values from console output.

## Test totals

`sandbox_quality_metrics` walks the completed repository build for Surefire and Failsafe `TEST-*.xml` reports. It counts individual `<testcase>` elements and classifies every case as passed, skipped, failed, or errored. The public test badge includes both the exact registered-test total and the skipped count represented by that run.

For every JUnit suite, the generator requires the declared `tests` value and verifies it against the contained test cases. Optional failure, error, and skipped summaries are checked when present. Conflicting testcase outcomes, missing reports, empty reports, and contradictory summaries fail the publication instead of silently producing zero or incomplete metrics.

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

## Publication and provenance

Pull requests run the generator's Maven/JUnit regression tests. The complete report collection and publication run after a successful `main` verification or an applicable scheduled build. The `gh-pages` branch receives:

```text
badges/tests.json
badges/coverage.json
quality-summary.json
tests/index.html
coverage/index.html
```

`quality-summary.json` records the source commit, generation timestamp, exact test totals, and raw aggregate coverage counters. Failed or incomplete builds retain any available JUnit and JaCoCo XML as workflow artifacts but cannot replace the last verified public values.

The headline test total intentionally describes the canonical reactor verification. Specialized upstream, screenshot, distribution, or compatibility workflows may repeat some of the same tests and are therefore not added to this number. Their independent workflow status remains the appropriate signal for those additional environments.
