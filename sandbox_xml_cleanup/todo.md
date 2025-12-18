# XML Cleanup Plugin - TODO

## Status Summary

**Current State**: Experimental XML cleanup implementation

### Completed
- ✅ Basic plugin structure
- ✅ XML parsing infrastructure
- ✅ Test framework

### In Progress
- [ ] Define high-value XML transformations
- [ ] Implement core cleanup patterns

### Pending
- [ ] Comprehensive XML file type support
- [ ] Schema-aware transformations
- [ ] Integration with Eclipse XML editors

## Priority Tasks

### 1. Identify High-Value Transformations
**Priority**: High  
**Effort**: 6-8 hours

Survey XML files in Eclipse projects to identify cleanup opportunities:
- Common plugin.xml verbosity patterns
- Deprecated POM configurations
- Inconsistent formatting patterns
- Outdated XML conventions

**Approach**:
- Analyze Eclipse plugin manifests
- Review Maven POM best practices
- Survey Eclipse community for pain points
- Prioritize most impactful transformations

### 2. Implement Core Transformations
**Priority**: High  
**Effort**: 10-12 hours

Implement most valuable XML cleanups:
- plugin.xml modernization
- POM dependency optimization
- XML formatting standardization
- Namespace cleanup

### 3. Schema Validation
**Priority**: Medium  
**Effort**: 8-10 hours

Add XML schema validation:
- Validate against plugin.xml schema
- Validate POM files against Maven schema
- Report schema violations
- Suggest corrections

**Benefits**:
- Catch errors early
- Ensure valid XML
- Better error messages

## Known Issues

Plugin is in early experimental stage. No specific issues identified yet.

## Future Enhancements

### Plugin.xml Modernization
**Priority**: High  
**Effort**: 10-12 hours

Modernize Eclipse plugin manifests:
- Update deprecated extension points
- Simplify extension declarations
- Remove obsolete attributes
- Apply current best practices

### POM Optimization
**Priority**: Medium  
**Effort**: 8-10 hours

Optimize Maven POM files:
- Remove redundant dependencies
- Update dependency versions
- Simplify plugin configurations
- Apply Maven best practices

### XML Editor Integration
**Priority**: Medium  
**Effort**: 10-12 hours

Integrate with Eclipse XML editors:
- Real-time cleanup suggestions
- Quick fixes in XML editor
- Formatting on save
- Validation markers

### Batch XML Processing
**Priority**: Low  
**Effort**: 6-8 hours

Support batch processing of XML files:
- Process all XML files in project
- Apply consistent transformations
- Generate transformation report
- Selective application

## Testing Strategy

### Current Coverage
Basic test infrastructure in place.

### Needed Tests
- Tests for each XML transformation
- Schema validation tests
- Formatting tests
- Integration tests with real XML files
- Performance tests for large XML files

## Eclipse Integration

### PDE vs JDT
**Decision Needed**: Determine if this belongs in PDE instead of JDT.

XML cleanup for plugin.xml and feature.xml is more relevant to:
- **Eclipse PDE** (Plugin Development Environment)
- Not Eclipse JDT (Java Development Tools)

**Recommendation**: Consider moving to PDE-focused repository or contributing to Eclipse PDE project.

### Contribution Path
If targeting Eclipse PDE:
- [ ] Refactor to PDE package structure
- [ ] Integrate with PDE validation framework
- [ ] Test with PDE editors
- [ ] Engage with PDE community

If targeting standalone:
- [ ] Publish as Eclipse Marketplace plugin
- [ ] Support multiple XML types (not just Eclipse)
- [ ] Provide general XML cleanup capabilities

## Technical Debt

### XML Parser Choice
Current implementation uses basic DOM parsing. Consider:
- SAX parsing for large files
- StAX for streaming
- JDOM for easier manipulation
- Evaluate performance trade-offs

### Transformation Engine
Need flexible transformation framework:
- Define transformations declaratively
- Support custom transformation rules
- Enable/disable specific transformations
- Extensible architecture

## Performance Considerations

### Current Performance
Basic DOM parsing is adequate for small-medium XML files.

### Optimization Opportunities
- Stream processing for large files
- Incremental parsing
- Caching parsed documents
- Background processing

## References

- [Eclipse Plugin Manifest](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/misc/plugin_manifest.html)
- [Maven POM Reference](https://maven.apache.org/pom.html)
- [XML Best Practices](https://www.w3.org/TR/xml/)

## Contact

For questions about XML cleanup or suggestions for transformations, please open an issue in the repository.

## Strategic Decision Needed

**Important**: Determine the future direction of this plugin:

### Option 1: Eclipse PDE Focus
- Move to PDE package structure
- Focus on plugin.xml and feature.xml
- Contribute to Eclipse PDE
- Leverage PDE infrastructure

### Option 2: General XML Cleanup
- Support wide range of XML types
- Generic XML transformations
- Standalone Eclipse plugin
- Publish to Marketplace

### Option 3: Maven POM Focus
- Focus on Maven POM optimization
- Integrate with m2e (Maven Eclipse integration)
- Contribute to m2e project
- Maven-specific transformations

**Recommendation**: Evaluate use cases and user demand before investing further development effort.
