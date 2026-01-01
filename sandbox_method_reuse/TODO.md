# Method Reusability Finder - TODO

## Recent Changes (2025-01-01)

### Completed: Architecture Refactoring
- ✅ **Enum-Based Fix Core**: Created `MethodReuseCleanUpFixCore` following `UseExplicitEncodingFixCore` pattern
  - Two enum values: `METHOD_REUSE` and `INLINE_SEQUENCES`
  - Each value delegates to a plugin
  - Proper `findOperations()` and `rewrite()` methods
- ✅ **AbstractMethodReuse**: Moved from `helper/lib/` to `helper/` package
  - Updated signature to accept `MethodReuseCleanUpFixCore` instead of old `MethodReuseFixCore`
  - No longer throws CoreException from `find()` method
  - Signature matches `AbstractExplicitEncoding` pattern
- ✅ **Plugin Classes**: Created in `helper/` package
  - `MethodReusePlugin` - Placeholder for general method similarity
  - `InlineSequencesPlugin` - Structure for inline sequence replacement
- ✅ **CleanUpCore**: Updated to use EnumSet pattern
  - `createFix()` uses `EnumSet<MethodReuseCleanUpFixCore>`
  - `computeFixSet()` checks enabled constants
  - `getPreview()` aggregates from enum values
- ✅ **Test Enablement**: Removed `@Disabled` annotation from test
- ✅ **Documentation**: Updated ARCHITECTURE.md to reflect new structure

## Pending Implementation

### Core Features - Priority 1
- [ ] **Implement InlineSequencesPlugin find() method**
  - Traverse compilation unit to find all methods
  - Use InlineCodeSequenceFinder for each method
  - Create ReferenceHolder with match data
  - Add rewrite operations to set
- [ ] **Implement InlineSequencesPlugin rewrite() method**
  - Extract InlineSequenceMatch from ReferenceHolder
  - Use MethodCallReplacer to create method invocation
  - Apply variable mapping to arguments
  - Replace matching statements with method call
- [ ] **Test and validate inline sequence detection**
  - Run existing tests
  - Fix any issues found
  - Add additional test cases if needed

### Core Features - Priority 2
- [ ] Implement basic method similarity detection in `MethodReuseFinder`
- [ ] Implement AST-based pattern matching in `CodePatternMatcher`
- [ ] Implement method signature comparison in `MethodSignatureAnalyzer`
- [ ] Wire general method reuse into MethodReusePlugin
- [ ] Add similarity threshold configuration
- [ ] Create marker/warning system for detected duplicates

### Inline Sequence Detection - Remaining Work
- [x] Helper classes implemented (InlineCodeSequenceFinder, CodeSequenceMatcher, VariableMapping)
- [x] Enum and plugin structure created
- [ ] Complete integration in InlineSequencesPlugin.find()
- [ ] Complete InlineSequencesPlugin.rewrite() implementation
- [ ] Add support for return statement replacement (not just variable declarations)
- [ ] Handle edge cases (nested sequences, overlapping matches)
- [ ] Implement confidence scoring for matches
- [ ] Add configuration for minimum sequence length
- [ ] Support for extracting new methods from inline sequences

### Algorithm Improvements
- [ ] Add token-based similarity calculation
- [ ] Implement variable name normalization
- [ ] Add control flow graph comparison
- [ ] Handle method overloading in analysis
- [ ] Support lambda expressions and method references

### User Interface
- [ ] Add preference page for similarity threshold
- [ ] Add quick fix suggestions for found duplicates
- [ ] Create visual diff view for similar methods
- [ ] Add "ignore" annotations for false positives
- [ ] Implement batch analysis mode

### Testing
- [x] Add test cases for inline sequence detection
  - [x] Simple inline sequence with different variable names
  - [x] Inline with method call expressions
  - [x] Multiple variable mapping
- [x] Enable inline sequence tests
- [ ] Verify tests pass (currently they will fail as find/rewrite not implemented)
- [ ] Add negative test cases (side effects, unsafe patterns)
- [ ] Add comprehensive test cases for method similarity
- [ ] Test with various Java versions (11, 17, 21)
- [ ] Performance testing with large codebases
- [ ] Test false positive rate
- [ ] Add test cases for edge cases

### Performance
- [ ] Implement caching mechanism for analysis results
- [ ] Add incremental analysis support
- [ ] Optimize AST traversal
- [ ] Add parallel analysis for multi-module projects
- [ ] Profile and optimize hot paths

### Documentation
- [x] Updated ARCHITECTURE.md with new structure
- [ ] Add JavaDoc to all public methods in new classes
- [ ] Create user guide with examples
- [ ] Document configuration options
- [ ] Add troubleshooting guide
- [ ] Create video tutorial

## Known Issues

### Build Issues (Fixed)
- ✅ Unhandled exception type CoreException - Fixed by updating method signatures
- ✅ Cannot infer type arguments for ReferenceHolder<> - Fixed by proper generic usage
- ✅ Type mismatch errors - Fixed by correcting class structure

## Next Steps (Immediate)

1. **Implement InlineSequencesPlugin.find()**
   - Use InlineCodeSequenceFinder to search for matches
   - Create proper ReferenceHolder with match data
   - Add operations to the operations set

2. **Implement InlineSequencesPlugin.rewrite()**
   - Extract match data from ReferenceHolder
   - Use MethodCallReplacer to generate method invocation
   - Apply the AST transformation

3. **Test and Validate**
   - Run tests to verify structure is correct
   - Fix any compilation or runtime issues
   - Iterate on implementation

## Future Enhancements

### Short-term (next release)
- Basic method similarity detection
- Simple warning markers
- Integration with Eclipse cleanup UI

### Medium-term
- Advanced AST pattern matching
- Configurable similarity thresholds
- Quick fix refactoring proposals
- Cross-file analysis

### Long-term
- Machine learning-based similarity detection
- Automatic code extraction and refactoring
- Cross-project analysis
- IDE-independent library version

## Integration with Eclipse JDT

When ready for upstream contribution:
1. Replace `org.sandbox` with `org.eclipse` in package names
2. Move constants from `MYCleanUpConstants` to Eclipse's `CleanUpConstants`
3. Update plugin.xml to reference Eclipse packages
4. Add to Eclipse JDT cleanup UI
5. Submit contribution to eclipse-jdt/eclipse.jdt.ui repository

## Notes

- This plugin is designed to be easily portable to Eclipse JDT
- Follow Eclipse coding conventions throughout
- Maintain high test coverage (aim for >80%)
- Keep performance in mind - analysis should be fast
- Consider false positive rate in algorithm design
- **Pattern**: Follow UseExplicitEncodingFixCore enum pattern for consistency
