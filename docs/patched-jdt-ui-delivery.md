# Patched JDT UI delivery

## Purpose

The normal Sandbox target and product use the stock Eclipse 2026-09 / Platform 4.41 JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

This delivery path is separate from the ordinary update site. It proves source provenance, target compatibility, exact p2 publication, installation into the materialized Linux product, product startup, and a real preview/apply/undo cleanup lifecycle before any public patch channel is considered.

## Immutable 4.41 source coordinates

`.github/patched-jdt-ui.env` pins:

- repository: `https://github.com/carstenartur/eclipse.jdt.ui.git`;
- commit: `3ad88260994f8410e2c2e055d2eafc26b9ff689b`;
- parent commit: `54bd63e6453a7eb5db0bfc1d60f17e9568095619`;
- bundle: `org.eclipse.jdt.ui`;
- expected base version: `3.39.0`;
- target JDT feature: `org.eclipse.jdt` version `3.20.700.v20260828-1142`.

The commit has the official Eclipse 4.41/JDT UI release commit as its sole parent. The build verifies this parent directly from the immutable Git commit object, including in the depth-one checkout, and records the verified `sourceParent` in `provenance.json`.

The retained patch includes scope expansion and coordinated-preview support. The synchronization manifest preserves the fork-specific files. Before compilation, the build checks the scope-expansion source and test against that manifest:

- `CleanUpRefactoring.java`;
- `MultiFileCleanUpScopeExpansionTest.java`;
- `.github/fork-specific-files.txt`.

The runtime baseline must not be confused with the historical R4_40 source corpus used by the before/after migration tests. That corpus retains its original commit and provenance.

## Local stages

### 1. Build the pinned replacement bundle

```bash
bash .github/scripts/build_patched_jdt_ui.sh target/patched-jdt-ui
```

The script checks out only the immutable commit, verifies its sole parent, productive source, PDE test and synchronization manifest, builds the single JDT UI bundle, validates singleton identity and the `3.39.0.*` version, and writes SHA-256 provenance.

### 2. Compare against the resolved Sandbox target

```bash
bash .github/scripts/compare_patched_jdt_ui_with_target.sh \
  target/patched-jdt-ui \
  target/patched-jdt-ui-compatibility
```

The gate resolves the checked-in Eclipse 2026-09 target and compares execution environment, required bundles, imports, exports, versions and checksums. A difference is rejected rather than assumed compatible.

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

A green workflow proves the Linux GTK x86_64 product path with Java 21 and the checked-in Eclipse 2026-09 target. It does not claim Windows or macOS runtime execution and does not automatically publish a persistent public patch repository.
