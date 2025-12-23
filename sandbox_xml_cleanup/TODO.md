# XML Cleanup Plugin - TODO

## Status Summary

**Current State**: Functional PDE XML cleanup implementation with size optimization focus

### Completed
- ✅ PDE file filtering (plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd)
- ✅ Location filtering (root, OSGI-INF, META-INF only)
- ✅ XSLT transformation with secure processing
- ✅ Whitespace normalization (reduce empty lines)
- ✅ Leading space→tab conversion (4 spaces to 1 tab)
- ✅ Eclipse workspace API integration (IFile, ILog)
- ✅ Change detection (only write if content changed)
- ✅ Indent preference support (default: OFF for size reduction)
- ✅ Duplicate file processing prevention
- ✅ Proper error handling (no printStackTrace/System.out)

### In Progress
- [ ] Comprehensive test coverage
- [ ] XMLUnit integration for semantic equality testing

### Pending
- [ ] UI preferences page for configuration
- [ ] Batch processing across multiple projects
- [ ] Eclipse PDE validation integration

## Priority Tasks

### 1. Add Comprehensive Tests
**Priority**: High  
**Effort**: 8-10 hours

Create test cases to verify:
- Size reduction for PDE XML files
- Semantic equality (XMLUnit ignoring whitespace)
- Idempotency (second run produces no change)
- Leading-indent-only tab conversion (not inline text)
- PDE file filtering accuracy
- Non-PDE files are ignored (pom.xml, build.xml)
- Indent preference behavior (default OFF, optional ON)

**Test Resources Needed**:
```
sandbox_xml_cleanup_test/resources/
├── plugin.xml (with various indentation patterns)
├── feature.xml (with excessive whitespace)
├── fragment.xml
├── schema/
│   ├── sample.exsd
│   └── sample.xsd
└── ignored/
    ├── pom.xml (should NOT be processed)
    └── build.xml (should NOT be processed)
```

### 2. UI Preferences Page
**Priority**: Medium  
**Effort**: 4-6 hours

Create Eclipse preferences page for XML cleanup options:
- Enable/disable XML cleanup
- Enable/disable indentation (default: OFF)
- Preview before/after transformation
- Integration with existing `SandboxCodeTabPage`

**Location**: `org.sandbox.jdt.internal.ui.preferences.cleanup.SandboxCodeTabPage`

### 3. Batch Processing UI
**Priority**: Low  
**Effort**: 6-8 hours

Support batch processing of XML files:
- Process all PDE files in selected projects
- Progress dialog with cancellation
- Summary report (files processed, size saved)
- Selective application (choose which files to transform)

## Known Issues

### Current Limitations
1. **Requires Java Compilation Context**: XML cleanup triggers during Java cleanup, requiring a Java compilation unit. This is a limitation of integrating with Eclipse JDT cleanup framework.
2. **No Standalone Mode**: Cannot be run independently without Java files in project.
3. **Location Hardcoded**: PDE directories (OSGI-INF, META-INF) are hardcoded, not configurable.

### Potential Issues
- **XSLT Performance**: Large XML files may take time to transform (consider streaming for files >1MB)
- **Workspace Lock**: File updates during cleanup may conflict with other operations

## Future Enhancements

### PDE Integration
**Priority**: High  
**Effort**: 12-16 hours

Move from JDT cleanup to PDE-specific integration:
- **Problem**: Currently requires Java compilation unit context
- **Solution**: Create PDE-specific cleanup action
- **Benefits**: 
  - Works with non-Java Eclipse plugin projects
  - Better integration with PDE validation
  - Can run independently

**Approach**:
1. Implement `org.eclipse.ui.IWorkbenchWindowActionDelegate`
2. Register in `plugin.xml` under PDE menu
3. Remove dependency on Java compilation unit
4. Trigger via "Clean Up..." action in PDE editor

### Schema Validation
**Priority**: Medium  
**Effort**: 8-10 hours

Add XML schema validation:
- Validate plugin.xml against Eclipse plugin schema
- Validate *.exsd against extension point schema
- Report validation errors via Eclipse problem markers
- Integrate with PDE validation framework

**Benefits**:
- Catch structural errors early
- Ensure transformations don't break validity
- Better error messages

### Advanced Whitespace Rules
**Priority**: Low  
**Effort**: 4-6 hours

Additional normalization options:
- Configurable tab width (default: 4 spaces = 1 tab)
- Line ending normalization (LF vs CRLF)
- Trim trailing whitespace
- Max consecutive empty lines (currently: 2)

### XSL Stylesheet Customization
**Priority**: Low  
**Effort**: 6-8 hours

Allow custom XSLT stylesheets:
- User-defined transformation rules
- Project-specific formatter.xsl
- Fallback to built-in stylesheet
- Validation of custom stylesheets

## Testing Strategy

### Current Coverage
Basic infrastructure in place, needs expansion.

### Needed Tests

#### Unit Tests
- `SchemaTransformationUtils` - XSLT transformation
- `XMLPlugin` - File filtering logic
- `XMLCandidateHit` - Hit creation and storage
- Whitespace normalization patterns
- Leading space to tab conversion

#### Integration Tests
- Full cleanup cycle with sample plugin.xml
- Semantic equality verification
- Idempotency verification
- Indent preference behavior
- Error handling (malformed XML, missing files)

#### Performance Tests
- Large XML file processing (>100KB)
- Batch processing of multiple files
- Memory usage during transformation

### Test Data Requirements
Create realistic PDE XML samples:
- **plugin.xml** - Various extension points, deeply nested
- **feature.xml** - Multiple includes, nested features
- **fragment.xml** - Simple fragment definition
- **sample.exsd** - Complex extension point schema
- **sample.xsd** - Generic XML schema

## Eclipse Integration

### Current State
- Registered as JDT cleanup: `org.eclipse.jdt.ui.cleanup.xmlcleanup`
- Requires Java compilation unit context
- Integrates with Eclipse cleanup framework

### Ideal State
- PDE-specific action/command
- Works with any Eclipse plugin project
- Independent of Java files
- Integrated with PDE editors

### Migration Path
1. Keep current JDT integration for Java-based plugin projects
2. Add PDE-specific integration for non-Java projects
3. Provide both paths for maximum compatibility

## Technical Debt

### XML Processing
Current implementation uses JAXP with DOM parsing:
- ✅ **Good**: Standard Java API, secure by default
- ⚠️ **Consider**: StAX for streaming large files
- ⚠️ **Consider**: SAX for memory-constrained environments

### Caching Strategy
Currently caches processed files per session:
- ✅ **Good**: Prevents duplicate processing
- ⚠️ **Issue**: Cache not cleared between runs
- **Fix**: Clear cache when cleanup disabled or preferences changed

### Error Recovery
Partial error handling implemented:
- ✅ **Good**: Uses Eclipse ILog instead of printStackTrace
- ⚠️ **Issue**: Transformation failures may leave files unchanged
- **Enhancement**: Report transformation errors via markers

## Performance Considerations

### Current Performance
XSLT transformation is reasonably fast for typical PDE files (<50KB).

### Optimization Opportunities
1. **Streaming Processing**: Use StAX for files >100KB
2. **Parallel Processing**: Transform multiple files concurrently
3. **Incremental Processing**: Only process changed files
4. **Caching**: Cache transformation results for unchanged files

## References

- [Eclipse Plugin Manifest](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/misc/plugin_manifest.html)
- [Eclipse Extension Point Schema](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/guide/tools/editors/schema_editor/overview.htm)
- [JAXP Secure Processing](https://docs.oracle.com/en/java/javase/21/security/java-api-xml-processing-jaxp-security-guide.html)

## Contact

For questions about XML cleanup or suggestions for improvements, please open an issue in the repository.

## Next Steps

### Immediate (This Week)
1. ✅ Complete core implementation
2. [ ] Add basic test cases
3. [ ] Update documentation (architecture.md, todo.md)
4. [ ] Run code review and security scans

### Short Term (Next Sprint)
1. [ ] Comprehensive test coverage
2. [ ] UI preferences integration
3. [ ] Performance testing with large files
4. [ ] Bug fixes from initial testing

### Long Term (Future Releases)
1. [ ] PDE-specific integration (independent of JDT)
2. [ ] Schema validation integration
3. [ ] Custom stylesheet support
4. [ ] Eclipse Marketplace publication

## Strategic Direction

**Recommendation**: Focus on PDE integration to make this plugin more useful for Eclipse plugin developers.

### Why PDE Integration?
- **Current limitation**: Requires Java files to trigger cleanup
- **Target users**: Eclipse plugin developers (PDE users)
- **Better fit**: PDE projects may not have Java files
- **Improved UX**: Right-click cleanup in PDE editors

### Implementation Priority
1. **Phase 1**: Current JDT integration (for Java-based plugins)
2. **Phase 2**: Add PDE-specific action (for all plugin projects)
3. **Phase 3**: Integrate with PDE validation framework
4. **Phase 4**: Consider contributing to Eclipse PDE project
