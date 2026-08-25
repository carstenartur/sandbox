# Published test and coverage metrics

The README badges are generated from the machine-readable reports of the complete Maven verification. Sandbox does not maintain its own JUnit or JaCoCo parser.

## Established report processors

[`mikepenz/action-junit-report@v6`](https://github.com/mikepenz/action-junit-report) reads the Surefire and Failsafe `TEST-*.xml` files. The action supports nested test suites and supplies the `total`, `passed`, `skipped`, and `failed` values used for the test badge and report index.

[`cicirello/jacoco-badge-generator@v2`](https://github.com/cicirello/jacoco-badge-generator) reads the aggregate JaCoCo CSV report:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.csv
```

It generates the Shields-compatible instruction-coverage endpoint directly. No repository code interprets JaCoCo XML.

## Build and report publication

The workflow runs the complete verification with:

```bash
xvfb-run --auto-servernum mvn \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The Maven `reports` profile creates each available `target/site/surefire-report.html`. The workflow copies those generated module sites and the aggregate JaCoCo HTML report into one publication tree.

A small shell step only formats the already calculated action outputs as:

```text
badges/tests.json
badges/coverage.json
quality-summary.json
tests/index.html
coverage/index.html
```

It does not parse JUnit or JaCoCo evidence.

## Verification and provenance

Pull requests exercise the complete build, report collection, badge generation, and staging path without changing GitHub Pages. Successful `main` runs publish the staged tree to `gh-pages`. Scheduled runs without a commit in the preceding 24 hours retain the last verified publication.

`quality-summary.json` records the source commit, generation timestamp, JUnit action totals, and instruction-coverage percentage. Failed builds retain available reports as a rerun-safe workflow artifact and cannot replace the last successful public values.

The headline test count covers the reports produced by this canonical reactor run. Specialized compatibility, screenshot, distribution, and upstream workflows remain separate signals because they can execute overlapping tests.
