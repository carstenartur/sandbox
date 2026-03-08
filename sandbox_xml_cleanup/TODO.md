# XML Cleanup Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#xml_cleanup) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Functional PDE XML cleanup implementation with size optimization focus

### Completed
- ✅ PDE file filtering (plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd)
- ✅ Location filtering (root, OSGI-INF, META-INF only)
- ✅ XSLT transformation with secure processing
- ✅ Comment and processing instruction preservation in XSLT
- ✅ Whitespace normalization (reduce empty lines)
- ✅ Leading space→tab conversion (4 spaces to 1 tab)
- ✅ Empty element collapsing (<element></element> → <element/>)
- ✅ Eclipse workspace API integration (IFile, ILog)
- ✅ Change detection (only write if content changed)
- ✅ Indent preference support (default: OFF for size reduction)
- ✅ UI preferences page integration (indent as sub-preference)
- ✅ Duplicate file processing prevention
- ✅ Proper error handling (no printStackTrace/System.out)
- ✅ Test resources for PDE and ignored files (pom.xml, build.xml)

### In Progress
- [ ] Comprehensive test coverage (file filtering tests pending)

### Pending
- [ ] Batch processing across multiple projects
- [ ] Eclipse PDE validation integration

## Priority Tasks

### 1. Add Comprehensive Tests
**Priority**: High  
**Effort**: 8-10 hours

Create test cases to verify:
- Size reduction for PDE XML files ✅
- Semantic equality (XMLUnit ignoring whitespace) ✅
- Idempotency (second run produces no change) ✅
- Leading-indent-only tab conversion (not inline text) ✅
- PDE file filtering accuracy (file filtering tests exist)
- Non-PDE files are ignored (pom.xml, build.xml) (file filtering tests exist)
- Indent preference behavior (default OFF, optional ON)

**Test Resources Needed**:
```
sandbox_xml_cleanup_test/resources/
├── pde-files/
│   ├── plugin.xml (with various indentation patterns and comments) ✅
│   ├── feature.xml (with excessive whitespace) ✅
│   ├── fragment.xml ✅
│   └── sample.exsd (extension point schema) ✅
└── ignored-files/
    ├── pom.xml (should NOT be processed) ✅
    └── build.xml (should NOT be processed) ✅
```

**Status**: XMLUnit integration ✅ COMPLETED. Test resources created. File filtering tests exist.

### 1a. XMLUnit Integration (✅ COMPLETED → ⚡ REPLACED with DOM-based implementation)
**Priority**: Medium  
**Effort**: 4-6 hours

**Status**: ⚡ **Replaced with DOM-based implementation due to OSGi compatibility**
- ✅ Initially added XMLUnit 2.9.1 dependencies (xmlunit-core, xmlunit-matchers)
- ⚡ **Removed XMLUnit dependencies** - not available as OSGi bundle in Eclipse target platform
- ⚡ **Replaced with DOM-based implementation** using standard Java XML APIs (`javax.xml.parsers`)
- ✅ Created `XMLTestUtils` utility class for semantic XML comparison (now using DOM APIs)
- ✅ Created `XMLSemanticEqualityTest` with 7 comprehensive tests:
  - Basic semantic equality preservation
  - Multiple elements preservation
  - Attribute preservation
  - Text content preservation
  - Namespace preservation
  - Complex EXSD schema handling
  - Size reduction with semantic equality verification
- ✅ Enhanced existing `XMLCleanupTransformationTest` with semantic checks
- ✅ Tests verify transformations preserve XML structure and meaning while only changing whitespace

**Implementation Details**:
- `XMLTestUtils.isXmlSemanticallyEqual()` - Ignores whitespace and comments (using DOM)
- `XMLTestUtils.isXmlSemanticallyEqualWithComments()` - Preserves comment comparison (using DOM)
- `XMLTestUtils.assertXmlSemanticallyEqual()` - Provides detailed diff on failure (using DOM)
- Tests cover plugin.xml, feature.xml, .exsd, .xsd, and complex nested structures
- **No external dependencies required** - uses only standard Java XML APIs

### 2. UI Preferences Page (✅ COMPLETED)
**Priority**: Medium  
**Effort**: 4-6 hours

**Status**: ✅ Completed in recent update
- ✅ Enable/disable XML cleanup checkbox
- ✅ Enable/disable indentation as sub-preference (default: OFF)
- ✅ Integration with existing `SandboxCodeTabPage`
- ✅ Proper dependency registration (indent depends on main cleanup)
- ⏭️ Preview before/after transformation (future enhancement)

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
1. **Requires Java Compilation Context (JDT cleanup only)**: JDT-based cleanup triggers during Java cleanup, requiring a Java compilation unit. **However, the new PDE integration (XMLCleanupHandler/XMLCleanupAction) works independently without Java files.**
2. **No Standalone Mode (JDT cleanup only)**: JDT cleanup cannot be run independently without Java files in project. **Use the new "Clean Up PDE XML" command for standalone operation.**
3. **Location Hardcoded**: PDE directories (OSGI-INF, META-INF) are hardcoded, not configurable.

### Potential Issues
- **XSLT Performance**: Large XML files may take time to transform (consider streaming for files >1MB)
- **Workspace Lock**: File updates during cleanup may conflict with other operations

## Future Enhancements

### PDE Integration (✅ COMPLETED)
**Priority**: High  
**Effort**: 12-16 hours

**Status**: ✅ Completed - PDE-specific integration has been added

Move from JDT cleanup to PDE-specific integration:
- **Problem**: Currently requires Java compilation unit context
- **Solution**: Create PDE-specific cleanup action ✅
- **Benefits**: 
  - Works with non-Java Eclipse plugin projects ✅
  - Better integration with PDE validation (future enhancement)
  - Can run independently ✅

**Implementation**:
1. ✅ Created `XMLCleanupService` - Core transformation logic independent of JDT
2. ✅ Created `XMLCleanupHandler` - Command handler for Eclipse UI
3. ✅ Created `XMLCleanupAction` - Action delegate for backward compatibility
4. ✅ Registered command, handler, and menu contributions in `plugin.xml`
5. ✅ Added context menu on PDE XML files (plugin.xml, feature.xml, fragment.xml, *.exsd, *.xsd)
6. ✅ Added project-level cleanup via context menu
7. ✅ Progress dialog support with cancellation
8. ✅ Background job execution (non-blocking UI)

**Usage**:
- Right-click on PDE XML files → "Clean Up PDE XML"
- Right-click on project → "Clean Up PDE XML" (processes all PDE files)
- Source menu → XML Cleanup → "Clean Up PDE XML Files"

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
- ✅ **Fixed**: Cache is cleared at start of each cleanup run (XMLPlugin.java:80)
- ✅ **Verified**: No stale file references between runs

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
1. ✅ Complete core implementation - COMPLETED
2. ✅ Add basic test cases (transformation tests, file filtering tests) - COMPLETED
3. ✅ Update documentation (architecture.md, todo.md) - COMPLETED
4. ✅ Fix XSLT comment preservation - COMPLETED
5. ✅ Complete PDE integration (XMLCleanupHandler/Service) - COMPLETED
6. [ ] Run code review and security scans

### Short Term (Next Sprint)
1. ✅ Comprehensive test coverage (XMLUnit integration completed) - COMPLETED
2. ✅ UI preferences integration (completed with indent sub-preference) - COMPLETED
3. [ ] Performance testing with large files
4. [ ] Bug fixes from initial testing

### Long Term (Future Releases)
1. ✅ PDE-specific integration (independent of JDT) - COMPLETED
2. [ ] Schema validation integration
3. [ ] Custom stylesheet support
4. [ ] Eclipse Marketplace publication

## Strategic Direction

**Current State**: ✅ PDE integration is complete, making this plugin fully functional for Eclipse plugin developers.

### Completed PDE Integration
- ✅ **No Java requirement**: Works independently without Java files
- ✅ **Target users**: Eclipse plugin developers (PDE users) can use it directly
- ✅ **Standalone operation**: PDE projects without Java files are fully supported
- ✅ **Right-click cleanup**: Available in PDE editor context menus

### Next Priorities
1. **Phase 1**: ✅ Current JDT integration (for Java-based plugins) - COMPLETED
2. **Phase 2**: ✅ Add PDE-specific action (for all plugin projects) - COMPLETED
3. **Phase 3**: Integrate with PDE validation framework - FUTURE
4. **Phase 4**: Consider contributing to Eclipse PDE project - FUTURE

## TriggerPattern DSL Integration

### Status: ❌ Not applicable

The XML cleanup operates on XML files (plugin.xml, feature.xml, .exsd), not Java
source code. The `.sandbox-hint` DSL is designed for Java AST transformations and
cannot express XML restructuring rules.

### Notes
- This plugin is fundamentally different from Java-based cleanups
- XML formatting rules would require a separate DSL or tool
- PDE integration uses Eclipse's XML/DOM APIs, not JDT
