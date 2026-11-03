# Mathematics Java output quality

The upstream qualification found a constant-increment loop expanded into expensive
BigInteger bookkeeping, thirteen additional JDT compiler warnings, and no runtime
loop explanation comments. This change addresses those observations in Sandbox's
Java adapter. Regelsuche already supplies independently verified sum coefficients.

## Contracts

1. Lower checked constant polynomial sums on int induction variables to primitive
   Java arithmetic when Java-width evaluation, termination and observable counter
   state can be preserved. Int/long accumulators retain modular arithmetic. Keep
   the existing checked SDK plan, source binding replay and exact emitted replay.
   Long induction variables and nonconstant sums keep their established templates.
2. Prefer a compact path for ordinary exclusive unit-stride int loops, which cannot
   wrap before their bound; this path has no retained loop or crossover branch.
   Other admitted int headers retain a termination guard
   and original fallback. Evaluate source header expressions in their native width
   before widening. Never replace potentially nonterminating source execution with
   a returning closed form. Cost metadata describes the selected emitter honestly.
3. Generated Java must compile under the real project options and introduce no
   compiler warnings. Match retained baseline warnings by identity and source
   provenance, never by a global warning count. Keep existing errors fatal. A
   conservative rejection is preferable when provenance cannot be established.
4. Avoid unnecessary emitted casts only where the actual Java context supplies the
   same conversion. In particular, retain widening before arithmetic that could
   otherwise overflow. Emit signed-long BigInteger constants with valueOf; larger
   constants must remain exact and satisfy active NLS diagnostics.
5. Existing explanation modes also cover runtime iterations. Comments explain the
   checked identity, Java arithmetic contract and actual guards/fallback. Estimated
   costs are explicitly not measurements. NONE stays comment-free; no default or
   profile changes. Explanation text is bounded, cancellation-aware and replayed.
6. Applying cleanup again must not recursively transform retained generated
   fallbacks. Reproduce any defect first; recognize generated structure rather than
   treating a comment or variable-name prefix as proof authority.

## Validation and delivery

Meaningful JUnit tests compile and execute emitted source, cover extrema, live
counters, diagnostics provenance and tampered receipts. Existing headless math,
converter and normal Maven gates remain the authority. Re-apply the actual plugin
to pinned JDT sources; compile the generated classes and repeat the existing
MethodInfo JMH fixture against bound product JARs. Bytecode/allocation inspection
and benchmark evidence answer different questions and must be reported separately.

Work is isolated directly from main, with a reviewable PR and no merge implied.
Historical qualification evidence remains unchanged. Fresh test claims identify
their source revision and scope; a prior green build cannot qualify new code.
