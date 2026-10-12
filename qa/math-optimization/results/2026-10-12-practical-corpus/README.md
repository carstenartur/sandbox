# Practical Bouncy Castle and SWT mathematics qualification, 2026-10-12

These are new completed executions, not inferred totals from tests or merges.
The baseline is the actual CI-built adapter for PR #1700 (`3ce35553`), whose tree
matches Main `bae0eec7`. The comparison changes only the generic Java source
policy for flattened constant prefixes. SDK, scanner, dependencies, profiles
and budgets remain unchanged. The exact source blob, loaded classes, archive
hashes, run times and output hashes are bound by [qualification.json](qualification.json).

## Completed whole-archive results

| Corpus / mode | Parsed / errors | Before: input regions / accepted | Corrected: input regions / accepted | Corrected changed classes |
| --- | ---: | ---: | ---: | ---: |
| Bouncy Castle 1.85.2, strict | 3,126 / 10 | 2,707 / 103 | 2,706 / 101 | 48 |
| Bouncy Castle 1.85.2, mathematical | 3,126 / 10 | 3,049 / 114 | 3,048 / 112 | 54 |
| SWT GTK 3.135.0, strict integral | 473 / 0 | 375 / 7 | 374 / 6 | 4 |

All 101 corrected strict locations remain in mathematical mode. There are still
11 mathematical-only locations: ten modular-square cases and one BLS modular
reduction. In **both modes**, Hawk has 15 accepted replacements, including both
previously recovered complete fragments at offsets 106702 (304 characters) and
110134 (418 characters). The decreased counts are not another Hawk regression.

The complete `ImageData.java` yields **43 extracted regions and zero proposals**,
in both the native Eclipse test and the manual whole-archive scan. Its source is
unchanged. Of its optimizer outcomes, 27 report no improvement, eight an incomplete
search relation, six possible division exceptions and two no runtime improvement.
Extraction also encounters field/state writes, mutable array indices and output
boundaries. Increasing the budget alone is not established as a remedy.

Ten Bouncy Castle entries still cannot be parsed in the single Java-17
multi-release/module context; they remain visible errors, not analyzed successes.
The scan VM is source-bound Corretto 25.0.5-internal, a static CI build, not a
production benchmark JVM. Each scan completes sequentially with unchanged runtime
fingerprints. Original and generated files, actual evidence and completion markers
are retained outside the Git tree; large raw logs are not committed.

## Actual quality defect and its general correction

JDT represents `3 * 2 * numColors` as one n-ary infix node. The existing policy
protected nested constant expressions, but missed a constant left prefix within
that flattened node. The cleanup incorrectly advertised compiler folding as a
runtime improvement and obscured an intentional formula.

The nine-line policy addition protects that constant prefix without a project,
class, method or formula recognizer. It removes exactly these three proposals:

```java
// SWT TIFFDirectory -- retained, not rewritten to 6 * numColors
int numBytes = 3 * 2 * numColors;
// BC BF192 -- retained, not rewritten using 191
int o = xsOff + (192 - 1 - i) * LIMBS;
// BC BF256 -- retained, not rewritten using 255
int o = xsOff + (256 - 1 - i) * LIMBS;
```

Eleven new generic regression invocations include positive runtime controls.
The old actual plugin fails seven preservation cases; the fix passes all eleven.
A further test uses the three complete hash-pinned upstream files: it fails on
the old plugin and passes with the same minimal fix. These tests, the complete
ImageData test, the source-acquisition check and the twelve scanner tests pass
locally together: **26 tests, zero failures/errors/skips**. Native CI on the final
PR head remains a separate integration gate.

## Six surviving SWT proposals: usefulness is not automatic

- Two `JPEGFileFormat` index calculations factor out `DCTSIZE`, reducing eight
  source arithmetic operations to seven with no new guard or local binding.
- `PNGFileFormat` reuses `left - aboveLeft` inside the Paeth filter. This adds a
  temporary; the prepared value graph is unchanged, but duplicate source work is
  shared.
- Two `TIFFDirectory` proposals share `2 * numColors` or simplify an offset
  calculation. The latter removes the meaningful `lengthDirectory` variable and
  introduces 14; it is not automatically a readability improvement.
- `Shell` shares `2 * border` between width and height. Native GUI behavior and
  performance of this candidate have not been qualified.

The JPEG cases are the most compact new review material. None has a measured
speedup. The existing review heuristic labels all six compact; that label does
not judge meaningful names, JIT redundancy elimination or call-path frequency.
It must not be interpreted as six upstream-ready patches.

## Whole generated classes and real behavior

All complete original/generated changed classes compile: BC with `--release 17`
and SWT with `--release 21`, against their exact binary dependencies. Corrected
outputs cover 48 strict BC, 54 mathematical BC and four SWT classes.

The **actual generated BLS serialization class** passes differential execution
against its original class for **65 valid encodings and eight invalid encodings**,
including exact exception type and message. These are complete point-decompression
calls, not a hand-written substitute formula.

Direct execution of the actual original/generated **Java PNG, TIFF and legacy
JPEG codec classes** passes **340 comparisons**: 42 encoded images, their 42
decoded images, invalid truncated inputs, and 48 additional PNG images forced to
use Paeth filtering. Encoded bytes, decoded image state and colors match. The
forced-Paeth fixtures are also checked against their original RGB bytes and the
JDK ImageIO decoder. A deliberate `+1` mutation of the generated PNG intermediate
fails the differential test, showing that the probe reaches the modified logic.
Initial probe attempts exposed codec-specific scanline-padding preconditions;
final inputs use TIFF padding 1 and PNG/JPEG padding 4. Failed probe logs remain
retained, not counted as product failures or successful comparisons.

These checks deliberately enter `FileFormat` directly. On the inspected GTK
version, ordinary `ImageLoader` routes much image work through native loading;
the changed JPEG decoder also requires its existing legacy opt-in property.
Therefore these results do **not** certify faster ordinary GTK image loading,
native rendering, Shell behavior, whole-project behavior or constant-time
cryptographic execution. No JMH/allocation benchmark is claimed.

## Reproduction and evidence boundaries

Use the checked-in `ProjectMathScan` and explicit profiles documented in
[practical review](../../PRACTICAL_REVIEW.md). Run BC with source level 17 and SWT
with level 21. The exact Maven input hashes and six run receipts are in the JSON.
For the corrected local comparison, only the freshly compiled
`RuntimeCalculationPolicy` class family precedes the unchanged CI plugin on the
classpath; the scanner records and rechecks every loaded JAR/class hash. This is
an executed source-patch comparison, not a relabeled released plugin binary.

The native whole-ImageData result is retained in Maven run 38300886724,
artifact 11770822763 (source `e11c413e`): 342 mathematics tests pass. Its separate
Oomph workspace job reported a JDT indexing/import error; that failure is not
ignored or described as a wholly green workflow. Main's unchanged Oomph setup
passed independently. Final-head native integration must be checked separately.

Review is author self-review. Upstream BC/SWT sources have not been modified,
no external PR has been submitted, and broad speed claims remain unsupported.
