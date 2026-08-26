# Distribution compatibility and verification

Sandbox is an experimental Eclipse JDT cleanup distribution. The statements below describe the baseline that the repository builds and the evidence produced by its automated gates; they are not a general production-support commitment.

## Runtime baseline

| Component | Verified baseline |
|---|---|
| Java runtime | Java 21 |
| Eclipse target | Eclipse 2026-06 / Platform 4.40 |
| Build system | Maven with Tycho 5.0.4 |
| Plug-in status | Experimental; evaluate in a disposable installation or development workspace |

The target platform is resolved from `sandbox_target/eclipse.target`. Sandbox does not currently claim compatibility with older Eclipse releases merely because individual bundles may happen to resolve there.

## Reproducing the distribution gate

Run the same sequential Maven entry point used by CI:

```bash
mvn -Pdistribution \
  --batch-mode \
  -Dtycho.localArtifacts=ignore \
  clean verify
```

A headless Linux runner also needs a virtual X display. Do not add Maven parallelism: product materialization and p2 assembly must finish before `sandbox_distribution_verify` examines their output.

## Product matrix

Tycho currently materializes x86-64 archives for:

| Operating system | Window system | Architecture | Archive |
|---|---|---|---|
| Windows | win32 | x86_64 | ZIP |
| Linux | GTK | x86_64 | tar.gz |
| macOS | Cocoa | x86_64 | tar.gz |

ARM64/AArch64 product archives are not currently built or advertised.

## Automated runtime coverage

The required pull-request gate is **Distribution Smoke Test**. On Linux GTK x86-64 it:

1. builds the product and update site from a clean checkout;
2. parses p2 metadata and verifies all published feature/artifact references;
3. rejects duplicate singleton bundles and malformed materialized layouts;
4. installs every published Sandbox feature into a fresh p2 destination;
5. starts both the materialized product and the fresh installation;
6. imports an isolated Java project through the installed cleanup application;
7. applies a deterministic cleanup, validates the report and source change, and compiles the transformed source with Java 21.

Windows and macOS archives are assembled by the same reactor but do not yet receive equivalent native launch-and-transform verification. They are build-verified, not runtime-verified.

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

Use a separate Eclipse installation or disposable workspace for initial evaluation, and keep source changes under version control. The command-line cleanup application requires Java 21 and an explicit Eclipse workspace through `-data`.

## Baseline consistency

`RepositoryBaselineConsistencyTest` checks that `pom.xml`, the capability inventory, PDE target, product, p2 category, Oomph setup, and active build documentation agree on the same Eclipse and Tycho baseline. Dated QA reports remain historical records and are intentionally excluded from this current-baseline contract.
