# Verified test and coverage publication

Sandbox publishes measured test and coverage values from the complete Maven verification. The implementation deliberately delegates report interpretation to established GitHub Actions rather than maintaining repository-specific parsers:

- `mikepenz/action-junit-report@v6` supplies JUnit totals from Surefire and Failsafe reports;
- `cicirello/jacoco-badge-generator@v2` generates the instruction-coverage endpoint from JaCoCo CSV.

The detailed contract is documented in [`docs/quality-metrics.md`](docs/quality-metrics.md).

## Published resources

A successful `main` run updates these GitHub Pages resources in one publication:

- `badges/tests.json` — test total and skipped count;
- `badges/coverage.json` — aggregate instruction coverage;
- `quality-summary.json` — source commit, timestamp, JUnit totals, and coverage percentage;
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

Publication is handled by [`.github/workflows/coverage.yml`](.github/workflows/coverage.yml). Pull requests validate the full generation path but do not mutate GitHub Pages. Failed runs retain available JUnit and JaCoCo evidence as workflow artifacts.
