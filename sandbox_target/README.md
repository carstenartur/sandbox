# Target Platform

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [Roadmap](TODO.md)

`sandbox_target` is the PDE target-definition module used by Tycho and by Eclipse developers working on Sandbox. It defines the APIs and installable units against which every Eclipse plug-in module is compiled and tested.

## Current baseline

| Component | Value |
|---|---|
| Eclipse simultaneous release | Eclipse 2026-06 |
| Eclipse Platform | 4.40 |
| Java execution environment | Java 21 |
| Tycho | 5.0.4, from the root `pom.xml` |
| Orbit aggregation | 2026-06 |
| Bouncy Castle | 1.84 from Orbit maven-osgi release 4.40.0 |

This is a named, pinned release line rather than a floating `latest` Eclipse repository. Installable units with version `0.0.0` select the newest matching IU currently published inside those named repositories, so a completely immutable build would additionally require a mirrored or qualifier-pinned repository snapshot.

Sandbox is built and tested against Eclipse 2026-06. Compatibility with older Eclipse releases is not claimed by the current automated gates.

## Authoritative files

- `eclipse.target` — PDE/ Tycho target definition.
- `pom.xml` — target-definition Maven artifact.
- root `pom.xml` — Tycho, Java, and matching p2 repository configuration.
- `../docs/capabilities.json` — machine-readable public baseline.

`RepositoryBaselineConsistencyTest` verifies that the target, root build, product, p2 category, Oomph setup, capability inventory, and active documentation describe the same Eclipse release.

## Repositories and installable units

The target currently resolves:

1. Eclipse 2026-06 SDK, JDT, PDE, executable, AST View, Java Element View, and PDE spies;
2. the matching Orbit 2026-06 aggregation for Apache Commons bundles;
3. the Eclipse license feature;
4. EGit and JGit;
5. Bouncy Castle 1.84 bundles from the Orbit 4.40 maven-osgi repository;
6. SWTBot for real-workbench UI tests.

The exact list is declared in `eclipse.target`; this README is explanatory and must not be treated as a substitute for that file.

## Building with the target

From the repository root:

```bash
mvn -T 1C clean verify
```

Tycho builds `sandbox_target` first and resolves all dependent plug-in modules against it. To exercise product and p2 packaging as well:

```bash
mvn -Pdistribution clean verify
```

The distribution build is intentionally sequential.

## Using the target in Eclipse

1. Open `sandbox_target/eclipse.target` in the PDE Target Definition editor.
2. Let PDE resolve all repositories and installable units.
3. Select **Set as Active Target Platform**.
4. Check the Problems view before importing or editing plug-in projects.

The Oomph setup provisions the IDE and records the same default release, but it does not replace PDE target activation. IDE provisioning and the workspace target are related, separate contracts.

## Updating the baseline

Treat an Eclipse baseline update as one coordinated transaction:

1. update `eclipse.target`, including matching Orbit and Bouncy Castle sources;
2. update root `pom.xml` repositories and any API version pins;
3. update `sandbox_product/sandbox.product` and `sandbox_product/category.xml`;
4. update `sandbox_oomph/sandbox.setup`;
5. update `docs/capabilities.json` and regenerate `docs/capabilities.md`;
6. update active build, contribution, target, product, and Oomph documentation;
7. run Maven, capability, distribution, Help/SWTBot, and security gates.

Do not rewrite dated QA records to make an earlier review appear to have used the new baseline.

## Troubleshooting

### Target does not resolve

Verify the named release and Orbit URLs, then clear only the relevant local p2/Tycho cache if a corrupt download is proven. Do not solve a resolution conflict by silently switching one file to a different Eclipse release.

### Bundle version conflict

Compare the target IU list with the root `target-platform-configuration` extra requirements. Bouncy Castle is intentionally aligned as a four-bundle 1.84 set.

### IDE and Maven disagree

Confirm that `eclipse.target` is the active PDE target and that Maven runs with Java 21. The active IDE installation alone does not determine Tycho's target platform.
