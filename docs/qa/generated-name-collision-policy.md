# Generated-name collision policy

Multi-file cleanups must treat every generated declaration name as part of the immutable migration plan. A name is accepted unchanged or the candidate is rejected; domain types and generated helper classes are not silently renamed with numeric suffixes.

## Checked namespaces

The shared `GeneratedNameAllocator` classifies conflicts as:

- `TYPE`: owner, nested or local type declarations and type parameters;
- `MEMBER`: fields, enum constants, methods and annotation members;
- `LOCAL`: parameters and local variables;
- `IMPORT`: explicit imports with the requested simple name;
- `PLANNED`: another candidate reserving the same generated nested type in the same owner.

Results are sorted deterministically by request identity and collision metadata so previews, diagnostics and tests do not depend on source-unit iteration order.

`GeneratedNameHierarchyPolicy` supplements the current-AST namespaces with binding-based hierarchy checks. It traverses superclasses and interfaces with cycle protection and rejects a generated nested name that would hide an accessible inherited member type. Public and protected member types are checked across packages; package-visible types are checked within their package; private member types are ignored because they are not inherited or accessible. Collision descriptions are sorted by qualified member-type name.

Although Java permits a subclass to declare a nested type that hides an inherited member type, an automatic cleanup must not introduce that semantic name-resolution change without an explicit user decision.

## Int-to-enum application guard

`IntEnumMigrationPlan` includes the proposed enum name in the generated-name request identity. Immediately before resolving rewrite targets in the owner compilation unit, it revalidates that name against the current AST. A collision raises a stale-plan `CoreException` before rewrite operations or processed-node state are added.

The executable closed-source Int-to-Enum mode already rejects inherited owner types, so no hierarchy lookup is needed after that planning gate.

## Generated JUnit helper classes

The ExternalResource-to-extension migration retains its deterministic field-name/checksum convention. Before creating the nested helper class, `NamingUtils` applies both the shared current-AST allocator and the hierarchy policy. Existing declarations, imports, prospective reservations and accessible inherited member types therefore fail before any AST rewrite is committed, with the requested generated name and collision source in the diagnostic.
