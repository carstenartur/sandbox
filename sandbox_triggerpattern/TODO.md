# TriggerPattern Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#triggerpattern) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Full cleanup and save action support implemented

### Completed
- ✅ Plugin structure created
- ✅ Basic string concatenation patterns (`"" + $x` and `$x + ""`)
- ✅ Pattern registration via extension point
- ✅ Integration with TriggerPattern engine
- ✅ Documentation (ARCHITECTURE.md)
- ✅ Quick Assist integration (Ctrl+1)
- ✅ Cleanup integration (Save Actions and Source → Clean Up)
- ✅ UI preferences for cleanup configuration
- ✅ Batch apply to entire project support
- ✅ Fixed preference registration (v1.2.2) - Added missing `registerPreference()` call to properly count checkbox in cleanup UI
- ✅ Double-checked locking detection (concurrency hint, inspired by NetBeans DoubleCheck)
- ✅ Shift out of range detection
- ✅ Test module (`sandbox_triggerpattern_test`) with tests for all cleanup types
- ✅ Encoding pattern plugins (CharsetForName, StringConstructor, StringGetBytes)
- ✅ `.sandbox-hint` DSL-based CleanUp and QuickAssist (`HintFileCleanUp`, `HintFileQuickAssistProcessor`)
- ✅ Bundled pattern libraries: `collections`, `modernize-java9`, `modernize-java11`, `performance`
- ✅ Workspace-level `.sandbox-hint` file loading — users can place hint files in project directories

### Implementation Status vs. Issue #709

All 7 phases of the [TriggerPattern DSL Implementierungsplan (#709)](https://github.com/carstenartur/sandbox/issues/709) are **complete**.
See [sandbox_common/TODO.md](../sandbox_common/TODO.md#implementation-status-vs-issue-709) for the detailed per-phase checklist.

**Closeable related issues**: #709 (all phases done), #722 (extension points), #723 (reporting + Problem-View), #724 (per-bundle preferences for all 6 bundles), #725 (editor support).
**Remaining related issues**: #729 (DSL-based JUnit migration — Phase 1 replaceStaticImport + Phase 3 assume5 done; type guards remain).

### In Progress
- [ ] Additional pattern variations

### Pending
- [ ] More complex string simplification patterns
- [ ] Edge case handling improvements
- [ ] Performance testing with large codebases

## Priority Tasks

### 1. ~~Complete Test Module~~ ✅ COMPLETED
**Priority**: High  
**Effort**: 2-3 hours

Test module `sandbox_triggerpattern_test` has been created with comprehensive test cases:
- `StringSimplificationCleanUpTest` — string simplification cleanup tests
- `ThreadingCleanUpTest` — threading cleanup tests
- `ShiftOutOfRangeCleanUpTest` — shift out of range cleanup tests
- `DoubleCheckLockingTest` — double-checked locking detection tests
- `StringSimplificationTest` — unit tests for string simplification patterns
- `EncodingPatternTest` — encoding pattern plugin tests

### 2. Add Edge Case Handling
**Priority**: Medium  
**Effort**: 1-2 hours

Handle special cases:
- Already using String.valueOf() - don't suggest
- Concatenation with multiple operands: `"" + a + b`
- Null literals: `"" + null`
- Parenthesized expressions

### 3. Add More String Patterns
**Priority**: Medium  
**Effort**: 3-4 hours

Implement additional useful patterns:

#### Pattern: Null-safe toString
```java
@TriggerPattern(value = "$x != null ? $x.toString() : \"\"", kind = PatternKind.EXPRESSION)
// Suggest: String.valueOf($x)
```

#### Pattern: Redundant toString with concat
```java
@TriggerPattern(value = "$x.toString() + \"\"", kind = PatternKind.EXPRESSION)
// Suggest: String.valueOf($x)
```

#### Pattern: String.format with single %s
```java
@TriggerPattern(value = "String.format(\"%s\", $x)", kind = PatternKind.EXPRESSION)
// Suggest: String.valueOf($x)
```

### 4. Pattern Refinements
**Priority**: Low  
**Effort**: 2-3 hours

Improve existing patterns:
- Check if replacement is actually beneficial (e.g., don't replace if $x is already a String literal)
- Add type checking to avoid suggesting String.valueOf() for expressions that are already strings
- Consider context - sometimes `"" + x` is intentional for readability

## Known Issues

### Issue 1: Multiple Operands
**Status**: Not handled

Currently the patterns don't handle:
```java
"" + a + b + c
```

This should potentially suggest:
```java
String.valueOf(a) + b + c
```

But needs careful consideration of operator precedence.

### Issue 2: String Literal Optimization
**Status**: Not handled

Pattern matches even when $x is already a String literal:
```java
"" + "hello"  // Pattern matches but replacement is not beneficial
```

Should check expression type before suggesting.

## Future Enhancements

### ~~Integration with Save Actions~~ ✅ COMPLETED
**Priority**: Medium → **COMPLETED**  
**Effort**: Depends on TriggerPattern engine enhancements → **Completed in v1.2.2**

**Status**: ✅ Completed  
The string simplification patterns are now integrated with Eclipse's cleanup infrastructure:
- Apply patterns as save actions via "Save Actions" configuration
- Batch apply to entire project via "Source → Clean Up"
- Configure which patterns to enable in cleanup preferences
- Full integration with Eclipse's cleanup UI

The implementation provides:
- `StringSimplificationCleanUp` - Main cleanup class
- `StringSimplificationCleanUpCore` - Core cleanup logic
- `StringSimplificationFixCore` - TriggerPattern-based operations
- UI preferences for configuration (DefaultCleanUpOptionsInitializer, SaveActionCleanUpOptionsInitializer, SandboxCodeTabPage)
- Cleanup constant `STRING_SIMPLIFICATION_CLEANUP` in MYCleanUpConstants

This feature is now available alongside the existing Quick Assist (Ctrl+1) functionality.

### Pattern Library
**Priority**: Low  
**Effort**: 4-6 hours

Create a comprehensive string simplification pattern library:
- StringBuilder optimization patterns
- String concatenation in loops
- String comparison patterns (equals, equalsIgnoreCase)
- Empty string checks

### Performance Optimization
**Priority**: Low  
**Effort**: 2-3 hours

Profile pattern matching performance:
- Measure matching time for large files
- Identify slow patterns
- Optimize if needed

The TriggerPattern engine handles most performance aspects, but individual hint methods should be efficient.

### User Configuration
**Priority**: Low  
**Effort**: 3-4 hours

Allow users to configure which patterns are active:
- Preference page for enabling/disabling patterns
- Pattern severity levels (error, warning, info)
- Custom pattern addition

This would require extending the TriggerPattern engine to support per-pattern configuration.

## Testing Strategy

### Unit Tests
- Pattern matching tests (verify patterns match expected code)
- Replacement tests (verify correct transformations)
- Edge case tests (null, complex expressions, etc.)

### Integration Tests
- Quick Assist integration (verify proposals appear correctly)
- Pattern registration (verify extension point loading)

### Manual Testing Checklist
- [ ] Test in Eclipse IDE with sample code
- [ ] Verify Quick Assist shows proposals
- [ ] Apply proposals and verify results
- [ ] Test with various Java versions
- [ ] Test with different expression types

## Documentation Improvements

### User Documentation
- [ ] Create user guide with examples
- [ ] Document when patterns should/shouldn't be applied
- [ ] Add screenshots showing Quick Assist in action

### Developer Documentation
- [ ] Document how to add new patterns
- [ ] Provide pattern authoring guidelines
- [ ] Create examples for common pattern types

## Contribution to Eclipse JDT

### Prerequisites
This plugin demonstrates TriggerPattern usage and would be contributed alongside the TriggerPattern engine to Eclipse JDT.

**Before contributing**:
1. Complete comprehensive testing
2. Ensure patterns follow Eclipse coding conventions
3. Verify patterns don't have false positives
4. Get community feedback on pattern usefulness

### Porting Checklist
- [ ] Replace `org.sandbox` with `org.eclipse` in package names
- [ ] Update plugin IDs and symbolic names
- [ ] Move to appropriate Eclipse JDT UI module
- [ ] Update extension point references
- [ ] Submit alongside TriggerPattern engine contribution

## Technical Debt

### Pattern Specificity
**Priority**: Medium

Some patterns may be too broad. Consider:
- Adding type constraints to placeholders
- Adding context-aware pattern matching
- Distinguishing between intentional and accidental patterns

### Code Duplication
**Priority**: Low

The two hint methods are very similar. Consider:
- Extracting common logic to helper methods
- Creating a base class for string simplification hints
- Generalizing the pattern to handle both cases

However, keeping methods separate improves readability and makes patterns more explicit.

## References

- [TriggerPattern Documentation](../sandbox_common/TRIGGERPATTERN.md)
- [Java String Best Practices](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/String.html)
- [Eclipse Quick Assist API](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/jdt/ui/text/java/IQuickAssistProcessor.html)

## Contact

For questions or suggestions about string simplification patterns, please open an issue or discussion in the repository.
