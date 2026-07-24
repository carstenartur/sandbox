# Patched JDT UI delivery

## Purpose

The normal Sandbox target and cleanup test reactor use the stock Eclipse JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

The delivery pipeline has five fail-closed stages:

1. reproducible source-bundle build from an immutable fork commit;
2. strict comparison with the exact stock bundle selected by the checked-in Eclipse 2025-12 target;
3. publication of a minimal p2 repository containing the replacement and an exact-version carrier feature;
4. installation into an independently materialized stock Linux product, followed by singleton-selection and startup checks;
5. a real Equinox cleanup probe that expands one selected Java file to two targets, previews and applies both changes, and undoes them byte-for-byte.

Persistent publication to a public versioned patch channel remains separate from the checkout and workflow-artifact proof.

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

The carrier feature pins the replacement through both its feature plug-in entry and a `match="perfect"` requirement. Its qualifier is derived from the reviewed bundle version, so rebuilding the same pinned source produces the same feature identity.

`.github/scripts/verify_patched_jdt_ui_repository.py` then requires:

- exactly one bundle IU with the provenance-bound version;
- exactly one feature-group IU and feature-jar IU;
- an exact feature-jar-to-bundle requirement;
- exactly one bundle artifact and one feature artifact;
- referenced files, declared sizes and at least one verifiable checksum per artifact;
- byte equality between the published bundle and the original SHA-256 provenance;
- a packaged `feature.xml` with the expected IDs and exact version.

The resulting repository and JSON/Markdown evidence are uploaded as an installable workflow artifact. This satisfies the reproducible-download boundary without silently promoting the patch into the normal Sandbox update site.

## Stock-to-patched installation smoke test

The final workflow job first builds the normal stock Linux GTK x86_64 product without consuming the patch repository. It records the stock `org.eclipse.jdt.ui` entry from `configuration/org.eclipse.equinox.simpleconfigurator/bundles.info`, then installs the exact patch feature into that materialized profile with the p2 director.

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

## Runtime scope-expansion, preview, apply, and undo probe

The repository-owned probe lives under `.github/probes/patched-jdt-ui/`. It is compiled against the exact installed product, packaged as an Equinox application bundle and published to a temporary one-bundle p2 repository. No generated probe binary is committed.

```bash
bash .github/scripts/run_patched_jdt_ui_scope_probe.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation \
  target/patched-jdt-ui-runtime-probe
```

The probe creates a temporary Java project with `First.java` and `Second.java`. It adds only `First.java` to `CleanUpRefactoring`; its cleanup exposes the optional public `expandCleanUpScope(...)` method and returns `Second.java`. The installed patched host must then:

1. invoke expansion repeatedly until no target is added;
2. pass both compilation units to cleanup preconditions;
3. expose two text-change previews;
4. apply one composite change that inserts the deterministic marker into both files;
5. return a non-null undo change;
6. restore both original UTF-8 files byte-for-byte.

The application writes `probe-result.json` and prints `PATCHED_JDT_UI_SCOPE_PROBE_PASS`. The orchestration script independently requires exact counts for targets, planned units, previews, applied files and restored files, plus at least two expansion invocations proving fixed-point evaluation. The temporary project is deleted after success or failure.

## Workflow evidence

`.github/workflows/patched-jdt-ui-bundle.yml` runs for matching pull requests, matching pushes to `main`, and manual dispatch. It publishes bounded-retention artifacts for:

- source-bundle build diagnostics;
- verified bundle and source provenance;
- stock-target compatibility;
- exact-version p2 repository and repository verification;
- stock-versus-patched installation and startup evidence;
- runtime scope-expansion, preview, apply and undo evidence.

Cached Maven data may accelerate resolution, but every trust decision is recomputed from pinned source, an isolated target-resolution repository, product-selected bundles or artifact checksums.

## Rollback and upgrade boundary

The patch is not added to the normal target, normal update site or default product definition. Omitting the optional patch repository and feature therefore rebuilds the stock product; the installation evidence records the stock version before replacement. Before advancing the Eclipse target or pinned fork revision, the complete bundle, compatibility, p2, installation and behavior pipeline must pass again. A changed bundle qualifier produces a new carrier-feature identity rather than overwriting prior evidence.

No generated JAR is committed. A persistent public patch channel must use immutable versioned publication, verify its public URL and retain a documented prior version or stock reconstruction path before promotion.

## Claim boundary

Passing the complete workflow proves pinned-source reconstruction, stock-manifest compatibility, exact p2 metadata, one selected singleton replacement, product startup, real target expansion, preview, application and byte-exact undo on Linux GTK x86_64 with Java 21 and the checked-in Eclipse 2025-12 target. It does not claim that the optional patch is part of the normal Sandbox release channel or that other operating-system products have executed the runtime probe.
