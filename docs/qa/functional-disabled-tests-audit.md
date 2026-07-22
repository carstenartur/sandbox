# Functional converter disabled-test audit

## Purpose

Disabled tests are not automatically specifications. Each case was checked against Java semantics, the cleanup's advertised safety level, and the exact generated source before being enabled. Incorrect or outdated expectations were corrected rather than forcing the implementation to reproduce unsafe behavior.

## Result

All Functional Converter tests are active. The generated Test Report workflow is the authoritative source for the exact test count on each revision; the Functional Converter module contains **0 disabled tests**.

The implementation now supports the legitimate positive cases and rejects the legitimate safety cases described below.

## Safety regressions

| Test | Classification | Required behavior |
|---|---|---|
| `testLoopWithMapPut_ShouldNotConvert` | Legitimate safety test | Iterating `map.keySet()` while invoking `map.put(...)` mutates the backing map and must not be converted. |
| `testFieldAccessReceiverModification_ShouldNotConvert` | Legitimate safety test | Iterating `this.list` while invoking `this.list.remove(...)` must not be converted. |
| `testGetterMethodReceiverModification_ShouldNotConvert` | Legitimate safety test | Repeated calls to the same getter identify the same source; `getList().add(...)` blocks conversion of `for (... : getList())`. |
| `testIterator_withExternalModification_notConverted` | Legitimate safety test | Assigning to `lastItem`, declared outside the iterator body and used afterwards, cannot be moved into a lambda. |
| `testCollectWithSideEffects_ShouldNotConvert` | Legitimate safety test | `counter++` before a collect terminal must not become a side-effecting `map` stage. |
| `existingTargetReassignedAfterLoopIsNotCaptured` | Legitimate safety test | An existing target that is reassigned after the loop is not effectively final and cannot be captured by a generated `forEach` lambda. |
| `additionalCollectCaptureReassignedAfterLoopBlocksConversion` | Legitimate safety test | A non-accumulator local referenced by the collected expression must remain effectively final. |
| `forEachCaptureReassignedAfterLoopBlocksConversion` | Legitimate safety test | A simple side-effecting loop cannot become `forEach` when its lambda would capture a subsequently reassigned local. |
| `iteratorCaptureReassignedAfterLoopBlocksConversion` | Legitimate safety test | Iterator-loop conversion must enforce the same effectively-final capture rule as enhanced-for conversion. |
| `enhancedForDoesNotReplaceConcreteListAccumulator` | Legitimate type-safety test | A concrete `ArrayList` declaration cannot be initialized from the result type promised by `Collectors.toList()`. |
| `enhancedForDoesNotReplaceConcreteSetAccumulator` | Legitimate semantic-safety test | A concrete or sorted set implementation must not be replaced by an unspecified `toSet()` result. |
| `iteratorDoesNotReplaceConcreteListAccumulator` | Legitimate type-safety test | Iterator conversion applies the same concrete-accumulator boundary as enhanced-for conversion. |
| `iteratorDoesNotReplaceConcreteSetAccumulator` | Legitimate semantic-safety test | Iterator conversion must preserve concrete set construction and ordering semantics. |

The implementation compares iterated-source and mutation receivers through bindings, normalizes map views to their backing map, conservatively recognises conventional no-argument getter aliases of iterated fields, rejects unsafe external iterator-state mutation, refuses non-terminal accumulator mutation, and validates Java's effectively-final rule for every local that remains in any generated loop lambda. Missing binding identities fail closed.

## Positive iterator pipelines

The iterator handler feeds statements after the canonical `iterator.next()` declaration into the same `JdtLoopExtractor` / unified loop representation / `ASTStreamRenderer` pipeline used for enhanced-for loops.

Supported and enabled:

- iterator collect to an interface-declared `List`;
- iterator collect to an interface-declared `Set`;
- iterator map+collect;
- iterator method-call map+collect;
- iterator filter+collect;
- iterator filter+map+collect;
- iterator sum reduction;
- effectively-final external captures in iterator `forEach` conversion.

Collect and reduce are applied only when the loop is preceded by a matching fresh local accumulator declaration. The declaration and loop are replaced together. A pre-existing, field-backed, aliased, concretely declared, or otherwise construction-sensitive target is not overwritten. For reductions, the original initializer becomes the reduce identity.

## Additional positive cases

### Existing target collection

A loop adding to a pre-existing collection or iterable target is converted to direct `forEach`, preserving existing contents and aliases when every captured local is effectively final:

```java
source.forEach(item -> target.add(item.toUpperCase()));
```

It is not replaced with `target = source.stream().collect(...)`. Array sources use the corresponding ordered stream form while copying the original AST body, so mappings, comments and the existing target object are preserved:

```java
Arrays.stream(array).forEachOrdered(item -> target.add(item.toUpperCase()));
```

### Consecutive loops

Consecutive loops are analysed and rewritten independently in source order. A compatible fresh accumulator may be merged into the first stream pipeline; later loops append through sequential `forEach` operations. Existing targets remain the same object. The previous `Stream.concat` preprocessing path is no longer used by the registered safe handler because it could bypass capture checks and replace aliased targets.

### Copy-on-write collections

Read-only iteration over `CopyOnWriteArrayList` and `CopyOnWriteArraySet` is supported because their iterators and spliterators traverse a stable snapshot. Weakly consistent concurrent collections remain conservatively blocked. `iterator.remove()` remains unsupported.

### Array collect and map+collect

Array sources with a compatible fresh interface accumulator use `Arrays.stream(array)` and the project's canonical collector rendering. Existing targets use an AST-preserving `forEachOrdered` body instead of replacing the collection. Import expectations account for wildcard imports and add only required imports.

### Field side effects

A final field mutation such as `counter++` is rendered as a sequential `forEach` body rather than being misclassified as a local reduction. Fields are not subject to local-variable capture finality; external local-variable mutation that violates lambda capture rules remains blocked.

## Test expectations corrected

- Replacing an existing collection with a newly collected instance was rejected as non-equivalent; the expected result is direct `forEach` for collection and iterable sources and `Arrays.stream(...).forEachOrdered(...)` for arrays.
- Copy-on-write collections are treated separately from weakly consistent concurrent collections.
- Array tests no longer require a redundant explicit `Arrays` import and use the canonical renderer output.
- Lambda versus method-reference form is treated as style, not correctness; tests verify the canonical renderer output.
- Iterator tests use the renderer's canonical filter parentheses, reduction form, line wrapping and `map(...).forEachOrdered(...)` representation.
- The former “unused element” test actually mutates a field. It is now classified and tested as a sequential `forEach` side-effect case.

## Remaining conservative boundaries

- Iterators with `remove()`, multiple `next()` calls, `break`, labeled `continue`, nested loops, unsupported try/synchronized control flow, or unsafe external-local mutation are not converted.
- Existing/aliased accumulator replacement is forbidden; supported conversions preserve the original target object through direct `forEach` or ordered array-stream traversal.
- Concrete or construction-sensitive fresh accumulator support remains blocked until #1251 models an explicit `Collectors.toCollection(...)` supplier.
- Loop conversions are rejected when local binding recovery is incomplete or a generated lambda would capture a non-effectively-final local.
- Weakly consistent concurrent collection iteration is not generalized from the copy-on-write cases.
- Parallelization is never introduced implicitly.

Every positive case is exercised as an exact transformed-source test; every safety case proves that no change is offered.
