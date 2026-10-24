# Distribution compatibility and verification

Sandbox is an experimental Eclipse JDT cleanup distribution. The statements below describe the baseline that the repository builds and the evidence produced by its automated gates; they are not a general production-support commitment.

## Runtime baseline

| Component | Configured baseline |
|---|---|
| Java runtime | Java 25 |
| Eclipse target | Eclipse 2026-09 / Platform 4.41 |
| Build system | Maven with Tycho 5.0.4 |
| Plug-in status | Experimental; evaluate in a disposable installation or development workspace |

The target platform is resolved from `sandbox_target/eclipse.target`. Runtime verification is established by the successful distribution gate for the exact revision under test, not by the version table alone. Sandbox does not currently claim compatibility with older Eclipse releases merely because individual bundles may happen to resolve there.

## Reproducing the distribution gate

Run the sequential distribution build and installed-mathematics stages used by CI:

```bash
./mvnw -Pdistribution,cli-dist,swtbot \
  --batch-mode \
  -Dtycho.localArtifacts=ignore \
  -Dsandbox.math.retainHeadlessProbe=true \
  clean verify

./mvnw -Pdistribution --batch-mode -pl sandbox_distribution_verify \
  -Dtycho.localArtifacts=ignore exec:java@verify-installed-mathematics
```

A headless Linux runner needs a virtual X display for the build command, for example through `xvfb-run --auto-servernum`. The installed-mathematics stage separately verifies execution without a display. Do not add Maven parallelism: product materialization and p2 assembly must finish before `sandbox_distribution_verify` examines their output.

## Product matrix

Tycho currently materializes x86-64 archives for:

| Operating system | Window system | Architecture | Archive |
|---|---|---|---|
| Windows | win32 | x86_64 | ZIP |
| Linux | GTK | x86_64 | tar.gz |
| macOS | Cocoa | x86_64 | tar.gz |

ARM64/AArch64 product archives are not currently built or advertised.

## Automated runtime coverage

The required pull-request gate is **Distribution Smoke Test**. Its native matrix covers Linux GTK x86-64, Windows x86-64 and macOS Cocoa x86-64. On each platform it:

1. builds the product and update site from a clean checkout;
2. parses p2 metadata and verifies all published feature/artifact references;
3. rejects duplicate singleton bundles and malformed materialized layouts;
4. installs every published Sandbox feature into a fresh p2 destination;
5. starts both the materialized product and the fresh installation;
6. imports an isolated Java project through the installed cleanup application;
7. applies a deterministic cleanup, validates the report and source change, and compiles the transformed source with Java 25;
8. upgrades the published stable 1.3.6 aggregate and its components to the candidate in one p2 transaction, then verifies installed cleanup behavior;
9. executes the native mathematics Workbench scenarios and separately verifies installed mathematics analysis/apply/idempotence without a display.

The independent 1.3.4 legacy-feature migration and aggregate-metadata-only update cases remain part of the gate. Linux additionally qualifies cleanup consumers against a Docker image built from the same candidate. Release acceptance requires successful results for the exact candidate revision on every advertised platform; an older successful matrix is supporting evidence.

## Publication channels

### Latest snapshot

[Latest snapshot p2 repository](https://carstenartur.github.io/sandbox/snapshots/latest/)

The `Deploy Snapshot to GitHub Pages` workflow runs only after successful Java CI on `main`. It repeats the exact-commit distribution gate, publishes the p2 repository, and reads the public version and composite metadata back. A failed public verification restores the previously captured `gh-pages` revision with force-with-lease rollback evidence.

### Versioned releases

[Versioned release repositories](https://carstenartur.github.io/sandbox/releases/)

The `Release Workflow` uses the same fail-closed distribution contract. It verifies the local artifacts and public release repository before creating the release tag and GitHub Release. Tests cannot be skipped for a published release.

## Evidence

Distribution workflows retain machine- and human-readable evidence under `target/distribution-verification/`, including:

- `verification.json` and `verification.md`;
- build, product, fresh-install, startup, and cleanup-application logs;
- public snapshot or release URL verification;
- the cleanup transformation report;
- rollback evidence when publication validation fails.

GitHub Actions stores the evidence as an immutable workflow artifact. Snapshot and release deployments also publish the summary and JSON report beside the corresponding p2 repository.

## Installation guidance

Use a separate Eclipse installation or disposable workspace for initial evaluation, and keep source changes under version control. The command-line cleanup application requires Java 25 and an explicit Eclipse workspace through `-data`.

## Baseline consistency

`RepositoryBaselineConsistencyTest` checks that `pom.xml`, the capability inventory, PDE target, product, p2 category, contributor Oomph setup, and active build documentation agree on the same Eclipse and Tycho baseline. Dated QA reports remain historical records and are intentionally excluded from this current-baseline contract. The frozen R4_40 migration corpus and its dedicated QA installation are separate test inputs, not an alternative supported Sandbox runtime.
