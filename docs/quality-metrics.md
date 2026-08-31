# Published test and coverage metrics

The README badges and GitHub Code Quality results are generated from the machine-readable reports of the complete Maven verification. Sandbox does not maintain an independent coverage calculator.

## Established report processors

[`mikepenz/action-junit-report@v6`](https://github.com/mikepenz/action-junit-report) reads the Surefire and Failsafe `TEST-*.xml` files. The action supports nested test suites and supplies the `total`, `passed`, `skipped`, and `failed` values used for the test badge and report index.

[`danielpalme/ReportGenerator-GitHub-Action@5.5.11`](https://github.com/danielpalme/ReportGenerator-GitHub-Action) reads the aggregate JaCoCo XML report:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

It emits two synchronized formats:

```text
target/native-coverage/Cobertura.xml
target/native-coverage/Summary.json
```

Cobertura is the format consumed by [`actions/upload-code-coverage@v1`](https://github.com/actions/upload-code-coverage). The JSON summary supplies the aggregate covered-line count, coverable-line count, and line-coverage percentage used by the public badge and quality summary. This keeps GitHub's pull-request rule and the public project metrics on the same **line coverage** definition.

## Build and report publication

The workflow runs the complete verification with:

```bash
xvfb-run --auto-servernum mvn \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The Maven `reports` profile creates each available `target/site/surefire-report.html`. The workflow copies those generated module sites and the aggregate JaCoCo HTML report into one publication tree.

A small shell step validates and formats the already calculated action outputs as:

```text
badges/tests.json
badges/coverage.json
quality-summary.json
tests/index.html
coverage/index.html
```

It does not derive coverage from source code or reimplement JaCoCo semantics.

## Pull-request and default-branch coverage

The workflow runs for every pull request and for relevant pushes to `main`. It explicitly checks out the pull-request head rather than GitHub's synthetic merge commit so coverage locations and the uploaded commit identity agree.

Same-repository pull requests and `main` pushes upload `Cobertura.xml` with the `code-quality: write` permission. Fork pull requests still build and retain evidence, but cannot write coverage data into the base repository. Scheduled and manually dispatched runs verify the generation path without creating an unrelated coverage identity.

Only successful `main` runs publish the staged HTML reports and JSON endpoints to `gh-pages`. Pull-request runs never mutate the public report site.

## Verification and provenance

`quality-summary.json` records:

- the measured source commit;
- generation timestamp;
- JUnit action totals;
- metric name `LINE`;
- line-coverage percentage;
- covered and coverable line counts.

Failed builds retain available reports as a rerun-safe workflow artifact and cannot replace the last successful public values. The artifact includes JUnit XML, aggregate JaCoCo XML/CSV, converted Cobertura, ReportGenerator's JSON summary, and the staged public report tree.

The headline test count covers the reports produced by this canonical reactor run. Specialized compatibility, screenshot, distribution, and upstream workflows remain separate signals because they can execute overlapping tests.
