# Independent review: full integration at 9c2d1216

**Result: APPROVED for the completed full headless QA and corpus gate in retry1. No concrete source, test, provenance, or gate finding remains in the evidence reviewed below.**

Reviewed checkout: `/tmp/sandbox-bls-on-native`  
HEAD: `9c2d121686dd2bfcd774d820312ca603d8fc4b4f`  
Tree: `d9736c8cd1e397508784e7d79fd1cb08ac978312`  
Reviewer: `/root/review_constant_residue`  
Independent checks completed on 2026-11-02, after the recorded end at 18:29:49 UTC.

This review used read-only Git, filesystem, XML/JSON and SHA-256 checks. It did not start a JVM, Maven, tests, a capture or a benchmark, and did not modify repository sources or the evidence of either execution.

## 1. Completed execution and source binding

The qualifying execution is:

`/workspace/scratch/0eb32b15fbb4/recovery/full-integration-9c2d1216-retry1`

Its recorded start is **18:19:46 UTC** and recorded finish is **18:29:49 UTC**. Maven reports **BUILD SUCCESS**, total **10:01 min**, with its local-zone finish `2026-11-02T23:59:49+05:30`, which is the same UTC finish.

The actual `maven-exit-code.txt` and `exit-code.txt` both contain **0**. The automatic before/after identity files both contain the exact HEAD and tree above; both tracked-status files are empty, and `source-current.txt` is **true**. An additional independent Git read after completion confirmed the same HEAD/tree and a clean tracked checkout.

The copied command is byte-identical to the current wrapper and the first attempt's command. All three have SHA-256:

`edda7fea3d519d98a7358fe4b2ff0c887e3286ffdc2ad0b4f3ce63dfe92a3134`

The unchanged Maven operation was `clean verify -Pbc-corpus -Dsandbox.math.bc.upstream=true -Dsandbox.math.swt.current=true`, using JDK 25.0.5+8, Maven 3.9.16, the existing proxy settings and local dependency repository. The log records fresh compilation of 131 production source files and 131 test source files with release 25.

## 2. Complete fresh JUnit evidence

I counted all **130 XML suites** and all **1,144 test-case elements**, independently of Maven's aggregate line:

| Measure | Observed result |
| --- | ---: |
| Tests | 1,144 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 1 |
| XML suites | 130 |
| Copied reports excluded as stale | 0 |
| Copied/original XML byte mismatches | 0 |

Every original XML modification time is after the retry start marker. The observed original XML range is **18:20:14.374–18:25:34.463 UTC**, versus the marker at approximately **18:19:46.270 UTC**. Every copied XML is byte-identical to its original in this checkout, and the complete copied file set agrees with both the source report directory and `report-copy-origin.json`. Every suite records the two enabled upstream/SWT properties and runtime `25.0.5+8-LTS`.

The sole skip remains `BouncyCastleMathReferenceTest.exactPinnedTreesRemainUnchangedWithHonestUnsupportedDiagnostics`, with the actual reason:

`Assumption failed: Set math.qa.bcMirror to a verified JGit mirror`

It is separate from the enabled upstream identity gate, which ran successfully.

| Required or directly relevant suite | Tests | Failures/errors/skips | XML time |
| --- | ---: | --- | ---: |
| JavaTailRecursionRewriteTest | 21 | 0/0/0 | 2.134 s |
| BlsUpstreamSourceIdentityTest | 1 | 0/0/0 | 18.333 s |
| SwtCurrentSourceRefinementTest | 1 | 0/0/0 | 63.707 s |
| BlsSourceCohortTest | 12 | 0/0/0 | 17.326 s |

The digest of the complete sorted XML ledger is `9a2b9195178bc7823bca280b092394e2737f9bcd38143c4916d3ac556c9ace17`. The ledger input consists of one UTF-8 line per copied XML, sorted by filename, in the form `<sha256>  <filename>\n`.

## 3. Actual SDK and runtime inputs

The actual SDK JAR has SHA-256:

`ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1`

This matches the checked-in `qualification.json`: source revision `70c13fb580062b1fd653f35b81b2fb7ed1903c28`, version `0.5.2-issue1657-70c13fb58006`, Java release 25, `publiclyPublished=false`.

I independently hashed **all 475 file entries** in each corpus report's runtime inputs against the actual files; both lists match completely and refer to the same 475 distinct files. The loaded-class locations point to this checkout's SDK JAR and QA classes, plus the recorded JDT 3.47.0 dependency. The `loaded:` entries were treated as location metadata, not as file hashes.

The Bouncy Castle input hashes are:

| Input | SHA-256 |
| --- | --- |
| 1.85.2 source archive | `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f` |
| 1.85.2 binary archive | `986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5` |

## 4. Executed upstream, SWT and BLS gates

**Bouncy Castle upstream identity:** the active test compared the complete five BLS source classes against the pinned source archive and the actual upstream checkout. Its receipt records `https://github.com/bcgit/bc-java.git`, `refs/tags/r1rv85v2`, commit `854bd5c4286e1530132418906bf6175da7d9d83c`, and the five expected source hashes.

**Current SWT:** the active test and receipt cover the complete six classes below, plus the ImageData.getPixel selection. The source pin is `221d1de66c33292400c91cbd17e3121f72b04ea3` from `eclipse-platform/eclipse.platform.swt`. The test uses whole original/generated classes compiled for Java 21, isolated loaders, executable equivalence/exception/effect checks, exact Undo and dependency freshness checks.

| Whole SWT class | Accepted regions |
| --- | ---: |
| ImageData | 0 |
| StyledTextRenderer | 2 |
| JPEGDecoder | 0 |
| JPEGFileFormat | 2 |
| PNGFileFormat | 1 |
| TIFFDirectory | 1 |
| ImageData.getPixel selection | 1 replacement |

The SWT source and binary archive hashes remain `7bbb0b52c43152f2b8c1faeee458f1e24fb04f8a9d516c3409cd509e6415dcbc` and `6c2b69d1c2096d9915df958536aeb6a7efec8210901e3b54b322d8423dbf0119`.

**BLS cohort:** the 12 passing tests exercise the complete source/cohort, separate original/generated compilation and loaders, public calls and boundary/provenance/report controls. The recorded preparation contains **5 parsed classes, 0 parse errors and 9 accepted regions**, with target Java 17 and audited runtime `AUDITED_OPENJDK_25_0_5_8`. Tail elision remains false in this profile.

| BLS class | Accepted regions |
| --- | ---: |
| BLS12_381G2HashToCurve | 2 |
| BLS12_381Pairing | 1 |
| Fp12Element | 0 |
| Fp2Element | 1 |
| Fp6Element | 5 |

The JUnit export at `target/surefire-reports/practical-corpus/bls-source-cohort` contains its four metadata files. The complete compilation/execution is evidenced by the actual passing test and its source, not by a claim that this metadata export contains the temporary generated Java/class files. Persistent preparation and JMH measurement are separate subsequent operations.

## 5. Both full corpus scans

| Profile | Selected entries | Parsed files | Parse-error files | Accepted regions | Recorded finish UTC |
| --- | ---: | ---: | ---: | ---: | --- |
| bc-strict | 3,136 | 3,126 | 10 | 146 | 18:27:44.155790057 |
| bc-mathematical | 3,136 | 3,126 | 10 | 153 | 18:29:48.795439105 |

Both reports explicitly have status `COMPLETED_WITH_PARSE_ERRORS`. The ten parse-error files are not represented as zero parse errors. Both scans use target Java 17 and runtime 25.0.5+8-LTS. The existing 14-option profiles retain work budget 1,000,000, maxStates 20,000 and timeout 5, with `valueRuntime=UNSPECIFIED` and tail elision false. Mathematical opt-in is false for strict and true for mathematical.

These totals establish completed corpus execution. Exact baseline-region retention and unchanged per-region costs are the separate retention audit; this review does not infer them from the totals.

## 6. First attempt: preserved, but no invented process exit

The earlier execution is preserved at:

`/workspace/scratch/0eb32b15fbb4/recovery/full-integration-9c2d1216`

I read its immutable `preserved-target` snapshot and `recovery-observation.json`. Its log records BUILD SUCCESS at **18:17:48 UTC**, Maven total **10:17 min**, and complete 1,144/0/0/1 JUnit and 146/153 corpus results. The separately recorded observation at **18:19:07.109 UTC** found the expected clean HEAD/tree.

However, the Exec transport change lost the process connection, and the automatic process-exit and post-run wrapper files are absent. Its `processExitCode` is explicitly **null**. The snapshot's modification times are copy times and were not used as proof of original XML freshness.

Accordingly, the first attempt is classified **Maven/JUnit/corpus complete; process exit unproven**. It is not relabelled as a recorded exit-0 qualification. Root repeated the unchanged command once to obtain the missing contractual receipts, and retry1 supplies the complete qualification above. Neither this review nor the retry modifies the first attempt's evidence.

First-attempt log SHA-256: `d883ab5f757c1a2cd409667e4e86f0b6a47f71170720d84f27c9410e4eaebafb`.

## 7. Evidence hashes and scope

The following hashes were read directly from the completed retry's files:

| Artifact | SHA-256 |
| --- | --- |
| retry1/run.log | `9b793d71c78f6c41de1f2dd99eeb605e28825ce6034636d60e739b1c406c68aa` |
| target/bc-strict/review.json | `30374a02c860ea85d9c980c45fec0abddea2ed9d56a39ff959267be52f10cb57` |
| target/bc-mathematical/review.json | `4a556c3dfda1503cec277126172a5e661261f37358cfdbb6f16e355d69d323ee` |
| target/surefire-reports/practical-corpus/bls-source-identity.json | `1f03f472f94d4f42bd69a27ad1e6a3360673de5b965b329bb6a59b0ebff332fd` |
| target/swt-current-patch/receipt.json | `8b1ffc164528a93b5f00aae21f4e0d396e44a7a22c91d6322ce691dd43157a66` |
| target/surefire-reports/practical-corpus/bls-source-cohort/review.json | `30ef8ed75765dca2b610058673d00e98ac6a72d2634683e519f95b269e02543f` |
| target/surefire-reports/practical-corpus/bls-source-cohort/cohort.json | `bf33322844f10344f442d6bb019c0ec42ddba0aed99e13b45303dcf03cc733bb` |

Here `target` means `/tmp/sandbox-bls-on-native/qa/math-optimization/target`; retry1 means the completed evidence directory named above.

This approval is limited to the full headless QA/corpus execution on the exact 9c2d1216 tree. It does not transfer historical 7fcd results to this tree, make a new JMH or speed claim, or replace the following retention, measurement, native/public-CI, Help or release checks.

