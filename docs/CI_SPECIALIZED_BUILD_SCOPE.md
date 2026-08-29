# Specialized CI build scope

Sandbox keeps one authoritative, cross-platform verification path and several
specialized Linux-only evidence paths. The specialized jobs may avoid work that
is already performed authoritatively, but they must not remove semantic tests,
corpus checks, screenshot assertions, provenance, or distribution coverage.

## Authoritative gates

The following workflows retain the normal Windows, Linux, and macOS Tycho target
and run the compile-bound SpotBugs checks:

- `Java CI with Maven` (`.github/workflows/maven.yml`);
- `Distribution Smoke Test` (`.github/workflows/distribution-smoke.yml`).

Release and distribution builds therefore continue to prove that the complete
multi-platform target and all product formats can be resolved and assembled.
The Linux-only optimization is never active by default.

## Opt-in Linux-only Tycho scope

Specialized jobs running exclusively on `ubuntu-24.04` pass:

```text
-Dsandbox.tycho.linux-only=true
```

That property activates two deliberately separate Maven profiles:

1. `linux-only-tycho` in the root `pom.xml` replaces the inherited Tycho target
   environment list with exactly `linux/gtk/x86_64`.
2. `linux-only-product` in `sandbox_product/pom.xml` replaces the product archive
   format map with exactly the Linux `tar.gz` format.

Both list replacements use Maven's `combine.self="override"`. Without an
explicit override, Maven could merge the Linux entry with the normal Windows and
macOS entries and silently preserve the expensive cross-platform work.

The property is used only by:

- the ordinary Eclipse Help screenshot reproduction gate;
- the patched-JDT-UI atomic preview gate;
- the pinned JDT UI/JUnit 4 before/after migration gate;
- the focused patched-JDT-UI compatibility build.

## SpotBugs boundary

Those same specialized Maven invocations pass:

```text
-Dspotbugs.skip=true
```

This removes duplicate static analysis from evidence jobs whose purpose is UI,
product-runtime, real-corpus, or replacement-bundle verification. It does not
change compilation, tests, SWTBot scenarios, before/after comparison, whitespace
regression checks, screenshot comparison, provenance collection, or artifact
upload.

SpotBugs remains mandatory in the authoritative Java CI and distribution gates.
A Maven/JUnit policy test,
`CiBuildScopeContractTest`, fails if either authoritative workflow adopts the
Linux-only property or the SpotBugs skip flag.

## Preserved semantic authority

The optimization does not change the selected test classes or real-corpus
runner:

- `SandboxHelpScreenshotsMergeGateSWTBotTest` still drives the normal Help gate;
- `SandboxAtomicPreviewPatchedJdtSWTBotTest` still drives both coordinated atomic
  preview scenarios;
- `run-jdt-ui-before-after.sh --mode strict` still performs project-wide check
  and apply, identical upstream test execution, inventory comparison, whitespace
  regression analysis, and provenance generation.

The specialized jobs continue to use `clean verify`. Only target-platform breadth
and duplicate static analysis differ.

## Local reproduction

The optimized Help command is:

```bash
mvn --batch-mode --no-transfer-progress \
  -Dsandbox.tycho.linux-only=true \
  -Dspotbugs.skip=true \
  -Dtycho.localArtifacts=ignore \
  -Dhelp.screenshot.testClass=org.sandbox.jdt.ui.helper.views.SandboxHelpScreenshotsMergeGateSWTBotTest \
  -DfailIfNoTests=false \
  -f sandbox_help_build/pom.xml \
  -Phelp-screenshots \
  clean verify
```

Omit both optimization properties when reproducing an authoritative or
cross-platform build.

## p2 repository measurement boundary

The root POM currently registers broad p2 repositories while
`sandbox_target/eclipse.target` selects named installable units from several of
the same sources. Removing either source without evidence could change resolver
candidates, referenced-repository behavior, source-bundle availability, or
product materialization. This change therefore removes no p2 repository.

A later optimization must first compare cold and warm runs on the same commit and
record at least:

- complete `sandbox_target` duration;
- target-definition resolution duration;
- `validate-classpath` duration for `sandbox_common`;
- resolved installable-unit and bundle versions;
- product/update-site contents and platform inventory;
- complete test, screenshot, corpus, and distribution results.

Only a configuration producing the same selected artifacts and evidence may
replace the current repository combination. This keeps build-speed work
measurable and prevents a fast but semantically weaker target platform.
