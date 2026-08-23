# Verified test and coverage publication

Sandbox publishes measured test and coverage values from the authoritative Maven verification. The README badges are Shields endpoint badges backed by JSON generated from JUnit XML and the aggregate JaCoCo XML report; they are not workflow-status badges or manually maintained percentages.

The canonical description of the evidence contract, validation rules, build isolation, publication layout, and provenance is maintained in [`docs/quality-metrics.md`](docs/quality-metrics.md).

## Published resources

A successful verification of `main` updates these GitHub Pages resources in one publication:

- `badges/tests.json` — exact registered test total and skipped count;
- `badges/coverage.json` — aggregate instruction coverage;
- `quality-summary.json` — source commit, timestamp, test outcomes, and raw coverage counters;
- `tests/` — readable totals and links to generated module reports;
- `coverage/` — the aggregate JaCoCo HTML report.

The public entry points are:

- <https://carstenartur.github.io/sandbox/tests/>
- <https://carstenartur.github.io/sandbox/coverage/>
- <https://carstenartur.github.io/sandbox/quality-summary.json>

## Local verification

Run the quality-generator regression tests without resolving the Eclipse target platform:

```bash
mvn --file sandbox_quality_metrics/pom.xml clean verify
```

Generate the complete evidence locally with the same principal profiles used by the publication workflow:

```bash
xvfb-run --auto-servernum mvn \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The aggregate coverage source is then available at:

```text
sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml
```

Publication is handled by [`.github/workflows/coverage.yml`](.github/workflows/coverage.yml). It retains unrelated GitHub Pages content, publishes only after a successful `main` verification, and preserves available XML evidence as a workflow artifact when a build fails.
