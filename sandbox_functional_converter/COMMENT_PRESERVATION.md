# Comment Preservation in Functional Converter Plugin

## Übersicht / Overview

**Ja, das sandbox functional converter plugin unterstützt das Erhalten von Kommentaren!**

**Yes, the sandbox functional converter plugin supports preserving comments!**

Das Plugin bewahrt Kommentare während der Transformation von imperativen Schleifen zu funktionalen Stream-Pipelines. Diese Funktion wurde im Februar 2026 als Teil von Phase 10 implementiert und ist derzeit für Enhanced-For-Schleifen vollständig verfügbar.

The plugin preserves comments during transformation of imperative loops to functional Stream pipelines. This feature was implemented in February 2026 as part of Phase 10 and is currently fully available for enhanced-for loops.

---

## Current Status / Aktueller Status

| Loop Type | Comment Preservation | Status |
|-----------|---------------------|--------|
| **Enhanced-for loops** | ✅ Full support | Production-ready |
| **Bidirectional transformations** | ✅ Full support | All 4 directions preserve body comments |
| **Iterator-while loops** | ⏳ Body comments only | Operation comments pending |
| **Traditional for-loops** | ⏳ Body comments only | Operation comments pending |

---

## How It Works / Funktionsweise

### 1. Comment Extraction / Kommentarextraktion

The plugin uses Eclipse JDT AST to extract comments associated with statements in the loop body:

```java
// Das Plugin extrahiert:
for (String item : items) {
    // Skip empty items
    if (item.isEmpty()) continue;
    
    // Convert to uppercase for display
    String upper = item.toUpperCase();
    
    System.out.println(upper);
}
```

Comments are attached to the corresponding operations:
- Line comments (`// ...`)
- Block comments (`/* ... */`)
- Javadoc comments (`/** ... */`)

### 2. Comment Storage / Kommentarspeicherung

Comments are stored in the intermediate representation:
- **FilterOp**: Stores comments for filtering operations
- **MapOp**: Stores comments for mapping operations

Each operation maintains a list of associated comment strings extracted from the source code.

### 3. Comment Rendering / Kommentarausgabe

Comments are rendered as block lambdas in the transformed code:

**Before / Vorher:**
```java
for (String item : items) {
    // Skip empty items
    if (item.isEmpty()) continue;
    System.out.println(item);
}
```

**After / Nachher:**
```java
items.stream()
    .filter(item -> {
        // Skip empty items
        return !(item.isEmpty());
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

---

## Examples / Beispiele

### Example 1: Filter with Comment

**Before / Vorher:**
```java
for (Integer num : numbers) {
    // Only process positive numbers
    if (num <= 0) continue;
    System.out.println(num);
}
```

**After / Nachher:**
```java
numbers.stream()
    .filter(num -> {
        // Only process positive numbers
        return !(num <= 0);
    })
    .forEachOrdered(num -> {
        System.out.println(num);
    });
```

### Example 2: Map with Comment

**Before / Vorher:**
```java
for (String name : names) {
    // Convert to title case
    String title = toTitleCase(name);
    System.out.println(title);
}
```

**After / Nachher:**
```java
names.stream()
    .map(name -> {
        // Convert to title case
        return toTitleCase(name);
    })
    .forEachOrdered(title -> {
        System.out.println(title);
    });
```

### Example 3: Multiple Comments

**Before / Vorher:**
```java
for (Customer customer : customers) {
    // Skip inactive accounts
    if (!customer.isActive()) continue;
    
    // Get the billing address
    Address addr = customer.getBillingAddress();
    
    // Skip if no address
    if (addr == null) continue;
    
    // Print formatted address
    System.out.println(addr.format());
}
```

**After / Nachher:**
```java
customers.stream()
    .filter(customer -> {
        // Skip inactive accounts
        return !(!(customer.isActive()));
    })
    .map(customer -> {
        // Get the billing address
        return customer.getBillingAddress();
    })
    .filter(addr -> {
        // Skip if no address
        return !(addr == null);
    })
    .forEachOrdered(addr -> {
        // Print formatted address
        System.out.println(addr.format());
    });
```

### Example 4: Math.max with Comment

**Before / Vorher:**
```java
int max = Integer.MIN_VALUE;
for (Integer num : numbers) {
    // Track the maximum value seen so far
    max = Math.max(max, num);
}
```

**After / Nachher:**
```java
int max = Integer.MIN_VALUE;
// Track the maximum value seen so far
max = numbers.stream().reduce(max, Math::max);
```

---

## Bidirectional Transformations / Bidirektionale Transformationen

Comment preservation also works for bidirectional loop transformations:

### Enhanced-For ↔ Iterator-While

**Original Enhanced-For:**
```java
for (String item : items) {
    // Process the item
    System.out.println(item);
}
```

**Transformed to Iterator-While:**
```java
Iterator<String> iterator = items.iterator();
while (iterator.hasNext()) {
    String item = iterator.next();
    // Process the item
    System.out.println(item);
}
```

**And back to Enhanced-For:**
```java
for (String item : items) {
    // Process the item
    System.out.println(item);
}
```

All comments in the loop body are preserved through the transformation cycle using `ASTRewrite.createCopyTarget()`.

---

## Architecture / Architektur

The comment preservation system has three main components:

### 1. Extraction (JdtLoopExtractor)
```java
extractComments(ASTNode node, CompilationUnit cu)
├─ Identifies leading comments (line before statement)
├─ Identifies trailing comments (same line)
├─ Extracts comment text and removes delimiters
└─ Returns List<String> of comment lines
```

### 2. Storage (FilterOp, MapOp)
```java
class FilterOp {
    private List<String> associatedComments;
    
    void addComment(String comment)
    List<String> getComments()
    boolean hasComments()
}
```

### 3. Rendering (ASTStreamRenderer, StringRenderer)
```java
renderFilterOp(pipeline, filterOp, variableName)
├─ if (filterOp.hasComments())
│   └─ Generate block lambda with comment lines
└─ else
    └─ Generate expression lambda
```

---

## Technical Details / Technische Details

### Comment Types Supported

1. **Line Comments** (`//`)
   ```java
   // This is preserved
   if (condition) continue;
   ```

2. **Block Comments** (`/* */`)
   ```java
   /* This is also preserved */
   String x = value.toString();
   ```

3. **Javadoc Comments** (`/** */`)
   ```java
   /** Even Javadoc is preserved */
   result = calculate(input);
   ```

### Comment Position Rules

- **Leading comments**: Comments on the line(s) immediately before a statement ✅ Fully supported
- **Trailing/inline comments**: Comments on the same line after a statement ✅ **Fully supported (NEW!)**
- **Embedded comments**: Comments inside complex statements ⚠️ Limited support

**Example of all three types:**
```java
for (String item : items) {
    // Leading comment - appears before the statement
    if (/* embedded */ item.isEmpty()) continue; // Trailing/inline comment
    System.out.println(item);
}
```

**After transformation:**
```java
items.stream()
    .filter(item -> {
        // Leading comment - appears before the statement
        return !(/* embedded */ item.isEmpty()); // Trailing/inline comment
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

### Limitations / Einschränkungen

1. **Iterator-while and traditional for-loops**: Currently only preserve body comments via `createCopyTarget()`, not individual operation comments during stream conversion
2. **Complex expressions**: Comments embedded in complex expressions may not be preserved
3. **Multiple statements per line**: Comments may not be correctly associated in edge cases

---

## Testing / Tests

The comment preservation feature has comprehensive test coverage:

### Unit Tests (No Eclipse runtime)
- `CommentPreservationTest` - 4 tests for basic comment functionality
- `StringRendererTest` - 8 tests for comment-aware rendering

### Integration Tests (With Eclipse JDT)
- `CommentPreservationIntegrationTest` - 8 end-to-end tests with JDT AST
- `LoopBidirectionalTransformationTest` - 5 bidirectional comment tests

### Running Tests
```bash
# Run all comment preservation tests
xvfb-run --auto-servernum mvn test -Dtest=CommentPreservationIntegrationTest \
    -pl sandbox_functional_converter_test

# Run bidirectional comment tests
xvfb-run --auto-servernum mvn test -Dtest=LoopBidirectionalTransformationTest \
    -pl sandbox_functional_converter_test
```

---

## Future Enhancements / Zukünftige Erweiterungen

See [TODO.md Phase 10](TODO.md) for planned improvements:

1. **Extended Operation Support**
   - Comment support for `FlatMapOp`, `PeekOp`
   - Comment support for terminal operations (collect, reduce)

2. **Complete Handler Coverage**
   - Iterator-while handler: integrate JdtLoopExtractor for operation comments
   - Traditional for-loop handler: add comment extraction

3. **Advanced Features**
   - Comment merging for consecutive operations
   - Comment positioning heuristics improvement
   - Multi-line comment formatting options

---

## How to Enable / Aktivierung

Comment preservation is **enabled by default** for all supported transformations. No configuration is required.

To use the functional converter plugin:

1. Open **Source** → **Clean Up...** in Eclipse
2. Navigate to **Sandbox** → **Java 8** section
3. Enable **"Use functional call"**
4. Select **Stream** as target format
5. Click **OK** to apply

Your comments will be automatically preserved during loop-to-stream transformations.

---

## References / Referenzen

- [Architecture Documentation](ARCHITECTURE.md#comment-preservation)
- [TODO Phase 10](TODO.md#phase-10-comment-preservation)
- [Integration Tests](../sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/CommentPreservationIntegrationTest.java)
- [Unit Tests](../sandbox-functional-converter-core/src/test/java/org/sandbox/functional/core/renderer/CommentPreservationTest.java)

---

## Summary / Zusammenfassung

**Deutsch:**
Das sandbox functional converter plugin bietet umfassende Unterstützung für das Erhalten von Kommentaren während der Transformation von Enhanced-For-Schleifen zu funktionalen Stream-Pipelines. Kommentare werden automatisch extrahiert, mit den entsprechenden Operationen verknüpft und in Block-Lambda-Ausdrücken ausgegeben. Diese Funktion ist produktionsreif und wird durch 17+ Tests abgedeckt.

**English:**
The sandbox functional converter plugin provides comprehensive support for preserving comments during transformation of enhanced-for loops to functional Stream pipelines. Comments are automatically extracted, associated with corresponding operations, and rendered in block lambda expressions. This feature is production-ready and covered by 17+ tests.

---

**Status**: ✅ Production-ready for enhanced-for loops  
**Version**: Phase 10 implementation (February 2026)  
**License**: Eclipse Public License 2.0
