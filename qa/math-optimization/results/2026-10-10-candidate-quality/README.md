# Candidate quality, mathematical contracts and a real BigInteger hit

Local qualification on Java 25.0.4.1, JDT 3.47.0 and Java 17 source/target.
Sandbox base: `dc01b417f8d0437ca31dbeee96cfff9073a990bf`.
SDK source: `73b81f1c60f13a5623af9351d7db7f64576858f1` in Regelsuche;
the standalone distribution receipts are in `sandbox_math_cleanup/lib/`.

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
