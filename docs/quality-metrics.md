# Published test and coverage metrics

The README test and coverage badges are generated from one authoritative Maven verification run. They are not source-file counts and they are not parsed from console text.

## Test totals

`sandbox_quality_metrics` walks the completed reactor for Surefire and Failsafe `TEST-*.xml` reports. It counts individual `<testcase>` elements and classifies every case as passed, skipped, failed, or errored. The public test badge therefore reports the exact total represented by that run, while the skipped badge exposes the corresponding skipped count separately.

The generator fails when no runtime test reports exist or when the reports contain no test cases. A failed or incomplete build cannot replace the last verified public values with zeroes.

## Coverage

The coverage value comes from the root `LINE` counter in the aggregate JaCoCo XML at:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

Reading only the root aggregate counter avoids adding the nested package and class counters a second time. The published site also retains the complete JaCoCo HTML report and the raw covered/missed line counts.

## Publication and provenance

Pull requests execute and validate the metric pipeline, but only a successful build of `main` publishes it. The `gh-pages` branch receives:

```text
badges/tests.json
badges/skipped.json
badges/coverage.json
quality-summary.json
tests/index.html
coverage/index.html
```

`quality-summary.json` records the exact verified commit plus the test and line counters. GitHub Actions only provisions the build environment, invokes Maven, runs the Maven-built generator, and publishes the resulting evidence. The metric logic and its regression tests remain ordinary Java/JUnit code in the reactor.

The headline test total intentionally describes the canonical reactor verification. Specialized upstream, screenshot, distribution, or compatibility workflows may repeat some of the same tests and are therefore not added to this number. Their independent workflow status remains the appropriate signal for those additional environments.
