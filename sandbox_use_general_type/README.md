# sandbox_use_general_type

Eclipse cleanup plugin that widens variable declaration types to more general supertypes/interfaces based on actual usage.

## Overview

This plugin analyzes variable declarations and checks whether the declared type can be **widened** (generalized) to a more general supertype or interface, based on the **actual usage** of the variable. The instantiation type stays the same, only the declaration type changes.

This works for **ALL classes**, not just Collections – any class hierarchy can be analyzed.

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
4. **Determine the set of methods/fields used** on the variable
5. **Find the most general type** (highest in hierarchy) that still declares all the used methods/fields
6. **Rewrite** the variable declaration type if a more general type was found; update imports accordingly

## Safety Rules

The type is **NOT changed** when:

- Variable is declared with `var`
- Variable type is primitive
- Variable is a field or method parameter (only local variables)
- Variable is cast to a specific subtype somewhere
- Variable is used with `instanceof` for a specific subtype
- Variable is passed to a method that requires the specific subtype (parameter type binding check) *(planned)*
- Variable is returned from a method with a specific return type *(planned)*
- Variable is assigned to another variable with a specific type *(planned)*
- The widened type would lose access to a method/field actually used on the variable

## Usage

1. **Enable the cleanup**:
   - Window → Preferences → Java → Code Style → Clean Up
   - Select your profile → Configure
   - Go to "Sandbox Code" tab
   - Check "Use general types for variables"

2. **Run the cleanup**:
   - Right-click on project/package/file → Source → Clean Up
   - Or enable as a save action: Preferences → Java → Editor → Save Actions

## Limitations

See [TODO.md](TODO.md) for planned enhancements and known limitations.

Current limitations:
- Only checks method names, not full signatures
- Does not analyze assignment target types
- Does not analyze method parameter type constraints
- Does not analyze return type constraints

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Requirements

- Eclipse 2025-12 or later
- Java 21 or later

## License

Eclipse Public License 2.0 (EPL-2.0)

## Contributing

This is part of the sandbox project for experimenting with Eclipse JDT cleanups. See the main repository README for contribution guidelines.
