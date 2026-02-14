# Encoding Quickfix Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#2-sandbox_encoding_quickfix) | [Architecture](ARCHITECTURE.md) | [Test Documentation](../sandbox_encoding_quickfix_test/README.md)

## Status Summary

**Current State**: Stable implementation with comprehensive test coverage for 22 different encoding-related API transformations

### Completed - All 22 Implemented Transformations

The plugin now supports transformations for the following APIs:

1. ✅ `Charset.forName("UTF-8")` → `StandardCharsets.UTF_8` (Java 18+)
2. ✅ `Channels.newReader(ch, "UTF-8")` → `Channels.newReader(ch, StandardCharsets.UTF_8)` (Java 10+)
3. ✅ `Channels.newWriter(ch, "UTF-8")` → `Channels.newWriter(ch, StandardCharsets.UTF_8)` (Java 10+)
4. ✅ `String.getBytes()` → `String.getBytes(Charset.defaultCharset())` or with explicit encoding
5. ✅ `new String(byte[], "UTF-8")` → `new String(byte[], StandardCharsets.UTF_8)` (Java 10+)
6. ✅ `new InputStreamReader(is)` → `new InputStreamReader(is, Charset.defaultCharset())`
7. ✅ `new OutputStreamWriter(os)` → `new OutputStreamWriter(os, Charset.defaultCharset())`
8. ✅ `new FileReader(file)` → `new InputStreamReader(new FileInputStream(file), Charset.defaultCharset())`
9. ✅ `new FileWriter(file)` → `new OutputStreamWriter(new FileOutputStream(file), Charset.defaultCharset())`
10. ✅ `new PrintWriter(filename)` → `new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filename), Charset.defaultCharset()))`
11. ✅ `new PrintStream(file, "UTF-8")` → `new PrintStream(file, StandardCharsets.UTF_8)`
12. ✅ `ba.toString()` → `ba.toString(Charset.defaultCharset())` (Java 10+)
13. ✅ `new Formatter(file, "UTF-8")` → `new Formatter(file, StandardCharsets.UTF_8)`
14. ✅ `URLDecoder.decode(s, "UTF-8")` → `URLDecoder.decode(s, StandardCharsets.UTF_8)` (Java 10+)
15. ✅ `URLEncoder.encode(s, "UTF-8")` → `URLEncoder.encode(s, StandardCharsets.UTF_8)` (Java 10+)
16. ✅ `new Scanner(file, "UTF-8")` → `new Scanner(file, StandardCharsets.UTF_8)`
17. ✅ `Properties.storeToXML(os, comment, "UTF-8")` → `Properties.storeToXML(os, comment, StandardCharsets.UTF_8)` (Java 10+)
18. ✅ `Files.newBufferedReader(path)` → `Files.newBufferedReader(path, StandardCharsets.UTF_8)` (Java 8+)
19. ✅ `Files.newBufferedWriter(path)` → `Files.newBufferedWriter(path, StandardCharsets.UTF_8)` (Java 8+)
20. ✅ `Files.readAllLines(path)` → `Files.readAllLines(path, StandardCharsets.UTF_8)` (Java 8+)
21. ✅ `Files.readString(path)` → `Files.readString(path, StandardCharsets.UTF_8)` (Java 11+)
22. ✅ `Files.writeString(path, content)` → `Files.writeString(path, content, StandardCharsets.UTF_8)` (Java 11+)

### In Progress
- None currently

### Cleanup Behavior Modes

The plugin supports three different modes for handling encoding transformations:

#### 1. KEEP_BEHAVIOR Mode
Replaces explicit `"UTF-8"` string literals with `StandardCharsets.UTF_8`, but leaves platform-default encodings as `Charset.defaultCharset()` to preserve existing behavior.

**Example:**
```java
// Before
new InputStreamReader(in);              // Uses platform default
Charset.forName("UTF-8");               // Explicit UTF-8

// After
new InputStreamReader(in, Charset.defaultCharset());  // Explicit default preserved
StandardCharsets.UTF_8;                               // Replaced
```

**Use Case**: Conservative approach for codebases that may rely on platform-specific encoding behavior.

#### 2. ENFORCE_UTF8 Mode
Replaces all implicit platform-default encodings and explicit `"UTF-8"` strings with `StandardCharsets.UTF_8`.

**Example:**
```java
// Before
new InputStreamReader(in);              // Uses platform default
new FileReader(file);                   // Uses platform default
Charset.forName("UTF-8");               // Explicit UTF-8

// After
new InputStreamReader(in, StandardCharsets.UTF_8);
new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8);
StandardCharsets.UTF_8;
```

**Use Case**: Standardize entire codebase on UTF-8 for consistent behavior across all platforms.

#### 3. ENFORCE_UTF8_AGGREGATE Mode
Similar to ENFORCE_UTF8, but creates a class-level constant for UTF-8 and references it throughout the class to reduce repetition.

**Example:**
```java
// Before
new InputStreamReader(in, StandardCharsets.UTF_8);
new FileReader(file);

// After
private static final Charset UTF_8 = StandardCharsets.UTF_8;

new InputStreamReader(in, UTF_8);
new InputStreamReader(new FileInputStream(file), UTF_8);
```

**Use Case**: Reduce repetition and create a single point of configuration for the encoding constant.

### Java Version Requirements

The cleanup is Java-version-aware and only applies transformations compatible with the project's Java version:

| Java Version | Capability | Details |
|--------------|------------|---------|
| **< Java 7** | ❌ Disabled | `StandardCharsets` not available |
| **Java 7-9** | ✅ Basic | `StandardCharsets.UTF_8` replacements, `Charset.defaultCharset()` |
| **Java 10+** | ✅ Extended | Most transformations including `Channels.newReader()`, `ByteArrayOutputStream.toString()`, `URLEncoder/URLDecoder` |
| **Java 18+** | ✅ Complete | `Charset.forName()` transformation support |

**Key Points:**
- Basic transformations using `Charset.defaultCharset()` are available since Java 1.5
- Most API transformations require Java 10+ for method overloads accepting `Charset`
- The cleanup automatically detects the Java version and only applies compatible transformations

### Pending Enhancements
- [ ] Configurable charset selection (allow charsets other than UTF-8)
- [ ] Detection of existing charset parameters to avoid duplicate transformation
- [ ] Performance optimization for large codebases

## Completed API Coverage

### Files API Methods ✅
All primary Files API methods now have charset transformations implemented:

- ✅ `Files.newBufferedReader(path)` - Add charset parameter (Java 8+)
- ✅ `Files.newBufferedWriter(path)` - Add charset parameter (Java 8+)
- ✅ `Files.readAllLines(path)` - Add charset parameter (Java 8+)
- ✅ `Files.readString(path)` - Add charset parameter (Java 11+)
- ✅ `Files.writeString(path, content)` - Add charset parameter (Java 11+)

### Potential Future API Coverage

Additional APIs that could be considered for future versions:

- `Files.write(path, lines)` - Add charset parameter for Collections overload
- `Files.lines(path)` - Add charset parameter for Stream<String> overload

### Stream-based APIs
Additional stream-related encoding APIs that could be considered:

- `PrintStream` constructors with file/filename
- `Console.reader()` / `Console.writer()` (though these are typically system-dependent)
- `ProcessBuilder` encoding settings

## Priority Tasks

### 1. Configurable Charset Selection
**Priority**: Medium  
**Effort**: 4-6 hours

Allow users to configure which charset to use instead of hard-coding UTF-8:
- Add preference page UI for charset selection
- Update cleanup to use configured charset
- Provide dropdown with common charsets (UTF-8, ISO-8859-1, UTF-16, etc.)

**Benefits**:
- More flexible for different project requirements
- Supports legacy codebases with specific encoding needs

### 2. Existing Charset Detection
**Priority**: High  
**Effort**: 3-4 hours

Avoid transforming code that already has explicit charset:
```java
// Should NOT transform - already has charset
FileReader r = new FileReader("file.txt", StandardCharsets.ISO_8859_1);
```

**Implementation**:
- Check method invocation arguments before transformation
- Only transform if charset parameter is missing
- Add test cases for existing charset scenarios

## Known Issues

None currently identified.

## Future Enhancements

### DSL-Based Cleanup Integration
**Priority**: Medium  
**Effort**: 6-8 hours

The plugin now bundles `encoding.sandbox-hint`, a declarative DSL file containing encoding transformation rules. Currently this file coexists with the imperative cleanup implementation. A future enhancement would be to:
- Use the hint file rules directly via `BatchTransformationProcessor` for simple cases
- Keep the imperative implementation for complex cases (version-aware transformations, three cleanup modes)
- Provide a unified preference page combining both approaches

### Smart Encoding Detection
**Priority**: Low  
**Effort**: 8-10 hours

Analyze the codebase to suggest appropriate charset:
- Detect if project already uses specific charset consistently
- Suggest matching existing charset usage
- Warn if mixed charset usage is detected

### Bulk Encoding Standardization
**Priority**: Medium  
**Effort**: 4-6 hours

Provide option to standardize all encoding across entire project:
- Find all encoding-related calls (even with explicit charsets)
- Offer to unify to single charset
- Generate report of current charset usage

### Encoding Migration Assistant
**Priority**: Low  
**Effort**: 10-12 hours

Create wizard for encoding migration:
- Analyze current encoding usage across project
- Preview all proposed changes
- Allow selective transformation
- Generate migration report

## Testing Strategy

### Current Coverage
Comprehensive test suite in `sandbox_encoding_quickfix_test`:
- API transformation tests for all supported methods
- Java version compatibility tests
- Edge case handling (null paths, complex expressions)
- Regression tests for previous bugs

### Future Testing
- Add tests for configurable charset selection
- Add tests for existing charset detection
- Performance tests for large codebases
- Integration tests with real-world code samples

## Performance Considerations

### Current Performance
- Single-pass AST traversal
- Efficient for typical file sizes
- May be slow for very large files (10,000+ lines)

### Optimization Opportunities
- Cache type bindings to avoid repeated lookups
- Batch process multiple files in parallel
- Early exit if file contains no encoding-related APIs
- Optimize AST visitor to skip irrelevant nodes

## Eclipse JDT Contribution

### Prerequisites
Before contributing to Eclipse JDT:
- [ ] Verify all tests pass on multiple Eclipse versions
- [ ] Ensure code follows Eclipse coding conventions
- [ ] Add configurable charset selection (makes it more useful)
- [ ] Get community feedback on feature design

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Move classes to `org.eclipse.jdt.core.manipulation`
- [ ] Register cleanup in Eclipse's extension points
- [ ] Update tests to use Eclipse test infrastructure
- [ ] Submit to Eclipse Gerrit for review

## Technical Debt

None currently identified. The codebase is clean and well-tested.

## References

- [StandardCharsets Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/charset/StandardCharsets.html)
- [Files API Documentation](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/Files.html)
- [FileReader JavaDoc](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/io/FileReader.html)

## Contact

For questions about encoding cleanup or suggestions for improvements, please open an issue in the repository.
