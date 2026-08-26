# Build & CI Reference

> **Read this when**: You need to build, run CI, fix build failures, or understand Maven profiles.

## Current baseline

| Component | Value |
|---|---|
| Java | 21 |
| Tycho | 5.0.4 |
| Eclipse target | Eclipse 2026-06 / Platform 4.40 |
| Target definition | `sandbox_target/eclipse.target` |

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
java -version
mvn -version
```

An `UnsupportedClassVersionError` normally means Maven is running with Java 17 or earlier. The version shown by `mvn -version`, not only the shell's `java -version`, must be Java 21.

## Maven profiles

| Profile | Purpose | Command |
|---|---|---|
| default development reactor | Bundles, features, and tests | `mvn -T 1C clean verify` |
| `product` | Materialized Eclipse product | `mvn -Pproduct -T 1C clean verify` |
| `repo` | p2 update site | `mvn -Prepo -T 1C clean verify` |
| `jacoco` | Coverage reports | `mvn -Pjacoco -T 1C clean verify` |
| `reports` | Maven test reports | `mvn -Preports -T 1C clean verify` |
| `distribution` | Product, update site, install/start/cleanup verification | `mvn -Pdistribution clean verify` |

Do not add Maven parallelism to the `distribution` command. Product and p2 repository assembly must complete before the final verification module executes.

## Makefile shortcuts

```bash
make dev
make dev-notests
make product
make repo
make release
make test
make clean
```

## UI tests on Linux

Most PDE/SWTBot tests require a display:

```bash
xvfb-run --auto-servernum --server-args="-screen 0 1600x1200x24" \
  mvn clean verify
```

CI verifies that the required Eclipse desktop runtime is present before starting those tests.

## Quality and security gates

- **JUnit/Maven** is the executable correctness authority.
- **SpotBugs** and **Checkstyle/Codacy** report code-quality findings.
- **CodeQL** performs security analysis.
- **JaCoCo** produces module and aggregate coverage evidence.
- **Test Source Inventory** checks that source tests and reported counts remain traceable.
- **Capability Inventory** checks `docs/capabilities.json` and generated Markdown.
- **Distribution Smoke Test** builds, installs, starts, and exercises the published artifacts.
- **Eclipse Help screenshots** reproduces committed UI evidence from a real workbench.

A green lightweight inventory gate does not replace Maven, distribution, or SWTBot evidence.

## Build outputs

- Product archives: `sandbox_product/target/products/`
- Update site: `sandbox_updatesite/target/repository/`
- Module coverage: `<module>/target/site/jacoco/`
- Distribution evidence: `target/distribution-verification/`

## Target platform

`sandbox_target/eclipse.target` resolves the named Eclipse 2026-06 release, the matching Orbit aggregation, EGit, SWTBot, and the pinned Bouncy Castle 1.84 bundles from the Orbit 4.40 repository. The project intentionally uses named release repositories rather than a floating Eclipse `latest` URL.

When the Eclipse or Tycho baseline changes, update all active build, product, Oomph, capability, and documentation references in the same reviewed change. `RepositoryBaselineConsistencyTest` rejects contradictory active values.

## Troubleshooting

| Problem | Likely cause | Action |
|---|---|---|
| `UnsupportedClassVersionError` | Maven uses an older JDK | Set `JAVA_HOME` to Java 21 and re-check `mvn -version` |
| Tycho/p2 resolution failure | Repository or target mismatch | Compare `pom.xml` with `sandbox_target/eclipse.target` |
| Product assembles but install verification fails | p2 metadata or category mismatch | Run the sequential `distribution` profile |
| SWTBot timeout or missing shell | No display or stale workspace state | Use Xvfb and inspect uploaded SWTBot diagnostics |
| Unused imports or NLS errors | Eclipse compiler conventions | Remove imports and add required NLS markers |
| Screenshot mismatch | UI state or real rendering changed | Diagnose state isolation; do not accept unrelated baselines |
