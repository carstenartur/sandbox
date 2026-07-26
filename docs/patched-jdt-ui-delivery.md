# Patched JDT UI delivery

## Purpose

The normal Sandbox target and default Maven build use the stock Eclipse 2026-06 JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

The replacement lane is isolated from ordinary `main` validation. It runs for pull requests that change the delivery implementation and by manual dispatch after merge. The stock product remains the supported default.

## Pinned source

The immutable coordinates are stored in `.github/patched-jdt-ui.env`:

- repository: `https://github.com/carstenartur/eclipse.jdt.ui.git`;
- commit: `450bfd46089c99608dd60203e1257e1c329ad2c5`;
- bundle: `org.eclipse.jdt.ui`;
- expected base version: `3.39.0`.

The commit is the merged result of `carstenartur/eclipse.jdt.ui#95`. Its fork synchronization manifest preserves both the modified `CleanUpRefactoring.java` source and `MultiFileCleanUpScopeExpansionTest.java`.

## Local rebuild

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

## Eclipse 2026-06 compatibility gate

After building the bundle, CI runs:

```bash
bash .github/scripts/compare_patched_jdt_ui_with_target.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility
```

The comparison removes only cached `org.eclipse.jdt.ui` artifacts, resolves `sandbox_target/eclipse.target` through the normal Tycho build and identifies the single stock JDT UI bundle selected from Eclipse 2026-06. It records:

- the exact stock and patched OSGi versions and SHA-256 checksums;
- whether the patched version is strictly newer, as required for singleton replacement;
- normalized differences in `Bundle-RequiredExecutionEnvironment`, `Require-Bundle` and `Import-Package`;
- whether every stock `Export-Package` remains present in the patched bundle.

A manifest or export-surface difference fails closed. The p2 repository is not produced unless `compatibleForReplacement` is true.

## Exact-version p2 repository

```bash
bash .github/scripts/publish_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility \
  target/patched-jdt-ui-p2
```

The publisher creates a minimal carrier feature whose plug-in entry and p2 requirement both pin the exact qualified patched bundle version. Repository verification requires:

- exactly one patched bundle IU;
- exactly one feature-group and feature-jar IU;
- exact requirements from the feature group to its feature jar and replacement bundle;
- only the expected bundle and feature artifacts;
- file-size and checksum metadata for every artifact;
- byte-for-byte agreement with the source-build SHA-256 provenance.

## Stock-to-patched installation

Build the ordinary Eclipse 2026-06 product first:

```bash
mvn -Pproduct --batch-mode -Dtycho.localArtifacts=ignore clean verify
```

Then install and verify the optional repository:

```bash
bash .github/scripts/smoke_test_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation
```

The smoke test records the stock selection, installs the exact feature with the product's own p2 director and requires:

- one active `org.eclipse.jdt.ui` simpleconfigurator entry;
- the exact patched version and SHA-256;
- the carrier feature as an installed profile root;
- successful product startup through the p2 director.

## Runtime behavior proof

The repository-owned Equinox application under `.github/probes/patched-jdt-ui/` is compiled against the plug-ins of the materialized patched product and published through a temporary one-bundle p2 repository. It is then installed into that same profile.

```bash
bash .github/scripts/run_patched_jdt_ui_scope_probe.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation \
  target/patched-jdt-ui-runtime-probe
```

The probe creates two Java compilation units, explicitly selects only the first and exposes `expandCleanUpScope(...)` to return the second. Success requires:

- repeated scope expansion to a fixed point;
- both compilation units in preconditions;
- two text-change previews;
- application of one composite cleanup change to both files;
- a non-null undo change;
- byte-for-byte restoration of both original UTF-8 files.

The bounded JSON and Markdown evidence includes exact target, planning, preview, apply and restore counts. The temporary workspace project is deleted after success or failure.

## Trust boundary

Passing the complete lane proves the pinned source revision, singleton identity, strict compatibility with the Eclipse 2026-06 stock target, exact p2 publication, installation into the stock product and real cleanup lifecycle behavior on Linux GTK x86_64 with Java 21.

It does not add the replacement to the normal Sandbox update site and does not claim runtime execution on Windows or macOS. The stock target remains independent of this optional artifact.
