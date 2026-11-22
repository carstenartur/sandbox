# sandbox_use_general_type

Eclipse cleanup plugin that widens variable declaration types to more general supertypes/interfaces based on actual usage.

## Overview

This plugin analyzes variable declarations and checks whether the declared type can be **widened** (generalized) to a more general supertype or interface, based on the **actual usage** of the variable. The instantiation type stays the same, only the declaration type changes.

The analysis is not limited to collections. A class hierarchy is eligible only
when its bindings and supported local uses establish a safe declaration change.

## Examples

### Example 1: Collections

```java
// Before:
LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
map.put("a", 1);
map.get("a");

// After:
Map<String, Integer> map = new LinkedHashMap<>();
map.put("a", 1);
map.get("a");
```

### Example 2: I/O Streams

```java
// Before:
FileInputStream fis = new FileInputStream("file.txt");
fis.read();
fis.close();

// After:
InputStream fis = new FileInputStream("file.txt");
fis.read();
fis.close();
```

### Example 3: Lists

```java
// Before:
ArrayList<String> list = new ArrayList<>();
list.add("a");
list.size();

// After:
List<String> list = new ArrayList<>();
list.add("a");
list.size();
```

## Algorithm

The plugin follows these steps:

1. **Find variable declarations** with explicit concrete types (not `var`, not primitives, not in public API signatures)
2. **Collect all usages** of each variable: method calls, field accesses, casts, instanceof checks, assignments to other variables, passed as parameters
3. **Walk the type hierarchy** (`ITypeBinding.getSuperclass()` and `getInterfaces()`) to build the full class hierarchy
4. **Record binding-resolved method signatures and used fields** on the variable; reject unsupported use roles
5. **Find the most general type** (highest in hierarchy) that still declares all the used methods/fields
6. **Rewrite** the variable declaration type if a more general type was found; update imports accordingly

## Safety Rules

The type is **NOT changed** when:

- Variable is declared with `var`
- Variable type is primitive
- Variable is a field or method parameter (only local variables)
- Variable is cast to a specific subtype somewhere
- Variable is used with `instanceof` for a specific subtype
- Variable is passed as a method argument, returned, or used as an assignment value outside the supported receiver-use analysis
- The widened type would lose access to a method/field actually used on the variable

## Binding and Use-site Checks

The analyzer considers single-variable local declaration statements with resolved
bindings. Arrays, raw or type-annotated declarations, recovered bindings and
unsupported roles such as constructor arguments, aliases or method references
prevent widening.

Supported roles include receiver method/field access, comparisons with `null`,
standalone simple reassignment to the variable, and enhanced-for iteration when
the replacement remains `Iterable`. At least one method or field use is required.

Candidate types must retain the resolved member contracts:

- Method parameter/return types and accessibility are checked; a replacement must not add a checked exception beyond the original resolved call's contract.
- Field accesses retain the same declaration and type, and static method calls retain the same method declaration.
- A new type or method must not introduce deprecation where the original was not deprecated; raw/annotated supertypes and classpath-restricted types are excluded.

Widening must also preserve the type relationship required by an `equals`
receiver use and must not make an initializer or reassignment cast redundant.
Existing classpath-restricted references are retained, including references whose
removal could make a suppression unnecessary. Rewrites use the candidate's
resolved generic arguments and the surrounding import context; the old type
import is removed only when no surviving use needs it.

## Usage

1. **Enable the cleanup**:
   - Window → Preferences → Java → Code Style → Clean Up
   - Select your profile → Configure
   - Go to "Use General Type (Sandbox)" tab
   - Check "Use general types for variables"

2. **Run the cleanup**:
   - Right-click on project/package/file → Source → Clean Up
   - Or enable as a save action: Preferences → Java → Editor → Save Actions

## Limitations

See [TODO.md](TODO.md) for planned enhancements and known limitations.

Current limitations:

- The cleanup changes local declarations, not fields, parameters, method signatures or callers in other files.
- Resolved member signatures and supported use roles constrain widening; an unsupported argument, return or assignment use is left unchanged.
- Missing bindings and concrete-type observations prevent a safe widening decision.
- A useful technical supertype is not automatically the best domain abstraction; review the proposed type in the execution preview.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Requirements

- Eclipse 2025-12 or later
- Java 25

## License

Eclipse Public License 2.0 (EPL-2.0)

## Contributing

This is part of the sandbox project for experimenting with Eclipse JDT cleanups. See the main repository README for contribution guidelines.
