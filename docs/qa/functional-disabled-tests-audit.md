# Functional converter disabled-test audit

## Purpose

Disabled tests are not automatically specifications. Each case was checked against Java semantics, the cleanup's advertised safety level, and the exact generated source before being enabled. Incorrect or outdated expectations were corrected rather than forcing the implementation to reproduce unsafe behavior.

## Result

All Functional Converter tests are active:

- **546 total**
- **546 enabled**
- **0 disabled**

The implementation now supports the legitimate positive cases and rejects the legitimate safety cases described below.

## Safety regressions

| Test | Classification | Required behavior |
|---|---|---|
| `testLoopWithMapPut_ShouldNotConvert` | Legitimate safety test | Iterating `map.keySet()` while invoking `map.put(...)` mutates the backing map and must not be converted. |
| `testFieldAccessReceiverModification_ShouldNotConvert` | Legitimate safety test | Iterating `this.list` while invoking `this.list.remove(...)` must not be converted. |
| `testGetterMethodReceiverModification_ShouldNotConvert` | Legitimate safety test | Repeated calls to the same getter identify the same source; `getList().add(...)` blocks conversion of `for (... : getList())`. |
| `testIterator_withExternalModification_notConverted` | Legitimate safety test | Assigning to `lastItem`, declared outside the iterator body and used afterwards, cannot be moved into a lambda. |
| `testCollectWithSideEffects_ShouldNotConvert` | Legitimate safety test | `counter++` before a collect terminal must not become a side-effecting `map` stage. |

The implementation compares iterated-source and mutation receivers through bindings, normalizes map views to their backing map, rejects unsafe external iterator-state mutation, and refuses non-terminal accumulator mutation.

## Positive iterator pipelines

The iterator handler feeds statements after the canonical `iterator.next()` declaration into the same `JdtLoopExtractor` / unified loop representation / `ASTStreamRenderer` pipeline used for enhanced-for loops.

Supported and enabled:

- iterator collect to `List`;
- iterator collect to `Set`;
- iterator map+collect;
- iterator method-call map+collect;
- iterator filter+collect;
- iterator filter+map+collect;
- iterator sum reduction.

Collect and reduce are applied only when the loop is preceded by a matching fresh local accumulator declaration. The declaration and loop are replaced together. A pre-existing, field-backed, or otherwise aliased target is not overwritten. For reductions, the original initializer becomes the reduce identity.

## Additional positive cases

### Existing target collection

A loop adding to a pre-existing target collection is converted to direct `forEach`, preserving existing contents and aliases:

```java
source.forEach(item -> target.add(item.toUpperCase()));
```

It is not replaced with `target = source.stream().collect(...)`.

### Copy-on-write collections

Read-only iteration over `CopyOnWriteArrayList` and `CopyOnWriteArraySet` is supported because their iterators and spliterators traverse a stable snapshot. Weakly consistent concurrent collections remain conservatively blocked. `iterator.remove()` remains unsupported.

### Array collect and map+collect

Array sources use `Arrays.stream(array)` and the project's canonical `Collectors.toList()` rendering. Import expectations account for wildcard imports and add only the required `java.util.stream.Collectors` import.

### Field side effects

A final field mutation such as `counter++` is rendered as a sequential `forEach` body rather than being misclassified as a local reduction. External local-variable mutation that violates lambda capture rules remains blocked.

## Test expectations corrected

- Replacing an existing collection with a newly collected instance was rejected as non-equivalent; the expected result is direct `forEach`.
- Copy-on-write collections are treated separately from weakly consistent concurrent collections.
- Array tests no longer require a redundant explicit `Arrays` import and use the existing canonical collector output.
- Lambda versus method-reference form is treated as style, not correctness; tests verify the supported canonical renderer output.
- The former “unused element” test actually mutates a field. It is now classified and tested as a sequential `forEach` side-effect case.

## Remaining conservative boundaries

- Iterators with `remove()`, multiple `next()` calls, `break`, labeled `continue`, nested loops, unsupported try/synchronized control flow, or unsafe external-local mutation are not converted.
- Existing/aliased accumulator replacement is forbidden unless the handler can preserve the original object through direct `forEach`.
- Weakly consistent concurrent collection iteration is not generalized from the copy-on-write cases.
- Parallelization is never introduced implicitly.

Every positive case is exercised as an exact transformed-source test; every safety case proves that no change is offered.
