# Staged support provisioning implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Ship the existing Java 21 staged JUnit support artifact through the product's real cleanup feature, with immutable ordered identity and an actual prior-support-to-current-support installation proof.

**Architecture:** Qualify the canonical Maven bundle before packaging, include it through the existing feature graph, and extend the current distribution verifier/native probe to compare artifact bytes, framework wiring, API identity and p2 profile state. Preserve existing deployment and rollback; add one bounded Java publication preflight.

**Spec:** `docs/superpowers/specs/2026-11-23-staged-support-provisioning-design.md`

**Base:** Actual main `8c5ec81095c6a268b0498a8516be5b4bc7e9f6a5`, after #1981 merged. No grouped-runtime API or unmerged branch dependency is permitted.

**Global Constraints:** Preserve the existing Java25 build/product/PDE and Java21 support contract and all existing APIs/gates. One canonical Maven JAR must remain byte-identical through p2 and installation. Prove actual JDT5 Jupiter/Platform class-space and API binding, not only import text. Preserve old-to-new support upgrade in one actual p2 destination/profile and director transaction. No source generator, consumer target mutation, automatic mixed-rule migration, screenshot changes, new Python or parallel test framework. Maven/JUnit is the semantic authority; shells only adapt process/environment inputs. Publishing qualifier is module-local; no global requalification. No publication path or expected-receipt substitution that evades existing candidate-based live-site verification/rollback. Current source identity, history, versions and hashes are checked before publication mutation. Existing ordinary main-based PR workflow applies. Target640–790 implementation/test lines plus portable spec/plan; report measured scope before1500 and stop before2000 without a substantive documented exception. Root handles remote publication and qualified merge.

### Task 1: Package, provision and verify the staged support bundle

**Files and seams:**
- `sandbox-junit-migration-support/pom.xml` and `maven-bundle.bnd`: bind the real Tycho build-qualifier goal for the ordinary Maven module; consume qualifiedVersion, module-local publishing property and dev fallback from the spec. Do not change public Java code.
- `sandbox_junit_cleanup_feature/pom.xml` and `feature.xml`: actual matching Maven dependency with the existing Jupiter/Platform exclusions and one direct support bundle member. Reuse the aggregate/product/update-site graph.
- `sandbox_distribution_verify` production helpers, `probe/AggregateProbe.java`, tests and minimal frozen historical test resources: canonical artifact/feature/IU inventory, native bundle and provider identity, exact digest and profile checks, prior-support fixture and one-transaction upgrade.
- `.github/workflows/deploy-snapshot.yml` and `deploy-release.yml`: thin explicit qualifier/output-timestamp inputs and bounded Java preflight using current publication evidence. Keep existing deployment/provenance/rollback and all verification. Add no new Python invocation or test logic in shell.
- Brief adjacent support/distribution documentation. The existing Maven Central aggregator does not publish support; do not claim Central availability from this slice.

- [ ] **Step 1: Baseline and concrete failure.**
  Inspect pinned existing APIs/feature/POM/native probe and repo policies. Record unchanged focused verifier tests. The exact current published baseline has76IUs and39plugin files with zero support IU/JAR (see spec); locally establish the missing feature member/API observation through a meaningful verifier assertion. A fresh product must not be reported before it exists. Reuse the current source and dependency cache, not a duplicate upstream checkout.

- [ ] **Step 2: Give the canonical Maven bundle a usable version and include it.**
  Use the already probed Tycho5.0.4 build-qualifier plus Bnd flow. For snapshot publication use the spec's padded first-parent-height and full-source-SHA qualifier; reject shallow/unexpected source inputs. Stable publishing uses zRelease. Normal local/PR builds use the specified UTC dev qualifier, which publication rejects. Pass outputTimestamp from the validated commit for publishing and prove repeated exact bytes at a fixed identity. Add the feature dependency/member and verify actual generated metadata contains one exact nonoptional support dependency and matching artifact. Keep all existing imports and service provider.

- [ ] **Step 3: Extend native installed evidence and the real upgrade fixture.**
  Reuse/refactor the existing temporary probe runner and stock director adapter. Observe the support bundle in fresh aggregate, materialized product and the same-bundle upgrade. Resolve it with FrameworkWiring; prove actual current wiring and defining classes against installed JDT5 runtime providers. Reflect/invoke the existing original and supplier APIs through those exact classes and verify the packaged SPI/filter. Verify installed real path in the selected installation's actual bundle pool, its digest, one active/current support bundle, singleton expected p2 version, and support not independently rooted. Preserve all previous aggregate/released/legacy checks.
  Freeze only the exact pre-supplier source/service entries from commit49e12331b056bdf8ae9c7a07d9dce9e0245ba1b1 with their spec blob pins and licenses. Compile/package the functional verification-only old bundle and publish a tiny old component to an owned local fixture repo. Establish old installed API absence/presence and digest first; then replace the component via one director transaction in the same destination/profile. Require a strictly newer OSGi support version, different old/new bytes, new API/wiring and canonical Maven→p2→installed hash equality. Label the old fixture as reconstructed historical implementation, not an unavailable released binary.

- [ ] **Step 4: Enforce publication ordering without replacing the deployment path.**
  Add one Java preflight invoked before existing Pages mutation. Verify candidate/prior metadata, source receipts and hashes. Initial introduction requires genuinely absent prior support; a broken/missing receipt when support exists fails. Same-source retries require identical support version+digest and non-regressing component/aggregate versions; equal feature versions require identical complete feature JARs, larger versions are allowed. New-source updates require first-parent descendant ancestry and strictly newer support plus both containing versions. Fail before mutation for collisions/regressions. Successful retries publish the current verified candidate, so the unchanged live-site verifier still compares against this build's verification.json. Preserve rollback and provenance. Explicitly test the spec's finite rejection table, including same-minute collisions and same-version different bytes.

- [ ] **Step 5: Run real qualification and document limits.**
  Run focused Java verifier tests and genuine standalone packaging/reproducibility checks; then the paired native PDE support tests and Linux sequential distribution verification with actual fresh/upgrade native launches. Capture actual commands, XML counts, all three artifact paths/hashes, before/after p2 profiles, API and provider records. Hosted Windows distribution is mandatory before merge; Linux source/compile proof is not a replacement. No blanket skip or analyzer suppression. If a target resolution, signing/repackaging or class-space mismatch appears, report its exact provider/path/hash and fix the underlying delivery path.

- [ ] **Step 6: Self-review, report and commit locally.**
  Keep the source-consumer target availability/binding/Preview/Apply/Undo stage and automatic mixed-rule rewrite explicitly open. Do not claim the four reference cases are solved. Measure the exact main-relative text slice, run diff whitespace checks, commit implementation/tests/docs, and write the requested report with exact source and artifacts. No remote push/PR/merge by the implementer. Root reviews, integrates then-current main and publishes one ordinary PR.

## Validation boundary

The preliminary stable-version plugin/Bnd probe only proves the chosen version producer, not the actual Sandbox artifact or native installation. The candidate support artifact and native class-space must be verified by this task. Any remaining full upstream refactoring baseline limitation is separate and cannot be hidden by this packaging gate.
