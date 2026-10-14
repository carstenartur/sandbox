# Mathematics Cleanup

This module participates in the explicit mathematics cleanup. Numerical proof and Java source emission are separate from the Eclipse UI. The four-module integration is rebuilt after workspace maintenance; current qualification is recorded separately from historical results.

## Ordered primitive array inputs

For `PRESERVE_JAVA`, including the explicit mathematical opt-in, a straight-line
region can connect arithmetic that reads one-dimensional `byte`, `short`, `char`,
`int`, or `long` arrays. Receivers must be resolved local variables or parameters.
Indices may use primitive integral locals, literals, casts, and total arithmetic;
index calls, fields, mutation, nested reads, division, and remainder stay outside
this admission.

Each source read gets a distinct scalar input and an ordered `ArrayRead` source
obligation. The source rewriter emits every read exactly once into a fresh final
local before the numerical replacement, including reads whose values become
dead. `inputNames` contains identifiers only. The SDK and the Java emitter may
schedule the captured values freely; they never schedule the memory accesses.
Repeated reads and aliases are not identified with one another.

Hoisting captures is limited to regions containing total primitive integral
operations. A statement whose read depends on a local already declared or
changed in the region starts a new region, keeping that local's preceding
evaluation in source. Potentially throwing arithmetic, array/field writes,
external local assignments, unsupported calls, and control-flow boundaries
separate regions. The original index text and the left-to-right order of reads
preserve null and bounds exception precedence. Existing loop-body extraction
remains local to each straight-line block; loop headers and writes are unchanged.

Checked and guarded-fallback profiles still treat array reads as boundaries.
Floating/reference/multidimensional arrays and helper-body memory accesses are
not admitted. There are at most 64 captured reads per region. This is Java
dependency admission, with no array formula catalogue, memory optimization, or
additional mathematical search engine. `ArrayReadComputationTest` exercises the
real extraction, proof, source-edit, compilation, execution, and Undo paths.

The source adapter charges one primitive work unit for each capture store and
each capture-name reference in the actual emitted arithmetic and output values.
It also adds all N capture values to the scalar plan's peak storage, a
conservative bound that may double-count existing input lifetimes. Original
array loads and index calculations execute once on both sides and provide no
claimed savings. The reported costs and runtime/allocation goal gates use this
same assessment. Readability can accept a simpler formula with higher assessed
cost, while its runtime-improvement flag includes all capture work. These are
declared model units, not bytes, allocations, or measured timings.

An unsuccessful connected array attempt can retry the contained scalar regions
available when array reads are statement boundaries. This alternative extraction
is lazy and cached per analysis. Only strictly smaller array-free regions are
scheduled, excluding overlaps with accepted edits or pending regions; they cannot
produce another array retry. The rejected attempt's search and presentation work
stay charged, and all alternatives share the existing remaining file budget.
Capture costs and proof requirements are unchanged.

## Generated-expression normalization

`JavaOriginalExpressions` parses each generated output expression into a private
AST before restoring suitable original literal spellings. It transfers ownership
of that disposable expression to `JavaExpressionNormalizer`. This helper visits
its generated nodes once and retains unchanged nodes instead of recursively
copying every subtree at every ancestor. A long generated receiver chain therefore
no longer causes quadratic subtree-copy work in this normalization step.

The original compilation unit is never consumed. Literal forms taken from it are
copied into the generated AST, retaining their source AST and parent links. A
redundant generated parenthesis wrapper is discarded only for the existing
whitelist of expression forms; precedence-sensitive parentheses remain. Replaced
children are detached before reattachment, and unchanged children are not reset.
There is no shared mutable AST cache or transfer of source-node ownership.

The existing rendering depth and expression-length limits remain in force, as do
comment preservation, source-dependency validation, numerical obligations and
independent emitted-Java verification. This is a rendering implementation change,
not another optimizer, a new mathematical rule or an increase in search budgets.

`JavaExpressionNormalizerTest` checks node identity, parent links, literal-source
isolation, child-list ordering, unsupported forms and the depth boundary. These
are deterministic regression checks rather than timing assertions. Normalizer
microbenchmarks describe only that component; they do not establish a whole-plugin
speedup, new corpus findings or faster execution of the transformed Java program.
