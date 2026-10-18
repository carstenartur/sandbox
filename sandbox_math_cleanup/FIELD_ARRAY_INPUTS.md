# Ordered field-array inputs

The source adapter admits one-dimensional primitive integral array receivers
from resolved local variables and parameters, and from nonvolatile fields used
without a qualifier, through unqualified `this`, or through a type qualifier.
A field need not be final: every occurrence rereads the complete original
expression. A receiver, alias, repeated index or field value is never assumed
stable merely because two pieces of source look equal.

This is an extension of the existing ordered source-capture contract, not an
additional mathematical optimizer. The original read expressions execute once
in their original order, including discarded values and class initialization.
Only total integral arithmetic may be scheduled after these captures. Field and
array writes, unsupported calls, produced index locals and throwing arithmetic
retain their existing boundaries. The independent proof, output verification,
capture costs, numerical profiles, cancellation and finite budgets are unchanged.

Object-qualified/nested/effectful field receivers, volatile array fields,
mutable field indices, index mutation, nested reads and throwing index arithmetic
are not admitted. Checked and fallback profiles retain their previous boundary.
Authored constant index formulas are copied as source, not replaced by literals.

## Tests and evidence

`PreservedArraySourceTest` retains the initial five regression requirements.
`FieldArrayOrderingTest` adds class-initialization ordering, repeated/dead reads,
receiver reassignment, observable writes, aliases and excluded qualifiers.

`FieldArrayCorpusTest` processes complete pinned Bouncy Castle 1.85.2 HC128,
CAST5 and Keccak classes using one million work units, twenty thousand states,
the existing five-second invocation limit, Java 8, PRESERVE_JAVA and the runtime
goal. It records hashes, normalized options, admitted field-read counts,
accepted changes, independent proof evidence, diagnostics, complete compilation
and byte-exact Undo under the existing test-report artifact directory.

HC128, CAST5 and Keccak supply admitted field-array reads. Keccak's only field-array
read, `a00 ^= KeccakRoundConstants[i]`, is an incoming compound write to a
loop-carried local; it is now admitted as an isolated region that captures the
old local value before the field-array read.

An admitted input is not an optimization hit. No accepted change is fabricated
when a class produces zero. These reports are not a cipher-wide execution test,
a constant-time qualification, an allocation benchmark or a measured speedup.
Inspect exact-head Maven/JUnit evidence before declaring this increment verified.
