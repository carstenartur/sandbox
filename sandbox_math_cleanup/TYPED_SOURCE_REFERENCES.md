# Typed source references without retaining JDT in the search graph

`JavaSourceReferences` is an opt-in, request-local view over the existing detached
`JavaSourceRewritePlan`. It does not add any field to Regelsuche `Expr` objects and
is not a replacement for the mathematical verifier or the source rewrite plan.

```java
try (var sources = JavaSourceReferences.open(sourcePlan, compilationUnit, source)) {
    var ref = sources.reference(sourceId, InfixExpression.class);
    InfixExpression originalNode = sources.get(ref);
    // Use the node for source inspection. Do not mutate the AST during lookup.
}
```

A handle contains a scope token, source ID and `Class<T>`, but no AST node or
reference to the lookup object. The factory validates the type; lookup uses
`Class.cast` without unchecked casts. Cross-request handles, changed source text,
unknown IDs, wrong types, post-resolution AST mutations and closed lookups fail
closed. Source plans must be resolved against a freshly parsed, unmodified AST.
Edits still require the existing source-version and semantic checks.

Several source nodes can belong to one occurrence. Conversely, newly synthesized
mathematical expressions need not have a single original source node. This is why
source identity must not participate in mathematical equality, hash keys,
common-subexpression elimination or persistent learned rules.

`close()` clears this table's strong references. It cannot release nodes which a
caller deliberately stores elsewhere. JDT nodes can lead back to their parent
and root; retaining a node in a global cache may therefore retain much more than
the small fragment it represents. Do not store this lookup in optimizer results,
proofs or caches. The table is thread-confined, not a synchronization mechanism.

The number of retained entries follows source anchors, not explored candidates.
Resolution performs the existing source validation once; subsequent lookups use
a map, type check and AST modification counter. No measured byte count or CPU
speedup is claimed. Heap/throughput measurements remain separate qualification.

## Scope of this change

This provides the typed source bridge and six JUnit regressions. It does not yet
teach the extractor to move `IntBuffer.get`, preserve arbitrary embedded constant
formulas, or emit new pure field-read captures. These require separate binding,
purity, evaluation-order and exception contracts; a source pointer alone proves
none of them. The new Regelsuche signed-additive proposals also require SDK
integration and renewed complete-class pilot runs before claiming new hits here.
