# Independent retention review — 9c2d1216

## APPROVED for the completed current-source retention gate

No concrete remaining finding was found in the fresh execution, source binding, retained regions, replacement changes, costs or proof/provenance changes.

- HEAD: `9c2d121686dd2bfcd774d820312ca603d8fc4b4f`
- Tree: `d9736c8cd1e397508784e7d79fd1cb08ac978312`
- Checkout: `/tmp/sandbox-bls-on-native`
- Evidence: `/workspace/scratch/0eb32b15fbb4/recovery/current-retention-9c2d1216`
- Reviewer: `/root/review_constant_residue`, 2026-11-02

This review started no JVM, Maven or tests and changed no source, auditor, profile, assertion or existing receipt. Large difference reads were completed after Root confirmed the JMH process had ended. The only written artifact for this review is this receipt.

## 1. Fresh execution and exact source lease

The actual wrapper receipts record start **18:30:18 UTC**, finish **18:30:30 UTC**, Maven **BUILD SUCCESS**, **10.929 seconds**, Maven exit **0**, wrapper exit **0**, and `source-current=true`. Before/after HEAD and tree equal the identities above; both tracked-status files are empty. A final independent Git read also found the same clean checkout.

All three copied XML files are byte-identical to their originals and newer than the start marker `18:30:18.420 UTC`. No reports were excluded. I counted their actual suite headers and test-case elements:

| Suite | Tests | Failures | Errors | Skips | XML time |
| --- | ---: | ---: | ---: | ---: | ---: |
| RetentionAuditTest | 13 | 0 | 0 | 0 | 0.516 s |
| BaselineIntegrityTest | 1 | 0 | 0 | 0 | 1.908 s |
| CurrentRetentionTest | 2 | 0 | 0 | 0 | 5.531 s |
| **Total** | **16** | **0** | **0** | **0** | |

Every XML records the correct current checkout, expected full HEAD/tree and JDK `25.0.5+8-LTS`. The original report times range from 18:30:23.024 to 18:30:30.468 UTC.

The unchanged wrapper has SHA-256 `bd7e8a98c8c5e187e3a3c93e522da6c3b3d4951aa67f71ccb36cf8a7c2f7b34f`. The run log has SHA-256 `51bfb7dc2f628332927dc22e945c4be3c95473923e31c678dc127483b6ddebb6`.

## 2. Region and input integrity

I read the auditor and its 13 negative/positive controls, the baseline integrity test and the two active current-profile tests. The executed authority checks pinned baseline ZIP integrity, archive/source hashes, complete source selection, actual source-context bytes, runtime hashes and loaded origins, profile semantics/budgets, original source spans, unique candidate mapping, generated/review-only files and ordinary Apply reconstruction. It verifies Git and runtime inputs again before writing the current receipt.

Independently reconstructed indices from the actual copied baseline/current reports agree with the retention IDs. No baseline ID or source hash is missing or changed. The archived report copies hash exactly to their actual baseline and current input reports, and the summaries agree with the complete difference records.

| Profile | Baseline | Current | Retained | Lost | Added | Retained-region cost changes |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Strict | 146 | 146 | **146** | **0** | 0 | **0** |
| Mathematical | 149 | 153 | **149** | **0** | 4 | **0** |

Both profiles retain the complete **3,136-entry selection**, **3,126 parsed files**, and the same **10 parse-error files and diagnostics**. The fresh current tests verify the pinned ZIP `a37d6bf5ab2962184b051c806d6a7382e040816d0c4224547f127f8aa1f7e1ed` and the BC 1.85.2 source/binary hashes.

The current report hashes are exactly those from the completed **retry1** full validation:

- Strict: `30374a02c860ea85d9c980c45fec0abddea2ed9d56a39ff959267be52f10cb57`
- Mathematical: `4a556c3dfda1503cec277126172a5e661261f37358cfdbb6f16e355d69d323ee`

The actual SDK is the same private SDK70 JAR, SHA-256 `ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`. The full review independently checked all 475 runtime file hashes immediately before this retention run. The executed retention test also rechecks them and the current class origins.

## 3. All 31 replacement changes per profile

The complete current before/after replacement pairs, including their region IDs, are identical to the historical 7fcd difference pairs. I used that history only for comparison; the execution and source evidence above are fresh and bound to 9c2d1216.

I also checked the current code portions independently:

| Profile | Consistent temporary-name changes | Reuse of declared local outputs | Total |
| --- | ---: | ---: | ---: |
| Strict | 21 | 10 | 31 |
| Mathematical | 22 | 9 | 31 |

The changes are not all mere renaming. For the 10/9 local-reuse cases, a small read-only in-memory parser expanded earlier `int` declarations into later output expressions while preserving operator precedence, parentheses and output types. **Every named expanded output is structurally equal before and after.** This is a static cross-check, not an additional Java execution.

| Region, relative to org/bouncycastle/ | Outputs checked | Profiles with binding reuse |
| --- | --- | --- |
| pqc/crypto/hawk/HawkEngine.java:50036:196 | gdStart, fsStart, gsStart, t1Start, t2Start | Both |
| pqc/crypto/hawk/HawkEngine.java:51036:55 | idx1, idx2 | Strict |
| pqc/crypto/hawk/HawkEngine.java:82149:83 | Fd, Gd, fgt | Both |
| pqc/crypto/hawk/HawkEngine.java:83332:112 | Ft, Gt, ft, gt | Both |
| pqc/crypto/hawk/HawkKeyPairGenerator.java:18153:142 | tq00, tq01, tq11 | Both |
| pqc/crypto/hawk/HawkSigner.java:67225:275 | t1Offset, h0Offset, h1Offset, xxOffset | Both |
| pqc/crypto/sqisign/Hnf.java:3283:37 | j, k | Both |
| pqc/crypto/xmss/XMSSMTPrivateKeyParameters.java:8695:228 | secretKeySize, secretKeyPRFSize, publicSeedSize, totalSize | Both |
| pqc/crypto/xmss/XMSSPrivateKeyParameters.java:11431:228 | secretKeySize, secretKeyPRFSize, publicSeedSize, totalSize | Both |
| pqc/math/ntru/Polynomial.java:10340:643 | m, hi, saOff, sbOff, z0Off, z2Off, z1Off, next | Both |

The current generic emitter allows only unique declarations that do not collide with input names; return values and array stores are excluded. Its first-consumer and prelude-dependency checks prevent forward use. The current emitter, source-context implementation and constant/residue adapters are byte-identical in Git to their 7fcd counterparts.

## 4. The actual current proof hash is explained and recomputed

The only top-level proof change is at:

`org/bouncycastle/crypto/bls/BLS12_381Serialization.java:4656:109`

Only `assumptionsHash` changes:

- Baseline: `6a5255e8e126c47eeed4743a705a154ed88ab557c8911aca41bff6e82222ebd8`
- Current: `c5beec0bbe8c59e31fbf74c089ebc51ddd9afd0e5f33ed5ed01196ca5edc78ae`

The five semantic assumptions, original text, replacement text, all other proof fields and costs are unchanged. I read the pinned [SDK70 EvidenceHashes.java](https://github.com/carstenartur/Regelsuche/blob/70c13fb580062b1fd653f35b81b2fb7ed1903c28/regelsuche-optimization-sdk/src/main/java/de/regelsuche/sdk/optimization/EvidenceHashes.java) and reproduced its canonical hashing from the actual evidence. It length-prefixes the UTF-8 fields kind, subject, parameter and provenance, sorts the resulting assumption strings, concatenates them and hashes them.

Replacing only this provenance prefix:

`/home/runner/work/sandbox/sandbox/qa/math-optimization/target/bc-mathematical/source-context/`

with the actual current prefix:

`/tmp/sandbox-bls-on-native/qa/math-optimization/target/bc-mathematical/source-context/`

reproduces the current hash exactly. Both the independently computed baseline and current hashes equal their recorded values; the path-only rewritten canonical assumptions equal the current canonical assumptions. Assumption order does not alter the hash.

The two actual current dependency files also match their receipt hashes:

| Dependency | SHA-256 |
| --- | --- |
| Fp2Element.java | `b59fd659daf0de62923b1f3be85dfe0329aa45fb43add8a28b6befa8e4517a85` |
| BLS12_381G1.java | `40622d99ac46febebb82af4cd9286bdf843f10a6fffaf11291e161102aedc9c1` |

The historical 7fcd hash `de0d8ff2...` is not reused as the current hash.

## 5. Four current additions

Each addition was checked against its actual current original file and evidence file: source hashes match, the exact source substring equals the evidence's original text, and the actual evidence equals the retention entry.

| Added region | Recorded numeric proof | Estimated score |
| --- | --- | --- |
| BLS12_381G2HashToCurve.java:6070:309 | SAME_MODULUS_POLYNOMIAL_NORMAL_FORM | 145778 → 134178 |
| Fp6Element.java:4092:19 | INTEGER_POLYNOMIAL_NORMAL_FORM; TYPED_OPERATION_CONGRUENCE; TYPED_STRUCTURAL_IDENTITY | 165387 → 146135 |
| Fp6Element.java:4147:21 | Same three proof methods | 165387 → 146135 |
| Fp6Element.java:4745:19 | Same three proof methods | 165387 → 146135 |

All four original/replacement strings and complete canonical cost/proof field values match the historical additions. This does not claim byte equality of whole evidence files, which carry current provenance and schema metadata.

These remain the source-proved residue-factory case and the three constant-value Fp6 calls already covered by the unchanged generic adapters. The full current gate separately executed the required BLS cohort tests. The scores above are static estimates, not timing measurements.

## 6. Remaining evidence changes are accounted for

After explicitly comparing the new null fields `sourceNumericBranch`, `fixedPower` and `valueInverse`, normalizing only the two actual source-context prefixes, and comparing assumption sets independently of their serialization order, the remaining differences are:

- The same recomputed `assumptionsHash` at the two nested proof/evidence locations for the serialization region.
- A new, explicitly **null** `sourcePlan.valueRuntime` field for the retained Fp2 region. It does not assert an audited-runtime qualification.
- Recorded per-candidate `explanation.derivation.generationBudget` changes in **9 strict** and **114 mathematical** regions.

The changed current generation allowances are positive and within the unchanged configured 1,000,000-work limit: strict 26,701–227,407, mathematical 5,164–999,997. These are recorded allocation values, not changed operation-cost scores or increased profile budgets. No additional unexplained evidence field remains after those explicit comparisons.

The profile contract was not loosened. The existing auditor normalizes only absent default-false tail consent, absent UNSPECIFIED runtime mode and numeric-kind order; its negative controls reject changed budgets and newly enabled tail consent.

## 7. Receipts, hashes and qualification boundary

| Current artifact | SHA-256 |
| --- | --- |
| reports/bc-strict/summary.json | `9b4ee9cc45f52a2c56cb0e260237fa1f9c4e664f8d8b79d726b1f2de2a08e06f` |
| reports/bc-mathematical/summary.json | `4834a1204f4c2829c18e103272c81a4b2d56dcce53a9d3c2416a34ed7d2b7997` |
| reports/bc-strict/retention.json | `683fc02bc63c0de44dcfae551c633a8fce9aa1522319ee85ebe58be367b51ab3` |
| reports/bc-mathematical/retention.json | `a0e071e6407e0166c65127675f2815fe57d9e153da80da5287f049475bf18cec` |
| TEST-org.sandbox.math.retention.RetentionAuditTest.xml | `c921af15af1aed40e0edcaadc1bc4abcb378f2b04a584a2408d9da70e612aa54` |
| TEST-org.sandbox.math.retention.BaselineIntegrityTest.xml | `39860cad291ef96c564064c968290131bdd0179b399b9646a8ff922fb78a928f` |
| TEST-org.sandbox.math.retention.CurrentRetentionTest.xml | `7d6054f67100e1ff959b6a83d3b58a310cea194e71c4b9997d387b901b1c5e12` |

Report paths are relative to the current retention evidence directory above; XML files are in its `surefire-reports` subdirectory.

The matching full QA approval is `recovery/full-integration-9c2d1216-independent-review.md`, SHA-256 `9f236d3f2c4095b7df5faa994eaf1eb62fa19bb79675ada4a4d5f6f2efd797d2`. It qualifies **retry1**, whose process and wrapper exits are recorded. The first full attempt remains separately classified as Maven/JUnit/corpus complete with unproven process exit.

This receipt approves current-source retention and the reviewed differences. It makes no JMH, speedup, constant-time, public-CI, Help or release claim. No historical execution has been relabelled as a current one.

