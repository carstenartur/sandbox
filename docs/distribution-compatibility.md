# Distribution compatibility and verification

Sandbox is an experimental Eclipse JDT cleanup distribution. The compatibility statements below describe what the repository builds and what its automated publication gates actually verify; they are not a production-support commitment.

## Runtime baseline

| Component | Supported baseline |
|---|---|
| Java runtime | Java 21 |
| Eclipse target | Eclipse 2025-12 simultaneous release repository |
| Build system | Maven 3.9.14 with Tycho 5.0.3 |
| Plugin status | Experimental; validate in a disposable or development workspace before wider use |

The target platform is resolved from `sandbox_target/eclipse.target`. Plugins and the cleanup application may depend on APIs present in that target and are not claimed to support older Eclipse releases.

## Product build matrix

Tycho materializes product archives for these x86-64 environments:

| Operating system | Window system | Architecture | Build output |
|---|---|---|---|
| Windows | win32 | x86_64 | ZIP |
| Linux | GTK | x86_64 | tar.gz |
| macOS | Cocoa | x86_64 | tar.gz |

ARM64/AArch64 product archives are not currently built or advertised.

## Automated runtime coverage

The required pull-request gate is named **Distribution Smoke Test**. It currently runs the complete native verification on Linux GTK x86_64 and performs all of the following:

1. builds and tests the product and update site from a clean checkout under Xvfb;
2. parses p2 metadata and verifies that every published feature and referenced artifact is present;
3. checks the materialized product layout and rejects duplicate singleton bundles;
4. installs every published Sandbox feature into a fresh p2 destination;
5. starts both the materialized product and the fresh installation;
6. imports a real Java project through the installed cleanup application;
7. applies a deterministic cleanup, verifies the JSON report and source change, then compiles the transformed source with Java 21.

Windows and macOS archives are assembled by the same Tycho reactor, but they do not currently receive the equivalent native launch-and-transform smoke test. Treat those archives as build-verified rather than runtime-verified until platform-specific runners are added.

## Publication channels

### Latest snapshot

`https://carstenartur.github.io/sandbox/snapshots/latest/`

The named **Deploy Snapshot to GitHub Pages** gate runs only after a successful Java CI push on `main`. It repeats the full distribution gate for that exact commit, publishes the snapshot, and reads the public p2 metadata back from both the version endpoint and the snapshots composite repository. A failed build, test, p2 install, startup, cleanup transformation, compilation or public-URL check prevents successful promotion. If the public URL verification fails after the `gh-pages` write, the workflow restores the previously captured `gh-pages` revision with a force-with-lease rollback and records the rollback evidence.

### Versioned releases

`https://carstenartur.github.io/sandbox/releases/`

The named **Release Workflow** does not permit tests to be skipped for a published release. It runs the same local distribution gate, publishes the version-specific update site, verifies the public version URL and release composite, and only then creates the release tag and GitHub Release. Version-specific verification files are published beside the p2 repository and attached to the GitHub Release. The release publication follows the same fail-closed rule: a failed public repository verification must restore the previously captured `gh-pages` revision before any tag or GitHub Release is created.

## Evidence

Distribution workflows retain machine-readable and human-readable evidence under `target/distribution-verification/`, including:

- `verification.json` and `verification.md` for repository, artifact and product checks;
- build, materialized-product, fresh-install, fresh-product and cleanup-application logs;
- public snapshot or release URL verification JSON;
- the cleanup transformation report;
- a publication rollback log when an advertised URL fails validation after deployment.

GitHub Actions stores this directory as an immutable workflow artifact. Snapshot and release deployments also publish the summary and JSON report beside the corresponding p2 repository.

## Installation guidance

Use a separate Eclipse installation or a disposable workspace for initial evaluation. Back up source control state before enabling broad cleanup profiles. For the command-line cleanup application, provide a Java 21 runtime and an Eclipse workspace through `-data`; automated smoke tests use the internal `--import-project` option to register the isolated fixture project before invoking the normal cleanup path.
