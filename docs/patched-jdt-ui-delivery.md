# Patched JDT UI delivery

## Purpose

The normal Sandbox target and cleanup test reactor use the stock Eclipse JDT UI bundle. Automatic multi-file cleanup scope expansion is an optional product capability that requires a reviewed replacement of the singleton `org.eclipse.jdt.ui` bundle.

This document covers the reproducible source-bundle stage only. Minimal p2 publication, exact-IU resolution, product startup and stock-versus-patched smoke tests remain tracked in #1209 and #1215.

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

## Trust boundary

Passing this stage proves the source revision, patch paths, singleton identity, compiled capability marker and artifact checksum. It deliberately does not yet prove p2 replacement selection, product installation/startup, runtime cleanup expansion or rollback. Those checks belong to the p2/product stages before an installation channel is published.

The stock target remains the default and has no dependency on this optional bundle artifact.
