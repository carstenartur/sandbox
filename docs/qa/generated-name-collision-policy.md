# Generated-name collision policy

Multi-file cleanups must treat every generated declaration name as part of the immutable migration plan. A name is accepted unchanged or the candidate is rejected; domain types are not silently renamed with numeric suffixes.

## Checked namespaces

The shared `GeneratedNameAllocator` classifies conflicts as:

- `TYPE`: owner, nested or local type declarations and type parameters;
- `MEMBER`: fields, enum constants, methods and annotation members;
- `LOCAL`: parameters and local variables;
- `IMPORT`: explicit imports with the requested simple name;
- `PLANNED`: another candidate reserving the same generated nested type in the same owner.

Results are sorted deterministically by request identity and collision metadata so previews, diagnostics and tests do not depend on source-unit iteration order.

## Int-to-enum application guard

`IntEnumMigrationPlan` includes the proposed enum name in the generated-name request identity. Immediately before resolving rewrite targets in the owner compilation unit, it revalidates that name against the current AST. A collision raises a stale-plan `CoreException` before rewrite operations or processed-node state are added.

This closes the apply-time race where a valid plan could otherwise become uncompilable after another edit introduced a conflicting declaration. Planning-time diagnostics for all cleanup families remain follow-up work under issue #1225.