# Method Reusability Finder - TODO

## Pending Implementation

### Core Features
- [ ] Implement basic method similarity detection in `MethodReuseFinder`
- [ ] Implement AST-based pattern matching in `CodePatternMatcher`
- [ ] Implement method signature comparison in `MethodSignatureAnalyzer`
- [ ] Add similarity threshold configuration
- [ ] Create marker/warning system for detected duplicates

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
- [ ] Add comprehensive test cases
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
- [ ] Add JavaDoc to all public methods
- [ ] Create user guide with examples
- [ ] Document configuration options
- [ ] Add troubleshooting guide
- [ ] Create video tutorial

## Known Issues

None yet - this is a new implementation

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
