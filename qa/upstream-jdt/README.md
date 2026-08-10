# Upstream JDT migration QA

This directory defines a reproducible, evidence-producing migration scenario on
real Eclipse JDT source code. It is deliberately separate from normal pull
request CI because provisioning the complete JDT development workspace and
executing the upstream tests is expensive.

The first scenario exercises Sandbox's planned **JUnit 3 to Jupiter** migration
on the `org.eclipse.jdt.apt.tests` project from the Eclipse 4.40 release. The
selected corpus contains real `TestCase` hierarchies, named constructors,
`suite()` aggregators, lifecycle methods and message-first assertions.

## Reproducibility boundary

The following values are fixed in both `pins.env` and the Oomph model:

| Component | Ref | Commit |
|---|---|---|
| Eclipse SDK | 4.40 / 2026-06 | release repository `R-4.40-202606010713` |
| JDT Core | `R4_40` | `ef3d6f2115df89d7964bc13aa363ab8d6bd21256` |
| JDT UI | `R4_40` | `c922f757b27b7e2b6215db383cec5f8aafd13227` |
| JDT Core test binaries | `R4_40` | `0b8255ae33fc91724774ab2b550276191f9db416` |

The runner rejects a different remote, tag resolution or `HEAD`. It also
requires a clean JDT Core checkout before it starts.

## 1. Provision with Eclipse Installer Advanced Mode

Import:

```text
sandbox_oomph/jdt-migration-qa.configuration.setup
```

The configuration uses Eclipse SDK 4.40 and the accompanying project setup to:

- clone Sandbox;
- clone JDT Core, JDT UI and JDT Core test binaries at `R4_40`;
- resolve the official PDE/JDT target through Oomph Targlets;
- import the JDT test projects needed by the scenario;
- import the Sandbox projects;
- build the workspace; and
- write `.sandbox-jdt-migration-qa-pins.env` into the workspace.

After provisioning, compare the generated pin file with `pins.env`. The runner
performs the authoritative Git verification again before changing anything.

## 2. Build the Sandbox product under test

In the Oomph-provisioned Sandbox checkout, run the normal verified build. The
materialized Linux launcher is typically below
`sandbox_product/target/products/.../linux/gtk/.../eclipse/eclipse`.

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

Use the product from the exact Sandbox commit being assessed. Do not substitute
an older installed Sandbox feature.

## 3. Close the provisioned IDE

The cleanup application opens the same workspace in order to use the project
model, target platform and source relationships prepared by Oomph. Eclipse must
therefore be closed before running the scenario; otherwise the workspace lock
correctly prevents the QA run.

## 4. Run baseline, migration and identical post-test

```bash
bash qa/upstream-jdt/run-before-after.sh \
  --jdt-core /path/to/eclipse.jdt.core \
  --jdt-ui /path/to/eclipse.jdt.ui \
  --jdt-core-binaries /path/to/eclipse.jdt.core.binaries \
  --workspace /path/to/sandbox-jdt-migration-qa-ws \
  --sandbox-eclipse /path/to/sandbox-product/eclipse \
  --output /path/to/evidence
```

The runner performs these fail-closed steps:

1. verifies all repositories, refs and commits;
2. applies and locally commits the versioned Jupiter build overlay;
3. runs the pinned `org.eclipse.jdt.apt.tests` Maven test command;
4. saves every Surefire XML report as the baseline inventory;
5. runs Sandbox in `check` mode and requires exit code `2` plus a non-empty patch;
6. verifies that check mode restored the checkout;
7. applies the same cleanup profile;
8. requires at least one Java source change and a clean Git diff;
9. runs exactly the same Maven test command again;
10. compares test identities, multiplicity and passed/skipped/failure/error state;
11. writes provenance, cleanup reports, logs and the migration-only patch; and
12. resets the JDT Core checkout to the original pinned commit.

A green Maven process is not enough. The run fails when a test silently
disappears, is newly skipped, changes state, or appears unexpectedly. Intentional
identity changes must be recorded explicitly in `expected-test-mapping.json` and
reviewed like source code.

Use `--keep-changes` only when the migrated workspace is needed immediately for
SWTBot documentation capture. In that mode the local overlay commit and the
uncommitted migration diff remain in the JDT Core checkout and must be reset
manually afterwards.

## Evidence layout

The output directory contains at least:

```text
baseline/                         original JUnit XML reports
migrated/                         post-migration JUnit XML reports
cleanup-check-report.json         read-only cleanup evidence
cleanup-check.patch               patch predicted by check mode
cleanup-apply-report.json         applied cleanup evidence
migration.patch                   migration diff excluding the build overlay
changed-files.txt                 changed paths
test-inventory-comparison.json    before/after discovery and state comparison
provenance.json                   pins and SHA-256 artifact digests
logs/                             Maven and cleanup stdout/stderr
run-state.txt                     last completed phase or PASS/FAIL
```

These files are the provenance source for documentation screenshots. A Help
image based on this scenario should identify the repository, tag, commit,
project and successful before/after evidence rather than presenting an
untraceable synthetic example.

## Identical build overlay

`overlays/jdt-core-r4_40-jupiter.patch` adds only the Jupiter API packages needed
to compile migrated sources. It is applied **before both test runs**. The
baseline therefore differs from upstream only by the same dependency
availability that the migrated run receives; the source migration is the only
between-run change.

The overlay does not claim that Sandbox automatically rewrites PDE manifests or
Maven metadata. Resource/dependency migration remains a separate, explicit
project policy.

## Contract validation and manual CI mirror

Run the lightweight validation without provisioning JDT:

```bash
python3 qa/upstream-jdt/verify_contract.py
```

It checks XML well-formedness, agreement between Oomph and `pins.env`, narrow
cleanup options, shell syntax, the strict comparator and its negative case.

The `Upstream JDT migration QA` workflow runs this validation for changes to the
QA definition. Its manually dispatched baseline job checks out the same refs,
verifies the exact commit IDs, validates that the overlay still applies and runs
the official upstream baseline test project. The complete before/after scenario
uses the Oomph-provisioned workspace because that workspace is part of the
semantic migration input, not incidental CI state.

## Extending the corpus

The next scenario should retain the same model and add JDT UI's
`org.eclipse.jdt.ui.tests` for JUnit 4 rules, runners, suites and mixed
JUnit/Jupiter projects. Add a separate profile, overlay, expected inventory and
provenance entry rather than broadening the first JUnit 3 scenario implicitly.
