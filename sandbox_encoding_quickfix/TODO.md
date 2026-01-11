# Encoding Quickfix Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#2-sandbox_encoding_quickfix) | [Architecture](ARCHITECTURE.md) | [Test Documentation](../sandbox_encoding_quickfix_test/README.md)

## Status Summary

**Current State**: Stable implementation with comprehensive test coverage

### Completed
- ✅ FileReader/FileWriter transformation (Java 11+)
- ✅ Files.readAllLines() transformation
- ✅ Files.readString()/writeString() transformation (Java 11+)
- ✅ Scanner constructor transformation
- ✅ PrintWriter constructor transformation
- ✅ InputStreamReader/OutputStreamWriter transformation
- ✅ Java version detection and compatibility checks
- ✅ Comprehensive test suite

### In Progress
- None currently

### Pending
- [ ] Configurable charset selection (allow charsets other than UTF-8)
- [ ] Detection of existing charset parameters
- [ ] Additional encoding-related API support
- [ ] Performance optimization for large codebases

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

### 3. Expand API Coverage
**Priority**: Low  
**Effort**: 6-8 hours

Support additional encoding-related APIs:
- `ByteArrayOutputStream.toString()` → `toString(StandardCharsets.UTF_8)`
- `String.getBytes()` → `getBytes(StandardCharsets.UTF_8)`
- `URLDecoder.decode()/URLEncoder.encode()` methods
- `Channels.newReader()/newWriter()` methods

**Note**: Evaluate each API for compatibility with supported Java versions.

## Known Issues

None currently identified.

## Future Enhancements

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
