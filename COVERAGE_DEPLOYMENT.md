# Verified test and coverage publication

Sandbox publishes measured test and line-coverage values from the complete Maven verification. The implementation delegates report interpretation and format conversion to established tools instead of maintaining a second Java-coverage engine:

- `mikepenz/action-junit-report@v6` supplies JUnit totals from Surefire and Failsafe reports;
- `danielpalme/ReportGenerator-GitHub-Action@5.5.11` converts the aggregate JaCoCo XML report into Cobertura and a machine-readable JSON summary;
- `actions/upload-code-coverage@v1` uploads the Cobertura result to GitHub Code Quality for the pull-request head or the protected default branch.

The detailed contract is documented in [`docs/quality-metrics.md`](docs/quality-metrics.md).

## Published resources

A successful `main` run updates these GitHub Pages resources in one publication:

- `badges/tests.json` — test total and skipped count;
- `badges/coverage.json` — aggregate line coverage;
- `quality-summary.json` — source commit, timestamp, JUnit totals, covered lines, coverable lines, and line-coverage percentage;
- `tests/` — readable totals and generated module reports;
- `coverage/` — aggregate JaCoCo HTML report.

Public entry points:

- <https://carstenartur.github.io/sandbox/tests/>
- <https://carstenartur.github.io/sandbox/coverage/>
- <https://carstenartur.github.io/sandbox/quality-summary.json>

## Local evidence generation

```bash
xvfb-run --auto-servernum mvn \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The aggregate coverage inputs are then available at:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.csv
sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

The workflow converts the XML input to:

```text
target/native-coverage/Cobertura.xml
target/native-coverage/Summary.json
```

Publication is handled by [`.github/workflows/coverage.yml`](.github/workflows/coverage.yml). Every pull request checks out and measures its head commit. Same-repository pull requests upload their Cobertura report to GitHub Code Quality, while only successful `main` runs mutate GitHub Pages. Failed runs retain available JUnit, JaCoCo, Cobertura, and JSON evidence as workflow artifacts.

GitHub coverage rules evaluate **line coverage**, not JaCoCo instruction coverage. The badge, machine-readable summary, native upload, and repository rule therefore use the same metric.
