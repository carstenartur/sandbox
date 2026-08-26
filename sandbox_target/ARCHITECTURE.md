# Target Platform Architecture

> **Navigation**: [Main README](../README.md) | [Target README](README.md) | [Roadmap](TODO.md)

## Responsibility

`sandbox_target` defines the external Eclipse/OSGi world in which Sandbox plug-ins are compiled and tested. It is a build-configuration module, not an Eclipse bundle and not application code.

The current executable baseline is Eclipse 2026-06 / Platform 4.40, Java 21, and Tycho 5.0.4.

## Resolution flow

```text
root pom.xml
  - Java and Tycho versions
  - matching release repositories
  - target-platform-configuration
              |
              v
sandbox_target/pom.xml
  packages eclipse.target as a target-definition artifact
              |
              v
sandbox_target/eclipse.target
  resolves Eclipse, Orbit, EGit, Bouncy Castle and SWTBot IUs
              |
              v
Tycho reactors, PDE workspace, product and update-site builds
```

Every plug-in manifest is resolved against this graph. A dependency that is available in the developer's running IDE but absent from the target is not part of the supported build.

## Repository roles

### Eclipse 2026-06

Provides the SDK, Platform, JDT, PDE, executable feature, AST View, Java Element View, and PDE spies. The target and root POM both use the named `2026-06` simultaneous-release repository.

### Orbit 2026-06

Provides third-party OSGi bundles used by the target, including Apache Commons IO and Lang.

### Orbit maven-osgi 4.40.0

Provides the explicitly aligned Bouncy Castle 1.84 bundle set:

- `bcutil`
- `bcprov`
- `bcpkix`
- `bcpg`

The root Tycho configuration declares the same versions as extra requirements so the resolver cannot mix an accidental older cryptography bundle into the product.

### EGit/JGit

The EGit update site supplies the Eclipse integration features. Standalone Maven modules may additionally use the root Maven JGit version; those are separate dependency surfaces and must not be conflated.

### SWTBot

The SWTBot repository supplies real-workbench test infrastructure used for Help screenshots and UI execution evidence.

## Version-selection policy

Most Eclipse feature units use `version="0.0.0"`. This means “newest matching IU in the named repository”, not “newest Eclipse release on the internet”. The release line is fixed at 2026-06, but qualifier-only repository updates can still change resolution over time.

Critical externally mixed bundles are pinned explicitly. A future requirement for byte-for-byte offline resolution should be met with a checked and mirrored p2 repository or complete IU qualifier pins, not with undocumented local caches.

## Related runtime and publication models

The target is one part of a coordinated baseline:

- `sandbox_product/sandbox.product` declares what the materialized IDE contains and which repositories it exposes.
- `sandbox_product/category.xml` declares repository references shown to p2 clients installing published features.
- `sandbox_oomph/sandbox.setup` provisions the contributor IDE and carries the same default Eclipse release.
- `docs/capabilities.json` publishes the Java, Tycho, and Eclipse baseline.
- root `pom.xml` configures Tycho and matching repositories.

These files cannot be generated from one another with the current toolchain, so `RepositoryBaselineConsistencyTest` treats their agreement as an executable invariant.

## Build invariants

1. Maven runs with Java 21.
2. Root POM and target resolve one named Eclipse release.
3. Orbit aggregation matches that release.
4. Product, p2 category, Oomph default, and capability inventory use the same release identifier.
5. The root Tycho property and capability inventory use the same Tycho version.
6. Bouncy Castle bundles form one compatible version set.
7. Distribution verification installs from the generated p2 repository rather than trusting reactor success alone.

## Updating the architecture

An Eclipse or Tycho upgrade must update the complete invariant set and pass:

- the normal Maven reactor;
- repository baseline and capability inventory tests;
- product/update-site distribution verification;
- real-workbench Help/SWTBot gates;
- CodeQL and configured quality gates.

A partial update that merely compiles is not complete if published p2 metadata, Oomph provisioning, or active documentation still identifies another release.

## Non-goals

- Supporting multiple Eclipse release lines from one `main` build is not currently claimed.
- The Oomph-provisioned IDE is not the authority for Maven dependency resolution.
- `0.0.0` IU versions are not presented as fully immutable dependency pins.
- Target configuration does not establish runtime verification for every archive platform; native coverage is documented separately in `docs/distribution-compatibility.md`.
