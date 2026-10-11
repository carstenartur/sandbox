# Candidate quality, mathematical contracts and a real BigInteger hit

Historical corpus qualification on Java 25.0.4.1, JDT 3.47.0 and Java 17 source/target.
Sandbox base: `dc01b417f8d0437ca31dbeee96cfff9073a990bf`.
SDK source for these corpus measurements: `73b81f1c60f13a5623af9351d7db7f64576858f1`
in Regelsuche. The current SDK and its receipts were subsequently updated as
described below; the historical corpus counts are not a new scan of that SDK.

## Modular integration qualification, 2026-10-11

The #1691 integration embeds source-bound SDK revision
`ae1c39ea50cff685fe843b0357c818f38db6ec79`, standalone JAR SHA-256
`20f6a81c83a894c595f5447c72bebfc4e83c0503f16285b22d45f506af5e4e2b`.
The repository receipts `sandbox_math_cleanup/lib/qualification.json` and
`reproducibility.json` record two clean SDK reactor builds (2,359 tests each,
including 120 SDK tests and 11 modular tests), two fresh standalone consumers,
and byte identity of all ten distribution files. This development distribution
is distinct from the e3e795e SDK used for the historical #1690 qualification below.

Completed full-source scans of the same Bouncy Castle 1.85.2 Maven source archive
produce **104 strict Java replacements** and **114 mathematical replacements**,
with 2,204 and 2,544 analyzed regions respectively (50 and 55 changed source
classes). All 104 strict regions remain in the mathematical results. Both runs
report 3,126 parsed
source files and ten parse errors among the 3,136 archive entries. The ten
additional accepted BigInteger regions are distinct from the one-hit historical
scan below. These counts measure accepted regions, not ten additional classes,
whole-library behavioral equivalence, optimality, or measured runtime speedups.
Both scans use all numeric kinds, the runtime goal, Java 17 compliance,
1,000,000 work units per file and 20,000 states. Scan inputs and completed
strict/mathematical summaries are preserved beside this
README under `modular-integration-2026-10-11/`.

The first 180-test integration run had one failing test-only Javadoc counter:
without enabled doc-comment parsing, JDT retained source spans but omitted the
contract from `Javadoc.toString()`. The counter now reads those Javadoc source
spans from the actual generated text. It still requires exactly one method
contract and four region explanations; no assertion or production behavior was
removed. The isolated complete Bouncy Castle class test passed all five tests.
The corrected complete standalone mathematics/core suite passed all **180 tests**
with zero failures, errors or skips on Temurin 25.0.2 and Maven 3.9.16, completing
at 2026-10-11 03:04:17 UTC. Production/test source hashes remained unchanged
throughout this isolated run. Its source-bound receipt and full-log hash are preserved
beside the scan evidence; the full local log is retained in the isolated
qualification workspace. The installed-OSGi
`BouncyCastleRuntimeCorpusTest` is excluded by this standalone harness, as before.

The focused 22-module native Help reactor passed both existing
`MathematicsHelpScreenshotsSWTBotTest` tests on the current ae1c SDK and adapter,
completing at 2026-10-11 03:11:38 UTC. All five fixtures verified exact preview,
Apply, source compilation and byte-exact Undo. Fixture 05 uses the actual current
output: four NONTRIVIAL inline explanations and one Javadoc contract. The capture
verified the installed SDK hash and emitted each actual adapter fingerprint.
The independently recomputed compiled-class fingerprint is recorded in
`modular-integration-2026-10-11/native-qualification.json`.

At reactor exit, fixture 05's PNG/provenance had reverted to historical files;
the restoration cause remains unresolved. A bounded rerun of the same already
built UI harness in a fresh workspace passed both tests at 03:19:47 UTC. An
inotify observer independently archived the actual current 05 writes. Those
files remained current after this direct UI exit and are the selected 05 capture.
No passing whole-CI screenshot byte reproduction is inferred from either run.

For the first four images, the actual local render was byte-identical to the
initial local baseline and differed from the completed #1690 CI artifact only
by the known 1,172-pixel dialog-label variance (maximum RGB delta 140); the entire
source area was identical. Those four PNGs therefore retain the exact CI bytes.
Their new actual ae1c capture provenance explicitly distinguishes the current
source execution from the retained e3e795e CI raster and records the native PNG
hash separately. The fifth PNG is the new actual native capture. No raster data
or verifier was altered. Final full-CI screenshot byte reproduction, complete
Tycho/P2 and distribution installation gates remain separate.

## Historical review qualification, 2026-10-11 (#1690)

The historical #1690 review embedded SDK revision
`e3e795e009a35c88bcc7cdb4151408c147be0951`, merged upstream through Regelsuche #1084.
Its JAR SHA-256 was
`26f90e5493a68564a89fb04c740fcb2f744fa032246b97ebbaa75ff5d6752b36`.
At that review, the repository-owned qualification and reproducibility receipts
in `sandbox_math_cleanup/lib/` recorded two clean builds and two fresh consumers.
This revision binds the SDK generator change to revision v2 and uses the same
modular operation cost model through both public optimizer entry points.

The integration retains #1683's typed computation reports and #1689's selected
search-path explanations alongside #1690's mathematical contracts. All 171
standalone mathematics/core tests pass on Temurin 25.0.2, including the
complete Bouncy Castle class execution test. A new budget regression compares
later-method analysis with an isolated analysis using the measured remaining
file budget. It covers both exhausted and sufficient remaining budgets, without
assuming a fixed search-order-dependent admission threshold. Failed attempts
consume their own allocation or greater reported work; a reserved, unused
mathematical retry is not charged.

The normalized profile has eleven keys, including mathematical opt-in set to
false and source explanations set to NONTRIVIAL by default. Report, initializer
and native workbench assertions check both option families explicitly. Legacy
nine-argument callers and both ten-argument constructors keep their existing
contracts.

The focused native Eclipse Help reactor also passed both SWTBot tests: the
profile test verified all eleven effective options, and the capture test
recaptured all four real Bouncy Castle previews, verified the exact replacements, applied
them and undid them byte-for-byte. At that review, the image/provenance pairs
identified this SDK and its compiled adapter. The separate full CI and distribution gates
remain authoritative for merge qualification.

The four image/provenance pairs were subsequently copied byte-for-byte from
the completed native CI verifier artifact `generated-help-screenshots-1351-38106062712`
(artifact ID `11689872638`, source head
`7e38cf55c6c64f067dbeaa3f7556397e048224c1`). The archive SHA-256 is
`754bcb64d9bb5c7478ed298d901ac45fe1784c0cd6ce213f3d57c16f9160ef6f`.
The native scenarios passed, but the final baseline comparison found differences
in 1,172 dialog-label pixels per image. The source comparison area is pixel-identical;
the corresponding provenance differs only in `screenshotSha256`. No source,
profile, SDK, preview or Undo evidence changed, and the verifier remains unchanged.
The next CI run must reproduce these committed artifact bytes.

## Causes and changes

* The runtime goal accepted candidates whose SDK assessment explicitly reported
  no runtime improvement. Storage/order-only proposals are now rejected.
  Equal prepared-DAG operation work does **not** by itself mean no improvement:
  sharing repeated source evaluations can still lower the source-trace cost.
* Strict Java semantics excluded escaping BigInteger results and receivers with
  unknown exact type or magnitude. The separately selected mathematical mode
  permits result identity changes, checks unknown receivers and keeps the exact
  original expression as a fallback. This does not weaken the default profile.
* Guard costs could erase an apparent benefit. Both the runtime admission check
  and final assessment now include them. A BIP340Signer-shaped reorder with four
  receiver guards is correctly rejected, including under the allocation goal.
* The SDK did not propose a multiplication chain for a small literal modular
  power, and priced `modPow(2)` below a modular multiply. The modular domain now
  proposes bounded binary chains for exponents 2–16, using its existing independent
  normal-form proof. The cost model includes a fixed setup allowance; it remains
  a heuristic, not a timing guarantee.
* Preparation could stop before search on large straight-line regions. Smaller
  regions are now retried within the same remaining work budget. Actual
  `GF256AES.mul` reaches search after this change, but still has no accepted
  improvement. This is improved coverage/diagnostics, not a claimed rewrite.
* Signed literals such as `-2L` were incorrectly classified as intentional
  computed constant subformulas. They are now allowed as operands.

## Complete source scan

Input: Maven Central `org.bouncycastle:bcprov-jdk18on:1.85.2:sources`.
Archive SHA-256: `b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f`.
All 3,136 Java entries were inspected; 3,126 parsed without errors. Ten
multi-release/module-context entries remain outside this single Java 17 setup.
Each analysis used the actual production adapter, resolved bindings against the
matching binary JAR, all numeric kinds, the runtime goal, 1,000,000 work units
per file and 20,000 states. This is an exploratory corpus scan, not an optimality
proof or a whole-library behavioral qualification.

| Configuration | Accepted computation replacements | Changed complete classes |
| --- | ---: | ---: |
| Original strict adapter and pinned SDK | 190 | 69 |
| Updated strict Java profile | 104 | 50 |
| Updated profile with explicit mathematical opt-in | 105 | 51 |

The updated mathematical scan retains all 104 strict proposals and adds one
BigInteger proposal. The strict scan retains 49 reductions in estimated DAG
operation work and 55 source-sharing improvements with unchanged DAG work.
All accepted regions have independent SDK evidence, reparse successfully and
support byte-exact Undo. All changed complete classes in both updated profiles
compile with `javac --release 17` against the original binary dependency.
The nine established digest rewrites remain present.

## Real BigInteger example

`org/bouncycastle/crypto/hash2curve/impl/SimplifiedShallueVanDeWoestijneMapToCurve.java`,
method `process`, original line 56. Source-entry SHA-256:
`6cd8fbd459615e6936bd8eeea7413ea6a85dc91e6dd558de7d6c118c4d523119`.

```java
// Original
BigInteger tv1 = u.modPow(BigInteger.valueOf(2), p);
// Optimized branch
tv1 = u.multiply(u).mod(p);
```

The complete emitted replacement checks both `p` and `u` for non-null, exact
BigInteger class, `bitLength() < 4096` and positive sign. If any check fails, it
executes the unchanged original expression. Estimated operation work is 36 → 10;
14 guard-work units are included in the weighted score 371 → 249.
Method Javadoc records value semantics, identity permission and fallback scope.

`BouncyCastleBigIntegerTest` reads the unchanged complete source entry, verifies
the production result and Undo, checks second-pass stability, compiles both
whole classes and compares uncompressed P-256 curve points for 100 deterministic
inputs. Inputs include zero, one, negative values and a 5,001-bit value. Both
fixtures use isolated BC class namespaces; the installed signed JAR is unchanged.

Other powers in that method remain unchanged. For example, incomplete proof
relations and multi-statement output boundaries still limit coverage. The
negative regression for `x.mod(m).mod(m)` also remains: no candidate is invented
when the SDK has no supported rewrite.

## Generated range documentation

For the user's `int` example `2*x/2 → x`, generated method Javadoc states:

* Local exact no-overflow range before: `[-1073741824, 1073741823]`.
* Local exact no-overflow range after: `[-2147483648, 2147483647]`.
* Java overflow behavior may deliberately differ outside the old range.
* These are local calculation ranges, not whole-method preconditions.

The original Java expression is defined outside the old interval too, but wraps;
this is a changed mathematical contract, not an equivalence-preserving Java
rewrite. The option is off by default and Select All does not grant consent.
Ordinary Java search runs first, with a bounded mathematical retry when needed.
The SDK supplies and independently verifies the candidate. A separate exact
affine checker then proves equal mathematical functions and derives both ranges.
It supports one integral input and exact constant division, not arbitrary
nonlinear or floating-point domains. BigInteger gains no invented range extension.

Tests execute both sides at and outside the int boundaries, check signed long
bounds, null/subclass/large-value BigInteger fallback, existing Javadoc (including
disabled doc-comment parsing), Undo and second-pass stability.

## Validation and limits

* 124 plugin/core JUnit tests pass through Maven on the updated embedded SDK.
  The local standalone harness excludes only `BouncyCastleRuntimeCorpusTest`:
  its two existing tests require an installed, checksum-pinned OSGi source bundle.
  That requirement was not weakened. The new complete-class test accepts an
  explicitly configured source archive and verifies the actual entry hash.
* All 30 plugin production sources, including the UI, compile against JDT UI 3.39.0.
  No fresh full Tycho/P2, SWT or release-installation gate is claimed.
* Each of two clean SDK builds passed all 92 SDK tests plus five modular-domain
  tests. Ten distribution files are byte-identical, including qualification,
  sources and Javadoc; two fresh consumers passed.
* A local exploratory timing probe compared the actual guarded square with
  `modPow(2)`: 31 seeded rotating inputs, six warmup batches per variant, nine
  alternating measured batches of 10,000 calls, volatile sink and median times.
  Original/guarded median ratios were 2.30 (128 bits), 2.03 (256), 1.42 (512),
  and 1.48 (2,048). These are one-process measurements on this machine, not JMH,
  whole-map throughput, a constant-time assessment or a portable speed guarantee.

Cryptographic timing properties and complete-library behavior remain separate
qualification tasks. Fewer accepted candidates here primarily mean less noise;
the additional BigInteger candidate is backed by actual source and execution.
