# Target Platform Roadmap

> **Navigation**: [Main README](../README.md) | [Target README](README.md) | [Architecture](ARCHITECTURE.md)

## Current state

The active target is Eclipse 2026-06 / Platform 4.40 with Java 21 and Tycho 5.0.4.

Completed baseline work:

- [x] named Eclipse 2026-06 release repository;
- [x] matching Orbit 2026-06 aggregation;
- [x] EGit/JGit and SWTBot repositories;
- [x] Bouncy Castle 1.84 bundle set from Orbit 4.40;
- [x] Java 21 execution environment;
- [x] product and update-site distribution verification;
- [x] Java/JUnit consistency checks across Maven, target, product, p2 category, Oomph, capabilities, and active documentation.

## Next priorities

### Reproducible p2 inputs

The named release line is stable, but installable units declared with `version="0.0.0"` may acquire newer qualifiers inside that repository.

- [ ] Record the resolved IU graph and repository metadata digest as release evidence.
- [ ] Evaluate a project mirror for release builds.
- [ ] Pin complete qualifiers only where the maintenance cost is justified by reproducibility requirements.

### Native runtime coverage

Tycho assembles Windows, Linux, and macOS x86-64 archives, while the full start/install/transform smoke test currently runs on Linux GTK.

- [ ] Add Windows native startup and cleanup-application verification.
- [ ] Add macOS native startup and cleanup-application verification.
- [ ] Decide whether to publish ARM64/AArch64 products before advertising those architectures.

### Baseline update automation

- [ ] Detect a new Eclipse simultaneous release without automatically changing the supported baseline.
- [ ] Create a reviewable update proposal that changes target, Orbit, product, category, Oomph, capability inventory, and documentation together.
- [ ] Require the complete Maven, distribution, SWTBot, inventory, and security gate set before adoption.

### Dependency-boundary clarity

- [ ] Document where a library comes from Maven Central versus p2/Orbit.
- [ ] Continue reducing duplicate version declarations where Tycho and standalone Maven modules can safely share one property.
- [ ] Keep cryptography bundle alignment explicit and covered by distribution verification.

## Deliberate non-goals

- `main` does not currently promise one reactor that supports several Eclipse release lines.
- Local p2 caches are not considered release provenance.
- A successful target resolution alone is not accepted as product-installation evidence.
- Historical QA documents are not rewritten when the current baseline advances.

## Update checklist

For every future Eclipse or Tycho baseline change:

1. update executable configuration;
2. update product, p2, and Oomph provisioning metadata;
3. update the capability inventory and active documentation;
4. run `RepositoryBaselineConsistencyTest`;
5. run the clean Maven and sequential distribution builds;
6. reproduce real-workbench Help/SWTBot evidence;
7. review security and quality gates;
8. record remaining platform gaps explicitly.
