# Contributing to Sandbox

> **Navigation**: [Main README](README.md) | [Build and distribution compatibility](docs/distribution-compatibility.md)

Sandbox is an experimental Java modernization toolkit built on Eclipse JDT. Contributions should preserve the safety boundary of each cleanup, add executable evidence for supported behavior, and keep user-facing documentation consistent with the implementation.

## Supported development baseline

| Component | Current baseline |
|---|---|
| Java | 21 |
| Eclipse target | Eclipse 2026-06 / Platform 4.40 |
| Build system | Maven Wrapper 3.3.4, Maven 3.9.16, Tycho 5.0.4 |
| Default branch | `main` |

The authoritative values are declared in `.mvn/wrapper/maven-wrapper.properties`, `pom.xml`, `sandbox_target/eclipse.target`, and `docs/capabilities.json`. A JUnit repository-consistency test checks the active product, p2 category, Oomph model, and documentation against those values.

## Contribution workflow

1. Fork the repository and create a focused branch from `main`.
2. Implement the change using the existing module and cleanup lifecycle contracts.
3. Add positive, negative, and safety-boundary tests.
4. Update the module README, architecture, installed Help, capability inventory, or roadmap when the public behavior changes.
5. Run the relevant Maven verification from a clean working tree.
6. Open a pull request against `main` and describe the supported scope, rejected cases, tests, and known limitations.

## Building and testing

Only Java 21 is required to bootstrap Maven. Do not depend on a separately installed system Maven; the checked-in wrapper downloads and verifies the pinned Maven distribution.

A normal development verification on Linux or macOS is:

```bash
./mvnw -T 1C clean verify
```

On Windows, run the same reactor through the batch wrapper:

```bat
mvnw.cmd -T 1C clean verify
```

For a focused Maven module and its dependencies, use `./mvnw` on Linux/macOS or `mvnw.cmd` on Windows:

```bash
./mvnw -pl <module> -am clean verify
```

The complete product/update-site gate is deliberately sequential because repository assembly must finish before distribution verification:

```bash
./mvnw -Pdistribution \
  --batch-mode \
  -Dtycho.localArtifacts=ignore \
  clean verify
```

The equivalent Windows command is:

```bat
mvnw.cmd -Pdistribution --batch-mode -Dtycho.localArtifacts=ignore clean verify
```

Linux UI tests require a graphical display; CI supplies Xvfb. Do not use global test-skip or failure-ignore switches to make a transformation appear complete.

## Change guidelines

- Keep changes focused. The repository review target is about 1,500 changed text lines; stop and split at 2,000 unless the pull-request body contains a substantive `## Repository policy exception` explaining why the change is indivisible.
- Treat Eclipse JDT refactorings and cleanup infrastructure as semantic authorities where an existing implementation already owns binding, control-flow, import, formatting, preview, apply, and undo behavior.
- Preserve explicit save-action boundaries. A cleanup that may inspect or modify additional files must not silently become a save action.
- Remove unused imports and follow the existing NLS conventions in Eclipse plug-in code.
- Do not add a parallel Python test or validation framework. Repository semantics belong in Maven/JUnit; workflows should remain thin environment adapters.
- Do not weaken tests, accept unrelated screenshot changes, or update baselines merely to make CI green.

## Updating Eclipse, Maven, or Tycho

A baseline update is one coordinated change, not only a version-property edit. Verify and update, as applicable:

- Maven Wrapper scripts and `.mvn/wrapper/maven-wrapper.properties`;
- root `pom.xml` and Java-enforcer diagnostics;
- `sandbox_target/eclipse.target` and Orbit/Bouncy Castle repositories;
- `sandbox_product/sandbox.product` and `sandbox_product/category.xml`;
- `sandbox_oomph/sandbox.setup`;
- `README.md`, this guide, build references, and distribution documentation;
- `docs/capabilities.json` and generated `docs/capabilities.md`;
- the repository baseline consistency test.

Dated QA records describe their historical baseline and must not be rewritten as though an older review had used a later toolchain.

## Release process

Maintainers publish versioned releases through **Actions → Release Workflow**. The workflow performs the release build, verifies the public p2 repository, creates the release tag and GitHub Release only after successful publication, and then advances the development version. See [.github/workflows/README.md](.github/workflows/README.md#detailed-release-process) for the exact inputs and evidence contract.

## Reporting issues

Please include reproducible source, expected and actual behavior, the selected cleanup options, and the Java/Eclipse versions. For transformation defects, state whether the problem appears in preview, apply, undo, save actions, or the headless cleanup application.
