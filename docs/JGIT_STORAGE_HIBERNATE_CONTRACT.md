# jgit-storage-hibernate consumer contract

## Purpose

Sandbox is a real consumer of the released `jgit-storage-hibernate` library. This contract protects the integration from two different classes of regression:

1. a new library candidate breaks the Sandbox Core adapter, repository lifecycle, legacy-schema preflight, OSGi metadata, or standalone server packaging;
2. Sandbox silently starts consuming an upstream module whose migration slice has not yet been implemented.

The executable contract is [`.github/jgit-storage-hibernate-contract.sh`](../.github/jgit-storage-hibernate-contract.sh).

## Authoritative module scope

Sandbox currently consumes exactly one released module:

```text
jgit-storage-hibernate-core
```

The following modules are deliberately rejected by the contract:

```text
jgit-storage-hibernate-search
jgit-storage-hibernate-java-analysis
jgit-storage-hibernate-architecture
jgit-storage-hibernate-benchmarks
```

This is not a statement that those modules are unwanted. Search and Java Analysis are later migration slices because Sandbox still owns copied projections, database mappings, REST composition, and compatibility behavior that must be cut over deliberately. Adding one of those dependencies before the corresponding migration work would hide an incomplete integration behind a green dependency resolution.

The selected Core version is defined by `jgit-storage-hibernate.version` in `sandbox-jgit-storage-hibernate/pom.xml`. Documentation must not be treated as the version source of truth.

## How the central library invokes it

The `jgit-storage-hibernate` repository owns the real-consumer matrix. Its descriptor lists Sandbox with:

- the Sandbox repository and pinned consumer commit;
- `.github/jgit-storage-hibernate-contract.sh` as the consumer-owned entry point;
- `jgit-storage-hibernate-core` as the only expected module.

For a candidate run, the library workflow:

1. installs the same-run library candidate into an isolated Maven repository;
2. patches only the upstream library version in Sandbox POM files;
3. rejects any non-POM change made by substitution;
4. runs the Sandbox script with `JGIT_STORAGE_HIBERNATE_CONTRACT_MODE=candidate` and the expected candidate version;
5. uploads the consumer-owned result, dependency trees, manifests, build logs, and JUnit reports.

Scheduled and baseline runs execute the same script without replacing the selected released version.

## Contract modes

### Baseline

```bash
JGIT_STORAGE_HIBERNATE_CONTRACT_MODE=baseline \
  bash .github/jgit-storage-hibernate-contract.sh
```

The script resolves the version selected by the consumer POM and verifies the current integration.

### Candidate

```bash
JGIT_STORAGE_HIBERNATE_CONTRACT_MODE=candidate \
JGIT_STORAGE_HIBERNATE_CANDIDATE_VERSION=<version> \
  bash .github/jgit-storage-hibernate-contract.sh
```

Candidate mode additionally requires the patched POM property and the resolved Maven dependency tree to contain exactly the requested candidate version.

## What is verified

The contract uses Java 21 and builds the two storage consumer modules independently from the unrelated Eclipse cleanup reactor.

It verifies:

- the released Core dependency resolves at the selected/candidate version;
- Search, Java Analysis, Architecture, and benchmark modules do not leak in transitively;
- the Sandbox public integration-boundary tests pass;
- repository lifecycle, restart, deletion policy, and legacy-schema preflight tests pass;
- Bnd accepts the package instructions without malformed-property warnings;
- the generated manifest has the expected bundle symbolic name;
- `org.sandbox.jgit.storage.integration` is exported;
- released `io.github.carstenartur.jgit.storage.hibernate.*` packages are imported;
- the bridge JAR contains `JGitStorageLibraryBoundary`;
- the standalone shaded server contains the legacy preflight and released Core factory classes;
- JUnit XML reports are produced.

No Xvfb session is needed because this contract has no SWT or workbench path.

## Evidence

Each run writes `target/jgit-storage-hibernate-contract/`, including:

- `result.json` — machine-readable scope and resolved version;
- `dependency-tree.txt` — combined consumer dependency evidence;
- `bridge-manifest.mf` and an unfolded manifest — OSGi metadata evidence;
- module build logs;
- the list of JUnit XML reports.

The central library workflow uploads this directory even when a contract fails, together with consumer substitution evidence.

## Changing the scope

A new upstream module may be added only with the migration slice that proves its actual Sandbox behavior. That change must update together:

1. Sandbox POM dependencies and adapters;
2. tests for the migrated capability;
3. this contract's expected and forbidden module lists;
4. the central library's `expectedModules` descriptor;
5. ownership/migration documentation.

Resolving an artifact alone is not sufficient integration evidence.
