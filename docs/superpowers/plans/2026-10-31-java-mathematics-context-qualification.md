# Java mathematics context qualification — 2026-10-31

## Frozen scope and status

Baseline: `4f816e72ee3b369c4456ef1a894426529264edb0` (merged PR #1771).
Qualified local code: `f648240596f6d9f5451f236d4dc9e0770d142b85`, branch
`codex/math-java-language-support`. The prepared GitHub commit
`68c3c21bbc13bcd1781abdb855b81761d005f383` has the exact same tree as that tested
local code: `e3f9b0787c7be19ef7f7d4c66518ad8905dc4718`. Branch publication remains
pending; this prepared identity is not a published final-head CI result.
This report records local source/test/corpus
qualification; final whole-branch independent review and integrated-head
publication/CI are pending at this documentation checkpoint.

Delivered fragments and their individual admission boundaries are in
[Java computation contexts](../../../sandbox_math_cleanup/JAVA_CONTEXTS.md) and
[ordered inputs](../../../sandbox_math_cleanup/FIELD_ARRAY_INPUTS.md). Numerical
search/proof stays in Regelsuche, Java source/effects and separately replayed loop
scheduling stay in Sandbox, and ULR core stays JDT-independent. No SDK change,
source-name formula catalogue or CAS process was introduced. Expression-only
lambda/branch edits cannot introduce capture statements; loop-moved expressions
exclude reads, calls and unboxing. Coverage is not arbitrary composition.

Toolchain: Eclipse Adoptium Java **25.0.5+8**, Maven **3.9.16**, Tycho **5.0.4**,
Eclipse 2026-09 / Platform 4.41. The checked-in SDK SHA-256 is unchanged from
baseline: `5160ddecb4c4d4d521676d37c4dc639ae9b81f17fa37b6eaa3eead70f6a46efa`.

## Test and review evidence

Commands used Java 25 via `/workspace/scratch/9076dd571e02/toolchain/mvn-local`.
The full mathematics and expanded native runs used the frozen final code; scan
classes were copied from that successful QA `target/classes` into immutable
`evidence/final-classes` before scanning.

| Gate | Evidence | Result and limit |
|---|---|---|
| Baseline mathematics | `-f qa/math-optimization/pom.xml test` | 568 tests, 0 failures/errors, 1 existing optional skip. |
| Final mathematics | Same command; `/tmp/task5-final-full.log` | 714 tests, 0 failures/errors, 1 existing optional `BouncyCastleMathReferenceTest` skip; BUILD SUCCESS. |
| ULR core | `-f sandbox-functional-converter-core/pom.xml test`; `/tmp/task3-core-full.log` | 229 tests, 0 failures/errors/skips at `e9f5c08`; core code/tests unchanged since, no redundant final rerun claimed. |
| Final native package / SpotBugs | `-pl sandbox-functional-converter-core,sandbox_math_cleanup,sandbox_math_cleanup_test,sandbox_math_cleanup_help,sandbox_math_cleanup_feature -am -DskipTests package`; `/tmp/task5-final-native.log` | BUILD SUCCESS; analyzed modules 0 bugs / 0 errors. Native test/help/feature packages compiled; no workbench test execution. |
| Fresh repository policy | `-pl sandbox_common_test -am -Dtest=RepositoryPolicy*Test,PinnedGitRepositoryTest -Dsurefire.failIfNoSpecifiedTests=false -Drepository.policy.base=4f816e72ee3b369c4456ef1a894426529264edb0 -Drepository.policy.exception=<verbatim published PR1772 exception> package` | 12 tests, 0 failures/errors/skips, BUILD SUCCESS at documentation checkpoint `13638a03da02bf43f5c94f3537a629f59d01a961`; 42.270 s, bound SpotBugs 0; `evidence/final-policy.log`. The selector permits reactor modules with no selected tests; no failing selected test is ignored. |
| Task-scoped independent reviews | Tasks 1–3 approved; Task4 `118c43e` findings fixed in `ee58321` and rereviewed; Task5 `f648240` spec PASS / quality APPROVED | No pending task-scoped findings; final complete-branch review remains pending. |

Implementation commits: Task1 `d402504`, Task2 `ff50dd3`, Task3 `e9f5c08`,
Task4 `118c43e` + `ee58321`, Task5 `f648240`. Focused behavioral tests compile and
execute original/generated Java for overflow, null/bounds/throw precedence,
call counts, mutation, catch-visible locals/stores, deferred callbacks and
empty iterations; they check exact edits/Undo, stale dependencies, budgets,
cancellation, target bindings and deliberately tampered scheduling/cost evidence.

Task5's initial full run exposed changed Hawk primary windows and a budget
regression. The fix preserves the old first-pass grammar/windows and defers richer
reads after primary/retries, deferred assignments and stores. Source tasks/contexts
and stream/loop passes follow, all sharing the remaining allowance. Existing Hawk
minimum assertions were retained; the final focused run passed 85 tests. Task4's
no-candidate source-task budget refund can also enable later passes; it must be
considered when interpreting integrated corpus changes.

## Matched archive scans

The existing `ProjectMathScan` was invoked from the repository with no `--tasks`:

```sh
/workspace/scratch/9076dd571e02/toolchain/jdk-25.0.5+8/bin/java -Xmx512m \
  -cp "/workspace/scratch/fe317af5addb/evidence/final-classes:$(cat /workspace/scratch/fe317af5addb/evidence/math-classpath.txt)" \
  org.sandbox.math.qa.ProjectMathScan <binary.jar> <sources.jar> \
  qa/math-optimization/profiles/<profile>.properties \
  /workspace/scratch/fe317af5addb/evidence/final-<profile> \
  <sourceSHA256> <targetJava> [selectedEntry]
```

Baseline scans used baseline classes and `baseline-<profile>` output directories.
Inputs are under `/root/.m2/repository/`; the table gives their exact Maven-relative
binary/source paths. Profile files are under `qa/math-optimization/profiles/`.

| Profile | Binary and sources JAR paths below the Maven root | Target / selection |
|---|---|---|
| `bc-strict`, `bc-mathematical` | `org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2.jar`; `org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2-sources.jar` | Java 17; all entries |
| `swt-strict` | `org/eclipse/platform/org.eclipse.swt.gtk.linux.x86_64/3.135.0/org.eclipse.swt.gtk.linux.x86_64-3.135.0.jar`; `org/eclipse/platform/org.eclipse.swt.gtk.linux.x86_64/3.135.0/org.eclipse.swt.gtk.linux.x86_64-3.135.0-sources.jar` | Java 21; only `org/eclipse/swt/graphics/ImageData.java` |

The same sources, binaries, selected entries, target levels and normalized options
were used before/after. BC profiles enabled all eight numeric kinds
(`BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,BIG_INTEGER`); SWT enabled only the five
primitive integral kinds (`BYTE,SHORT,CHAR,INT,LONG`). All profiles used
`PRESERVE_JAVA`, `LOWER_ESTIMATED_RUNTIME`, 1,000,000 work units, 20,000 states,
5 seconds, `NONTRIVIAL` explanations, no exclusions, checked opt-in false and
underflow checks false. Only BC mathematical sets mathematical opt-in true.

| Profile | Parsed / parse-error files before and after | Extracted before -> after | Accepted before -> after | Exact prior spans retained | Added / removed |
|---|---|---|---|---|---|
| BC strict | 3,126 / 10 | 15,446 -> 17,007 | 120 -> 146 | 120 | 26 / 0 |
| BC mathematical | 3,126 / 10 | 15,787 -> 17,348 | 129 -> 147 | 129 | 18 / 0 |
| SWT strict ImageData | 1 / 0 | 169 -> 195 | 0 -> 0 | 0 | 0 / 0 |

The same ten BC multi-release parse-error files remain. Increased extraction is
source coverage, not acceptance or a proof of optimality. Every accepted scan
edit passes exact Apply/Undo and full generated-file reparsing. This is not a
whole-library compilation/runtime, installation or constant-time qualification.

### SHA-256 input and review identities

Hashes below identify the archive, exact profile bytes and complete `review.json`
receipts. Receipts also record runtime class/dependency hashes and normalized
options. Evidence directories are `evidence/{baseline,final}-<profile>` in the
qualification workspace; generated reports/binaries are not committed here.

**bc-strict**

- Sources: `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`
- Binary: `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`
- Profile: `38ae54f1720a83436e82937f0a12dfe63dbc427ca78e7c12c08df1c48ca3a538`
- Baseline review: `b3da3aebf9022b24d20af013ff5e6b1062ccb24582b30d6101fc3a682f9fb8b4`
- Final review: `d289f5ea8fd1165b508f2d96bc9aa6a7bcf46666a48c8ef1ec03755e2a581c90`

**bc-mathematical**

- Sources: `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`
- Binary: `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5`
- Profile: `5ad01f6c5617f1b4977527470dc657bd5d52aecae047fe2b3fe23f66198a757a`
- Baseline review: `3b676fa30fb9806c38fb9d72384fb475127d8c9ff3cfc33960e5e733bd721226`
- Final review: `34d55e36bea62aedc2114924fbfc1f96254b25d7f45e38e26455442750973a6d`

**swt-strict**

- Sources: `8f96c6e714b55ecd0868bfd42cfcd4eeb8a19c4bbee34d6bbb17cb6e593f7f19`
- Binary: `0799e4475b8937e05a0f13c6cbeadbf066ca56dd3622bfd8c7a4ba133b2db2e2`
- Profile: `5a709e01bbe3c5c5e18f49e244d0d47754c5cc0d5959c02f9db4150d5aedba74`
- Baseline review: `6c05219cacafcb6a9be631e4faa94ede4fe8784ad5463e5b2874c17195544d8e`
- Final review: `b789ad68332f3309cfccd1e67cb764a20e761ab4242dff47565e4d801e3222a8`

### Added original-source spans

Offsets and lengths below are original decoded-source character offsets, written
`offset:length`; no prior accepted span was removed. Paths share `org/bouncycastle/`.

| File below that prefix | Added spans | Profiles |
|---|---|---|
| `crypto/digests/RIPEMD128Digest.java` | `3705:47`, `4063:47`, `4408:47`, `4764:47` | strict and mathematical |
| `crypto/digests/RIPEMD256Digest.java` | `4211:47`, `4569:47`, `4914:47`, `5270:47` | strict and mathematical |
| `crypto/digests/SM3Digest.java` | `7647:90` | strict and mathematical |
| `crypto/engines/ISAPEngine.java` | `15143:27`, `15208:28`, `15274:27`, `15339:28`, `18765:27`, `18830:28`, `18896:27`, `18961:28` | strict and mathematical |
| `crypto/engines/SM4Engine.java` | `5315:55` | strict and mathematical |
| `crypto/generators/RSAKeyPairGenerator.java` | `6836:29`, `7020:29`, `7203:29`, `7329:29` | strict only |
| `math/ec/ECCurve.java` | `47291:29`, `47475:29`, `47658:29`, `47784:29` | strict only |

Eighteen added spans in each BC profile contain helper calls (RIPEMD128/256,
SM3, ISAP and SM4). The eight additional strict-only spans are conditional arms
in RSAKeyPairGenerator and ECCurve; the new expression-context path requires
mathematical opt-in off. This classification comes from recorded source/evidence,
not per-feature ablation. Budget/refund integration can contribute to availability.
All added spans have null scheduling evidence: none is reported as a loop-motion
gain. RIPEMD128 line 208 exposes nested `RL(a + f2(...) + ...)` arithmetic and the
SDK transforms the bitwise `f2` calculation, with estimated work 11 -> 10; SM4
line 87 records 10 -> 8. These are model units, not measured speedups.

## Remaining proof and integration boundaries

The new loop receipt independently replays source scheduling, exact trip controls,
input declarations/no-write facts and cost against the numerical trace. Numerical
proof covers one evaluation. Work charges a temporary store, every iteration's
read and retained primitive storage. Pinned array effects/enhanced-array loops
use normal-completion estimates because throwing effects can stop earlier.
No all-path saving or runtime speedup is established.

General recurrence closed forms, arbitrary virtual numerical summaries, heap/alias
analysis and BigDecimal scale/rounding require additional proof models. Floating
motion, arbitrary stream topology/conversion, expression-site captures and
retained-helper argument captures remain excluded. A configurable CAS belongs
behind Regelsuche's verified proposal boundary. Independent numerical equivalence
does not certify cryptographic constant-time behavior.

[PR #1772](https://github.com/carstenartur/sandbox/pull/1772) is an ordinary
main-based **draft/reference** PR with a substantive Repository policy exception
published before the aggregate size threshold was crossed. It is not a merge
candidate; split coherent reviewable main-based slices before merging.
Published Task3 commit `fbc99311bc7d1441d661fb9ee9c4dbda1f5849b3` had a tree
identical to local `e9f5c08`. Maven build passed; Oomph initially failed on a
JDT indexing/classpath transition and its exact failed-job rerun passed without
source/gate changes. Maven verification then succeeded in run `41186133665`,
attempt 2. That is Task3 checkpoint evidence, not final integrated-head CI.
Fresh repository policy passed at documentation checkpoint `13638a03`; these
subsequent wording/identity corrections do not change code or scan inputs. Final
whole-branch review and remote publication/check results remain pending; no
published final remote SHA is asserted.
