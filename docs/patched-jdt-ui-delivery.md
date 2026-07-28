# Patched JDT UI delivery

## Purpose

The normal Sandbox target and product use the stock Eclipse 2026-06 / Platform 4.40 JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

This delivery path is separate from the ordinary update site. It proves source provenance, target compatibility, exact p2 publication, installation into the materialized Linux product, product startup, and a real preview/apply/undo cleanup lifecycle before any public patch channel is considered.

## Immutable 4.40 source coordinates

`.github/patched-jdt-ui.env` pins:

- repository: `https://github.com/carstenartur/eclipse.jdt.ui.git`;
- commit: `c9a174e62c32be00bd14c368524d36de75e9fd0f`;
- parent commit: `c922f757b27b7e2b6215db383cec5f8aafd13227`;
- bundle: `org.eclipse.jdt.ui`;
- expected base version: `3.38.0`.

The commit has the official Eclipse 4.40/JDT UI 3.38.0 commit as its sole parent. It contains the two reviewed PR #94 patch files plus a provenance-only synchronization manifest:

- `CleanUpRefactoring.java`;
- `MultiFileCleanUpScopeExpansionTest.java`;
- `.github/fork-specific-files.txt`, listing exactly those two retained patch files.

It contains no 4.41 history or unrelated product changes. The build verifies both patch paths against the manifest before compiling the bundle.

## Local stages

### 1. Build the pinned replacement bundle

```bash
bash .github/scripts/build_patched_jdt_ui.sh target/patched-jdt-ui
```

The script checks out only the immutable commit, verifies the productive source, PDE test and synchronization manifest, builds the single JDT UI bundle, validates singleton identity and the `3.38.0.*` version, and writes SHA-256 provenance.

### 2. Compare against the resolved Sandbox target

```bash
bash .github/scripts/compare_patched_jdt_ui_with_target.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility
```

The gate resolves the checked-in Eclipse 2026-06 target and compares execution environment, required bundles, imports, exports, versions and checksums. A difference is rejected rather than assumed compatible.

### 3. Publish an exact-version p2 repository

```bash
bash .github/scripts/publish_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility \
  target/patched-jdt-ui-p2
```

The repository contains the replacement bundle and a carrier feature that pins its exact version. Repository metadata, artifact identity, sizes and SHA-256 values are verified.

### 4. Build, patch and start the product

```bash
mvn -Pproduct -T 1C --batch-mode clean package -DskipTests
bash .github/scripts/smoke_test_patched_jdt_ui_repository.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation
```

The normal stock product is built first. The p2 director then installs the exact patch feature, verifies the active simpleconfigurator entry and installed bytes, and starts the modified product.

### 5. Execute the real cleanup lifecycle probe

```bash
bash .github/scripts/run_patched_jdt_ui_scope_probe.sh \
  target/patched-jdt-ui-p2 \
  target/patched-jdt-ui-installation \
  target/patched-jdt-ui-runtime-probe
```

The wrapper first proves that the supplied p2 repository and installation evidence name the same feature and the same bundle ID, version and SHA-256. The installed Equinox application then creates two Java compilation units while initially selecting only one. The cleanup's optional `expandCleanUpScope(...)` method discovers the second unit. The probe requires fixed-point expansion, complete preconditions, two previews, atomic apply, a non-null undo change and byte-exact restoration.

## CI evidence

`.github/workflows/patched-jdt-ui-bundle.yml` runs when this delivery surface changes. Each stage consumes the previous stage's immutable artifact and uploads bounded evidence. The stock target and ordinary Sandbox update site remain independent of the optional patch.

## Claim boundary

A green workflow proves the Linux GTK x86_64 product path with Java 21 and the checked-in Eclipse 2026-06 target. It does not claim Windows or macOS runtime execution and does not automatically publish a persistent public patch repository.
