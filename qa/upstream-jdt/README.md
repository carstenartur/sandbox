# Upstream JDT migration QA

This directory defines reproducible, evidence-producing migration scenarios on
real Eclipse JDT source code. They are deliberately separate from normal pull
request CI because provisioning the complete JDT development workspace and
executing the upstream tests would be too expensive.

The first scenario exercises Sandbox's **JUnit 3 to Jupiter** migration on the
`org.eclipse.jdt.apt.tests` project from Eclipse 4.40. The corpus contains real
`TestCase` classes, delegating test-name constructors, `suite()` aggregators,
lifecycle super calls and message-first assertions.

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

The Oomph project setup writes the complete 14-entry `PIN_*` map to
`.sandbox-jdt-migration-qa-pins.env` in the workspace. The runner parses that
file and requires exact equality with `qa/upstream-jdt/pins.env` before any
source is changed. This prevents a partially generated or stale Advanced-Mode
workspace from being accepted as release evidence.

## 1. Provision with Eclipse Installer Advanced Mode

Import:

```text
sandbox_oomph/jdt-migration-qa.configuration.setup
```

The configuration uses Eclipse SDK 4.40 and the accompanying project setup to:

- clone Sandbox;
- clone JDT Core, JDT UI and JDT Core test binaries at `R4_40`;
- resolve the official PDE/JDT target through Oomph Targlets;
- import the real JDT test projects;
- import the Sandbox projects;
- build the workspace; and
- record the complete pin contract in the workspace.

## 2. Build the Sandbox product under test

In the Oomph-provisioned Sandbox checkout, run the normal verified build:

```bash
mvn --batch-mode --no-transfer-progress clean verify
```

The materialized launcher is below
`sandbox_product/target/products/.../linux/gtk/.../eclipse/eclipse` on Linux.
Use the product from the exact Sandbox commit being assessed; do not substitute
an older installed feature.

## 3. Close the provisioned IDE

The cleanup application opens the same workspace in order to use the project
model, target platform and source relationships prepared by Oomph. Eclipse must
therefore be closed before running the scenario. The Eclipse resources layer is
the authority for detecting a genuinely locked workspace; the runner does not
treat the mere continued existence of a `.metadata/.lock` file as proof that an
IDE process is still active.

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

1. verifies every repository URL, ref and full commit ID;
2. verifies the complete Oomph workspace pin file;
3. applies and locally commits the identical Jupiter build overlay;
4. snapshots the named source examples;
5. runs the pinned `org.eclipse.jdt.apt.tests` Maven test command under Xvfb;
6. saves every Surefire XML report as the baseline inventory;
7. runs **one project-wide Cleanup refactoring** in `check` mode over every
   source compilation unit and requires exit code `2`;
8. requires a non-empty check report and patch, structured planning diagnostics,
   and byte-for-byte source restoration;
9. runs the same project-wide Cleanup refactoring in `apply` mode;
10. requires check and apply to report the same changed files and the same
    planning diagnostics;
11. verifies the concrete migrated contents of `FactoryPathTests.java` and
    `TestAll.java` according to `expected-corpus.json`;
12. records every remaining file that still contains a JUnit 3 execution shape;
13. runs exactly the same Maven test command again;
14. compares test identities, multiplicity and passed/skipped/failure/error
    state; and
15. writes provenance and restores the pinned JDT checkout.

A green Maven process alone is not enough. The run fails when a named difficult
case was not migrated, when check and apply disagree, when a test silently
disappears or is newly skipped, or when a result state changes. Intentional test
identity changes must be recorded explicitly in `expected-test-mapping.json`.

Use `--keep-changes` only when the migrated workspace is needed immediately for
SWTBot documentation capture. In that mode the local overlay commit and the
uncommitted migration diff remain in the JDT Core checkout and must be reset
manually afterwards.

## Named real-corpus acceptance cases

`expected-corpus.json` prevents a minimal unrelated rewrite from making the
scenario green. It currently requires these exact R4_40 files to change:

- `org.eclipse.jdt.apt.tests/src/org/eclipse/jdt/apt/tests/FactoryPathTests.java`
- `org.eclipse.jdt.apt.tests/src/org/eclipse/jdt/apt/tests/TestAll.java`

For `FactoryPathTests`, the result must remove the JUnit 3 superclass,
delegating name constructor, self `suite()` and redundant `super.setUp()` while
adding Jupiter lifecycle/test annotations and migrated assertions. For
`TestAll`, the result must remove the JUnit 3 superclass, delegating constructor
and suite method and replace them with the JUnit Platform suite annotations.

Constructors with user state, dynamic suites, decorators and other unproven
harness semantics remain fail-closed and are covered by negative Sandbox tests.

## Evidence layout

The output directory contains at least:

```text
baseline/                         original JUnit XML reports
migrated/                         post-migration JUnit XML reports
corpus/baseline/                  original named source examples
corpus/migrated/                  migrated named source examples
cleanup-check-report.json         read-only project-wide cleanup evidence
cleanup-check.patch               patch predicted by check mode
cleanup-apply-report.json         applied project-wide cleanup evidence
migration.patch                   migration diff excluding the build overlay
changed-files.txt                 changed paths
corpus-result.json                named examples, diagnostics and residual JUnit 3 inventory
test-inventory-comparison.json    before/after discovery and state comparison
provenance.json                   pins and SHA-256 artifact digests
logs/                             Maven and cleanup stdout/stderr
run-state.txt                     last completed phase or PASS/FAIL
```

These files are the provenance source for documentation screenshots. A Help
image based on this scenario must identify the repository, tag, commit, project
and successful before/after evidence rather than present an untraceable
synthetic example.

## Identical build overlay

`overlays/jdt-core-r4_40-jupiter.patch` adds only the Jupiter API packages needed
to compile migrated sources. It is applied **before both test runs** and every
file touched by the overlay is committed. The migration-only diff therefore
cannot accidentally contain a newly added overlay file.

The overlay does not claim that Sandbox automatically rewrites PDE manifests or
Maven metadata. Resource/dependency migration remains a separate, explicit
project policy.

## Contract validation and manual CI mirror

Run the inexpensive validation without provisioning JDT:

```bash
PYTHONDONTWRITEBYTECODE=1 python3 qa/upstream-jdt/verify_contract.py
```

It validates XML, the complete Oomph pin map, the project-wide application, the
cleanup profile, shell and Python syntax, the test-inventory comparator and the
named-corpus verifier. Both comparators include negative self-tests that must
reject deliberately incomplete evidence.

The `Upstream JDT migration QA` workflow runs this inexpensive validation on
normal pull requests. A manual dispatch can additionally choose:

- a baseline-only mirror; or
- a full clean-workspace mirror that builds Sandbox, runs project-wide check and
  apply, executes both upstream test runs and uploads the complete evidence
  directory even on failure.

The clean-workspace mirror is a second execution environment, not a substitute
for the Oomph Advanced-Mode release evidence. Before release, the same runner
must also pass against the actual provisioned workspace and its generated pin
file.

## JUnit 4 to Jupiter on pinned JDT UI

The second real-corpus scenario is already implemented separately for
`org.eclipse.jdt.ui.tests`. It deliberately does not broaden the JDT Core/JUnit 3
profile. Its executable contract consists of:

- `run-jdt-ui-before-after.sh`, with dedicated strict and best-effort modes;
- `junit4-to-jupiter.properties` and
  `junit4-to-jupiter-best-effort.properties`;
- `jdt-ui-junit4-corpus.json`, which names the required real source shapes;
- `JUnitXmlInventoryComparatorTest`, which is invoked by Maven to compare exact test identity, state, and multiplicity;
- `JdtUiCorpusContractTest` and `JdtUiCorpusEvidenceVerifierTest`, which validate the pinned contract and source/report evidence under Maven; and
- the `JDT UI JUnit 4 Strict Migration QA` workflow.

Validate the inexpensive contract with:

```bash
mvn --batch-mode --no-transfer-progress \
  -Dtest='JdtUiCorpus*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false -DfailIfNoTests=false \
  -pl sandbox_target,sandbox_common_test -am package
```

Run the strict scenario against the closed, pinned Oomph workspace with:

```bash
bash qa/upstream-jdt/run-jdt-ui-before-after.sh \
  --jdt-ui /path/to/eclipse.jdt.ui \
  --workspace /path/to/sandbox-jdt-migration-qa-ws \
  --sandbox-eclipse /path/to/sandbox-product/eclipse \
  --mode strict \
  --output /path/to/jdt-ui-evidence
```

The runner verifies the exact JDT UI repository, `R4_40` ref and commit before
reading source. It executes the same pinned Maven reactor before and after one
project-wide cleanup. The runner delegates comparison of the baseline and migrated
JUnit XML inventories to `JUnitXmlInventoryComparatorTest` through Maven, then
requires check/apply agreement, records the named corpus and emits provenance.
The strict GitHub workflow additionally builds the exact Sandbox product under
test and checks that the migration introduced no whitespace regression relative
to the pinned upstream baseline.

Corpus verification is delegated to the same Java verifier through
`run-jdt-ui-corpus-verifier.sh`, a thin Maven process adapter. It forwards every
input as a separate argument, preserves a failing Maven exit code, deletes an
old result before execution, and rejects a successful process that produced no
fresh non-empty result. The result remains `corpus-result.json`; the evidence
directory additionally records `corpus-verification-command.txt`,
`corpus-verification-maven-exit-code.txt`, and
`logs/corpus-verification-maven.log`.

The two former JDT UI Python validators have been removed. This does not mean
that all orchestration is Python-free: workspace-pin checks, source copying,
bytecode-view runtime inspection, provenance assembly and the separate JDT Core
track still contain legacy Python. The new JUnit fixtures exercise the actual
five-file contract synthetically; they do not replace the pinned before/after
execution or claim upstream execution evidence.

The current named contract requires coordinated migration evidence for:

- `JUnitSourceSetup.java` and its `ExternalResource` lifecycle;
- `LeakTestSetup.java` and its superclass lifecycle chaining;
- `FileAdapterTest.java` and its `@Rule` consumer;
- `SearchLeakTestWrapper.java` and its combined lifecycle/rule usage.

`ConvertLoopOperationTest.java` is an intentional negative boundary. Strict mode
must leave its unsupported Parameterized field-injection shape byte-for-byte
unchanged and report `PARAMETERIZED_FIELD_INJECTION`; best-effort mode must add
explicit remediation scaffolding rather than silently performing a partial
migration.

### Headless and interactive evidence are distinct

The pinned JDT UI runner proves the headless before/after migration contract.
The separate `Patched JDT UI atomic Help screenshot` workflow proves that the
Sandbox-patched Cleanup preview keeps coordinated JUnit and Int-to-Enum
candidates atomic and reproduces the committed Help images from deterministic
Workbench fixtures.

These two gates must not be described as one already unified upstream UI
scenario. Two boundaries tracked by #1469 and #1497 remain:

1. drive the interactive Cleanup preview from the same pinned JDT UI workspace
   and headless plan, verify candidate and affected-file agreement, and attach
   matching screenshot provenance;
2. move the remaining checkout identity, bytecode-view runtime and
   provenance assertions from the current shell/Python orchestration into
   reusable Java/JUnit fixtures executed by Maven/Tycho, leaving workflows to
   provision the environment and invoke the same Maven authority.

Until both boundaries pass on the integrated commit, Sandbox must not claim
that the overall JUnit migration or its documentation-driven real-corpus QA is
complete.
