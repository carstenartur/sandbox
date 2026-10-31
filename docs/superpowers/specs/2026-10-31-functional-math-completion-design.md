# Functional conversion and finite mathematics completion

## Intent and recovered state

Complete the requested finite-series integration, repair the existing Functional
Converter defects reported during qualification, and offer an explicit combined
cleanup with one preview and Undo. This continues the approved finite-series
work; it does not expand the arithmetic contract to arbitrary nonlinear,
floating-point or infinite series.

Starting integration: Sandbox `04955e7cd00bc83525ecd757ec43f1f95720f249`,
Regelsuche `3a9250062f3f5ddb2ce46d8c1c3650801a77b4ef`. The Java numeric foundation
and documentation are on Sandbox main `7ecd5e30`; the constant finite-sum SDK
foundation is on Regelsuche main `101e504a`.

## Confirmed defect and investigation boundary

TraditionalForHandler emits an exclusive `(end)+1` for inclusive int bounds.
At Integer.MAX_VALUE this changes a loop whose first body call throws into an
empty stream. Replacing range with rangeClosed alone is insufficient because
the original counter may wrap and continue. Bound evaluation frequency and
mutable bounds need executable counterexamples and conservative admission.
The older unused-parameter String.valueOf/toString concern must be checked
against current source; do not describe an already removed behavior as unfixed.

## Approach and ownership

Use conservative binding-aware counted-loop admission in the converter and an
optional coordinator in the mathematics layer. Numerical synthesis and proofs
remain in Regelsuche. The converter must run without the mathematics bundle or
any mathematical search. Shared ULR records remain JDT independent.

Merely ordering the existing independent cleanup registrations is insufficient:
JDT initially supplies the same AST, defers conflicting edits, and mathematics
correctly rejects stale source. Tight coupling of the converter to the SDK is
unnecessary and would impose arithmetic search on ordinary style changes.

## Combined cleanup contract

- New opt-in key: `cleanup.loop_conversion.with_mathematics`, disabled by default.
- Explicit whole-file cleanup only. No implicit save-action or selection expansion.
- First run the existing mathematical passes against the original snapshot.
- A second mathematics pass is allowed only for a newly exposed range/map/sum
  envelope from a verified first-pass fusion; at most two mathematics phases.
- Reparse and resolve bindings in private ICompilationUnit working copies after
  source changes; never perform a hidden edit on the primary/editor buffer.
- Run at most one converter phase using the selected existing target/source options.
- Protect every mathematically changed region, including generated guards and
  original-loop fallbacks, from later representation conversion.
- One total work allowance and deadline cover phases, admission, reparsing and
  rejected work. Cancellation leaves the user's buffer unchanged.
- Produce one atomic final diff with normal JDT preview, Apply and exact Undo.
- Preserve guards on edit leaves so native JDT copied/merged changes still reject
  stale compilation-unit source, compiler settings, classpath, source dependencies
  and effective options. The native FileBuffer/Primary-buffer connection must be
  tested with an EOF append through IDocument alone. A raw edit applied to an
  arbitrary unrelated document only has the additional fixed-range check; public
  TextEdit leaf hooks cannot establish that unrelated document's full length.
- Combined mode requires both features; missing optional converter support is
  diagnosed without suppressing ordinary converter behavior. A saved combination
  flag remains dormant while the mathematics master is disabled.

## Verification and usefulness

Tests must exercise the real JDT admission/rendering and compile/execute relevant
before/after sources. Cover maximum/inclusive bounds, effectful and mutable
conditions, safe literal/local bounds, unused object parameters, overlapping
mathematics/style opportunities, template protection, option/default isolation,
stale settings, cancellation and native preview/apply/undo.

Re-use Maven/JUnit and the existing JMH/corpus infrastructure. Measure actual
generated Java including guards and fallbacks over small and large ranges;
compile and compare outputs before timing. Record sources, SDK, JVM, raw samples
and uncertainty. A non-improvement is a result, not a reason to substitute an
idealized hand-written formula. Read current CI corpus artifacts before repeating
an expensive matched Bouncy Castle scan.

## Integration constraints

Java 25, Maven 3.9.16, Tycho 5.0.4, Eclipse 2026-09 / Platform 4.41.
No new workflow testing framework, global failure-ignore switch or screenshot
baseline accepted solely to make CI pass. Keep fixes on their appropriate branch,
ordinary PRs against main, no stacks. Large reference integrations must yield
reviewable independently tested main-based slices before merge. Existing green
CI proves only its recorded source revision; new code requires new evidence.

Routine implementation choices follow this contract. The execution request
authorizes this completion work; no new feature-choice confirmation is required.
