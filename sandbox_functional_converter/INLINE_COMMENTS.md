# Inline/Trailing Comment Support - Summary

## Question
**"Was ist mit inline Kommentaren?"** (What about inline comments?)

## Answer
Inline/trailing comments are NOW fully supported!

---

## What Was The Problem?

The functional converter plugin had a bug in comment extraction. It was capturing:
- Leading comments (before statements)
- Trailing/inline comments (after statements on same line) - **were missing**
- Embedded comments (inside statements)

**Example of missing support:**
```java
for (String item : items) {
    System.out.println(item); // Print the item <-- THIS WAS LOST!
}
```

The comment `// Print the item` was detected by the parser but not attached to any operation, so it disappeared in the transformation.

---

## What Was Fixed?

**File**: `JdtLoopExtractor.java` (line ~897)

**Before (buggy code):**
```java
boolean isLeadingComment = commentEndLine == nodeStartLine - 1 || 
                          (commentEndLine == nodeStartLine && commentEnd <= nodeStart);
// Missing: trailing comment detection!

if (isLeadingComment || isEmbeddedComment) {
    // Extract comment
}
```

**After (fixed code):**
```java
boolean isLeadingComment = commentEndLine == nodeStartLine - 1 || 
                          (commentEndLine == nodeStartLine && commentEnd <= nodeStart);
boolean isTrailingComment = commentStartLine == nodeEndLine && commentStart >= nodeEnd; // NEW!
boolean isEmbeddedComment = commentStart >= nodeStart && commentEnd <= nodeEnd;

if (isLeadingComment || isTrailingComment || isEmbeddedComment) { // Now includes trailing!
    // Extract comment
}
```

---

## Examples That Now Work

### Example 1: Simple Trailing Comment

**Before:**
```java
for (String item : items) {
    System.out.println(item); // Print the item
}
```

**After:**
```java
items.stream()
    .forEachOrdered(item -> {
        System.out.println(item); // Print the item
    });
```

### Example 2: Filter with Trailing Comment

**Before:**
```java
for (String item : items) {
    if (item.isEmpty()) continue; // Skip empty strings
    System.out.println(item);
}
```

**After:**
```java
items.stream()
    .filter(item -> {
        return !(item.isEmpty()); // Skip empty strings
    })
    .forEachOrdered(item -> {
        System.out.println(item);
    });
```

### Example 3: Mixed Comments

**Before:**
```java
for (String item : items) {
    // Leading: validate first
    validate(item);
    
    if (item.isEmpty()) continue; // Trailing: skip empty
    
    /* Block comment before print */
    System.out.println(item); // Another trailing comment
}
```

**After:**
```java
items.stream()
    .peek(item -> {
        // Leading: validate first
        validate(item);
    })
    .filter(item -> {
        return !(item.isEmpty()); // Trailing: skip empty
    })
    .forEachOrdered(item -> {
        /* Block comment before print */
        System.out.println(item); // Another trailing comment
    });
```

---

## Test Coverage

**New Tests Added:**
1. `test_EndToEnd_TrailingInlineCommentsAttached()` - Verifies map with trailing comment
2. `test_EndToEnd_FilterWithTrailingComment()` - Verifies if-continue with trailing comment

---

## Documentation Updated

All documentation now reflects inline/trailing comment support:

1. **FAQ.md** - New entry: "Was ist mit inline Kommentaren?"
2. **COMMENT_PRESERVATION.md** - Updated with examples of all three comment types
3. **EXAMPLES.md** - Added trailing/inline comment examples with transformations
4. **README.md** - Highlights inline comment support in features section

---

## Summary Table

| Comment Type | Position | Example | Status |
|--------------|----------|---------|--------|
| **Leading** | Line before statement | `// comment\nstatement;` | Supported |
| **Trailing/Inline** | Same line after statement | `statement; // comment` | **NOW SUPPORTED!** |
| **Embedded** | Inside statement | `if (/* comment */ x)` | Limited support |

---

## How to Use

**No configuration needed!** The feature works automatically:

1. Open Eclipse
2. Enable **Source** -> **Clean Up...** -> **Sandbox** -> **Java 8** -> **"Use functional call"**
3. Your trailing/inline comments will be preserved automatically

---

## References

- [FAQ: Inline Comments](FAQ.md#q-was-ist-mit-inline-kommentaren--what-about-inline-comments)
- [Comment Preservation Guide](COMMENT_PRESERVATION.md)
- [Examples](EXAMPLES.md)
- [Tests](../sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/CommentPreservationIntegrationTest.java)

---

**Status**: **Production-ready** as of February 2026  
**Phase**: 10 (Comment Preservation)  
**License**: Eclipse Public License 2.0
