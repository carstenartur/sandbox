# Target Platform Roadmap

> **Navigation**: [Main README](../README.md) | [Target README](README.md) | [Architecture](ARCHITECTURE.md)

This roadmap tracks work that remains after establishing the current Sandbox target-platform baseline. It is not a promise that every listed compatibility mode will be supported.

## Current baseline

| Component | Value |
|---|---|
| Eclipse simultaneous release | Eclipse 2026-06 |
| Eclipse Platform | 4.40 |
| Java execution environment | Java 21 |
| Tycho | 5.0.4 |
| Orbit aggregation | 2026-06 |
| Bouncy Castle | 1.84 from Orbit maven-osgi release 4.40.0 |

`main` builds and tests one active target platform. Compatibility with older Eclipse releases is not currently claimed.

## Completed foundation

- [x] Use a named Eclipse 2026-06 repository instead of a floating Eclipse release URL.
- [x] Align the Orbit aggregation with Eclipse 2026-06.
- [x] Pin the four Bouncy Castle bundles to one 1.84 version set.
- [x] Include JDT, PDE, the Eclipse SDK, EGit/JGit, license information, and SWTBot.
- [x] Validate the target through Tycho before compiling dependent plug-ins.
- [x] Keep product, p2 category, Oomph, capability inventory, and active documentation on the same baseline through `RepositoryBaselineConsistencyTest`.
- [x] Exercise the materialized Linux product and a fresh p2 installation in the distribution gate.

## Active priorities

### 1. Make dependency resolution fully immutable

The named 2026-06 repositories constrain the release line, but installable units declared with `version="0.0.0"` may still resolve to newer qualifier builds published inside that line.

Planned work:

- [ ] inventory every qualifier selected by a clean distribution build;
- [ ] decide between complete IU qualifier pins and a verified repository mirror;
- [ ] verify that an offline build can reproduce the same target from the retained inputs;
- [ ] document mirror retention, integrity checks, and update procedure.

### 2. Add native runtime verification for every published archive

The current distribution gate builds Windows, Linux, and macOS x86-64 archives, but the complete install/start/transform scenario runs natively only on Linux GTK.

Planned work:

- [ ] run a Windows product launch and fresh p2 installation on a Windows runner;
- [ ] run the equivalent scenario on macOS;
- [ ] compare the evidence contract across platforms;
- [ ] advertise a platform as runtime-verified only after its native gate is required.

### 3. Automate coordinated baseline updates

A baseline update must change one reviewed set of files rather than only the Tycho property or target URL.

Planned work:

- [ ] detect a new Eclipse simultaneous release without modifying the repository;
- [ ] prepare one update branch covering Maven, target, product, p2 category, Oomph, capability inventory, and active documentation;
- [ ] keep the generated change blocked until Maven, distribution, Help/SWTBot, patched-JDT-UI, and security gates pass;
- [ ] retain dated QA reports as historical evidence rather than rewriting them.

### 4. Decide whether multi-release support is worth its cost

Multiple target files may be useful for maintenance branches or upstream compatibility checks, but they multiply product, Oomph, UI, and migration-test obligations.

Before implementation:

- [ ] identify a concrete supported older release and user need;
- [ ] define which transformations and product features must work there;
- [ ] decide whether the support belongs on maintenance branches or in profiles on `main`;
- [ ] require separate runtime and screenshot evidence for every advertised target.

Until that decision is made, adding an old target file alone does not establish compatibility.

## Baseline update contract

When the active Eclipse or Tycho baseline changes:

1. update the root `pom.xml` and Java-enforcer diagnostic;
2. update `eclipse.target`, including matching Orbit and Bouncy Castle sources;
3. update `sandbox_product/sandbox.product` and `sandbox_product/category.xml`;
4. update `sandbox_oomph/sandbox.setup`;
5. update `docs/capabilities.json` and regenerate `docs/capabilities.md`;
6. update the contributor, build, distribution, target, product, and Oomph documentation;
7. extend `RepositoryBaselineConsistencyTest` when another active contract is introduced;
8. run the complete required CI set before merging.

## Required verification

The local authoritative commands are:

```bash
mvn -T 1C clean verify
mvn -Pdistribution clean verify
```

The distribution command remains sequential. Linux workbench tests additionally require Xvfb or another real display server.

A baseline update is complete only when the following evidence agrees:

- Maven/Tycho reactor and JUnit results;
- target-platform validation;
- product and p2 repository assembly;
- fresh feature installation, product startup, cleanup execution, and transformed-source compilation;
- Eclipse Help/SWTBot screenshot reproduction;
- patched JDT UI compatibility and atomic-preview evidence;
- capability inventory, CodeQL, and configured quality gates.

## Known constraints

### Network dependency

A first clean build resolves content from Eclipse repositories. Local caches improve later builds but are not authoritative release inputs.

### Qualifier drift

`version="0.0.0"` can select a newer qualifier within a named repository. The target is release-line-pinned, not yet byte-for-byte immutable.

### Download size and duration

The complete target, product, coverage, and real-workbench gates are intentionally substantial. Optimizations must preserve the same semantic and runtime evidence rather than replacing it with a lightweight proxy.

### IDE and Maven are separate contracts

The Oomph-provisioned IDE does not determine the Tycho target automatically. Developers must activate `sandbox_target/eclipse.target` in PDE, while Maven resolves the same file independently.

## Explicit non-goals

- Do not switch the active target to an Eclipse `latest` repository.
- Do not claim compatibility from successful compilation alone.
- Do not add a second script-owned test authority beside Maven/JUnit.
- Do not silently mix Bouncy Castle bundle versions.
- Do not rewrite historical QA records to match a newer baseline.

## References

- [Eclipse target-platform concepts](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm)
- [Tycho target-platform configuration](https://tycho.eclipseprojects.io/doc/latest/target-platform-configuration/target-platform-configuration-mojo.html)
- [Distribution compatibility](../docs/distribution-compatibility.md)
