# Int-to-Enum generated-name and reference policy

The project-wide Int-to-Enum cleanup is intentionally limited to source-closed package-scoped migrations. Generated declarations and references must be deterministic, accessible and unambiguous before any edit is applied.

## Supported owner and caller model

- the owner is a top-level source type;
- the migrated method and constants are package-private;
- every direct invocation and modeled constant reference is inside the selected closed source scope;
- callers are in the same package as the owner;
- callers may be top-level, nested or inner types;
- generic top-level owners are supported because the generated enum is a static nested type by Java language rules;
- the default package follows the same simple-name rules, although named packages remain recommended.

Cross-package callers remain unsupported while the generated nested enum and migrated method are package-private. Public or protected API migration belongs to the separate compatibility-managed mode described in ADR 0008.

## Name reservation

`GeneratedTypeNamePolicy` checks prospective domain type names against:

- existing member, local, enum, record and annotation types;
- imported simple type names;
- owner and method type parameters;
- inherited nested types when bindings are available;
- conservative field and method name conflicts;
- top-level types in the affected package when the planner supplies the complete affected roots.

The policy never invents numeric suffixes for domain types. A collision returns the stable reason code `GENERATED_NAME_COLLISION` and a deterministic SHA-256 namespace fingerprint. The prospective enum name is already part of `IntEnumCandidate` record identity, so stale-plan equality cannot silently substitute a different name.

Every owner compilation unit is reassessed immediately before rewrite. A newly introduced collision aborts the coordinated change before edits are applied.

## Reference generation

References are created as JDT AST `Name` nodes rather than concatenated source text.

- inside the owner: `Status.PENDING`;
- ordinary same-package caller: `OrderProcessor.Status.PENDING`;
- same-package caller with a conflicting `OrderProcessor` type or type parameter: `test.OrderProcessor.Status.PENDING`;
- cross-package caller: rejected with `INACCESSIBLE_GENERATED_TYPE`.

The policy chooses the shortest unambiguous legal name and does not add unnecessary same-package imports. Unsupported or stale contexts fail closed with an explicit reason instead of producing inaccessible or ambiguous source.

## Extension points

The policy lives in `sandbox_common` so future generated JUnit extensions, compatibility adapters and helper types can use the same deterministic reservation contract. Those consumers must store the prospective name in their immutable plan and reassess it before rewrite.
