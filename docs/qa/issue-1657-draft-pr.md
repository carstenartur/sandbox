# Issue #1657: mathematics cleanup integration draft

This is a local integration/reference branch for [#1657](https://github.com/carstenartur/sandbox/issues/1657), not a merge-ready change. It combines the Java 25 baseline with a locally embedded, independently checked Regelsuche optimization SDK and exactly four new Sandbox modules:

- `sandbox_math_cleanup`
- `sandbox_math_cleanup_feature`
- `sandbox_math_cleanup_help`
- `sandbox_math_cleanup_test`

The cleanup starts disabled. Its initial selection is BigInteger with preserved Java semantics. Type selection, safety profile, optimization goal and budget remain separate. Generated Java has no Regelsuche runtime dependency, and the target project's Java level is not raised.

## Repository policy exception

This integration draft necessarily exceeds the usual 1,500 changed-line review limit because the agreed issue couples a repository-wide Java 25 runtime migration, a versioned numerical SDK, a JDT adapter, four complete plug-in modules, source-level regression tests and native installation verification. Splitting these into an independently releasable partial implementation would leave unsupported combinations of runtime, evidence format, emitter and UI. Review the migration, SDK, extraction/emission, UI and qualification commits separately; this branch remains a draft/reference branch until the required native gates and independent numerical review are complete. No new Python test authority or workflow is introduced. Ordinary follow-up merge candidates must be split into bounded, independently validated slices.

## Reconstruction and evidence boundary

Automated workspace maintenance removed the unpublished working trees and their Git metadata on 2026-10-04. The current implementation is being reconstructed from the issue's exact repository bases, surviving source/JAR artifacts and complete historical tool output. Decompiled bytecode is a reconstruction aid, not a claim of recovered original source identity.

Sandbox base: `b382011536d0ceaefab5a31c5e7d6e61c7422fc3`.
Regelsuche base: `da1f33195339efe08380b47c36e83455c1e3815f`.

Historical test counts, SDK pins and performance measurements do not qualify this reconstructed source. Current qualification requires fresh reports bound to the current source, SDK and installed adapter. Lost raw measurements must be regenerated rather than fabricated. An external recovery archive retains the surviving historical material and its provenance.

## Required current qualification

| Area | Required evidence | Current state |
| --- | --- | --- |
| SDK | Semantic regression tests, independent candidate checks, public API baseline, deterministic distribution and external consumer | Reconstructing |
| JDT core | Binding/extraction negatives, promotion and source-trace preservation, emitted-candidate verification, compile/differential tests | Reconstructing |
| UI and headless | Options, cancellation, stale state, two-project isolation, real LTK preview/apply/exact undo, CLI explicit consent | Reconstructing |
| Java 25 | Runtime baseline contracts, target-Java independence, Java 21 rejection, CI/product/JustJ consistency | 26 fresh contract tests pass; native qualification pending |
| Oomph | Fresh catalog-based provisioning and update, actual workspace build, fresh attempt receipts | Pending |
| Distribution | Sequential full Maven/Tycho build, fresh p2 install and installed headless analysis/apply/idempotence | 121 fresh module tests pass; full build and native qualification pending |
| Corpus | Exact JGit pins, analysis-only Bouncy Castle scan, positive/negative local controls | Reconstructing |
| Performance | Original versus generated Java, multiple forks, raw JMH samples and allocations, measured uncertainty | Pending |
| Native platforms | Linux workbench and configured Windows/macOS native jobs | Pending |

The final report must distinguish complete gates from environmental failures. A skipped, empty or interrupted suite is not a pass. UI errors logged outside JUnit must be checked explicitly; old workspaces and old success receipts must not supply missing evidence.

Fresh distribution module verification used Java 25 and the ordinary Maven command
`mvn -B -f sandbox_distribution_verify/pom.xml test`: 121 tests, zero failures,
errors or skips; SpotBugs reports no remaining findings. These include the 29
reconstructed installed-verifier contract cases and seven additional workbench
receipt checks. This qualifies the verifier's contracts, not an installed product.
The Oomph harness's lightweight suite also passes, with its native scenario still
explicitly unexecuted; that opt-in skip is not counted as native acceptance.

## Publication

No branch has been pushed and no pull request or issue comment has been published. Automatic approval review rejected the earlier attempt to create a remote branch because publication had not been explicitly authorized. Finish the concrete local changes and qualification first, then obtain approval for both branch pushes and draft pull requests. Do not close the umbrella issue on the basis of a partial milestone.
