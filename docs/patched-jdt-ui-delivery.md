# Patched JDT UI delivery

## Purpose

The normal Sandbox target and cleanup test reactor use the stock Eclipse JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

The delivery pipeline has four fail-closed stages:

1. reproducible source-bundle build from an immutable fork commit;
2. strict comparison with the exact stock bundle selected by the checked-in Eclipse 2025-12 target;
3. publication of a minimal p2 repository containing the replacement and an exact-version carrier feature;
4. installation into an independently materialized stock Linux product, followed by singleton-selection and startup checks.

Runtime apply/undo coverage for a real multi-file cleanup and publication to a persistent public channel remain tracked in #1209.

## Pinned source

The immutable coordinates are stored in `.github/patched-jdt-ui.env`:

- repository: `https://github.com/carstenartur/eclipse.jdt.ui.git`;
- commit: `450bfd46089c99608dd60203e1257e1c329ad2c5`;
- bundle: `org.eclipse.jdt.ui`;
- expected base version: `3.39.0`.

The commit is the merged result of `carstenartur/eclipse.jdt.ui#95`. Its fork synchronization manifest preserves both the modified `CleanUpRefactoring.java` source and `MultiFileCleanUpScopeExpansionTest.java`.

## Reproducible bundle build

Requirements are Git, Java 21, Maven 3.9.x, JDK tools, Python 3 and access to the pinned fork plus Eclipse build repositories.

```bash
bash .github/scripts/build_patched_jdt_ui.sh target/patched-jdt-ui
```

The script:

1. fetches and checks out only the exact 40-character commit;
2. verifies the productive patch source, PDE test and synchronization entries;
3. invokes the upstream `build-individual-bundles` Maven profile for `org.eclipse.jdt.ui`;
4. requires exactly one non-source bundle artifact;
5. validates the singleton bundle symbolic name;
6. requires a qualified `3.39.0.*` OSGi version;
7. verifies the compiled `CleanUpRefactoring` contains the scope-expansion marker;
8. emits the bundle, manifest evidence and `provenance.json` with SHA-256.

Generated JARs are not committed. CI publishes the verified output as a short-lived artifact.

## Stock-target compatibility gate

After building the bundle, CI runs:

```bash
bash .github/scripts/compare_patched_jdt_ui_with_target.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility
```

The comparison uses an isolated Maven repository, resolves `sandbox_target/eclipse.target` through the normal Tycho build and identifies the single stock JDT UI bundle selected from Eclipse 2025-12. It records:

- the exact stock and patched OSGi versions and SHA-256 checksums;
- whether the patched version is strictly newer, as required for singleton replacement;
- normalized differences in `Bundle-RequiredExecutionEnvironment`, `Require-Bundle` and `Import-Package`;
- whether every stock `Export-Package` remains present in the patched bundle.

Results are written to `compatibility.json` and `compatibility.md`. The p2 stage cannot run unless `compatibleForReplacement` is exactly `true`.

## Minimal exact-version p2 repository

The repository is generated from the already verified bundle and compatibility evidence:

```bash
bash .github/scripts/publish_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility \
  target/patched-jdt-ui-p2
```

The publisher creates a temporary source layout and invokes Tycho's Features and Bundles Publisher. The repository contains only:

- `org.eclipse.jdt.ui_<qualified-version>.jar`;
- `sandbox_patched_jdt_ui_feature_<derived-version>.jar`;
- compressed p2 metadata and artifact indexes.

The carrier feature pins the replacement through both its feature plug-in entry and a `match="perfect"` requirement. Tycho materializes those dependencies on the feature-group IU and keeps the feature-jar IU as the feature artifact carrier. Its qualifier is derived from the reviewed bundle version, so rebuilding the same pinned source produces the same feature identity.

After publication, `.github/scripts/add_p2_sha256_checksums.py` recomputes the local artifact bytes and writes size plus SHA-256 metadata for every bundle and feature artifact. `.github/scripts/verify_patched_jdt_ui_repository.py` then requires:

- exactly one bundle IU with the provenance-bound version;
- exactly one feature-group IU and feature-jar IU;
- exact feature-group requirements for both the feature-jar IU and patched bundle IU;
- exactly one bundle artifact and one feature artifact;
- referenced files, declared sizes and a verifiable checksum for every artifact;
- byte equality between the published bundle and the original SHA-256 provenance;
- a packaged `feature.xml` with the expected IDs and exact version.

The resulting repository and JSON/Markdown evidence are uploaded as a workflow artifact. This is an installable CI artifact, not yet a permanent public update-site channel.

## Stock-to-patched installation smoke test

The final workflow job builds the normal stock Linux GTK x86_64 product without consuming the patch repository. It records the stock `org.eclipse.jdt.ui` entry from `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`, then installs the exact patch feature into that materialized profile with the p2 director.

```bash
bash .github/scripts/smoke_test_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation
```

The smoke test requires:

- one and only one stock JDT UI selection before installation;
- a different, exact patched version after installation;
- one active simpleconfigurator entry for `org.eclipse.jdt.ui`;
- installed bundle bytes matching the pinned SHA-256;
- the patch feature reported as an installed root;
- successful startup of the modified product through the p2 director application.

Because the stock product is built before the patch repository is introduced, the same workflow also proves that the ordinary target remains independently materializable.

## Workflow evidence

`.github/workflows/patched-jdt-ui-bundle.yml` runs for matching pull requests, matching pushes to `main`, and manual dispatch. It publishes bounded-retention artifacts for:

- source-bundle build diagnostics;
- verified bundle and source provenance;
- stock-target compatibility;
- exact-version p2 repository and repository verification;
- stock-versus-patched installation and startup evidence.

Cached Maven data may accelerate resolution, but every trust decision is recomputed from pinned source, an isolated target-resolution repository or artifact checksums.

## Rollback and upgrade boundary

The patch is not added to the normal target, normal update site or default product definition. Omitting the optional patch repository and feature therefore rebuilds the stock product. Before advancing the Eclipse target or pinned fork revision, the complete bundle, compatibility, p2 and installation pipeline must pass again; a changed bundle qualifier produces a new feature identity rather than overwriting prior evidence.

No generated JAR is committed. A persistent public patch channel must add immutable versioned publication, public URL verification and retention/rollback policy before promotion.

## Remaining #1209 work

The current stages prove source provenance, manifest compatibility, p2 metadata, exact singleton selection and product startup. They do not yet prove that a running patched product invokes scope expansion for a real cleanup, previews the related compilation unit, applies the complete change and undoes it. That behavioral product test, plus persistent release-channel publication and rollback, remains required before #1209 can close.
