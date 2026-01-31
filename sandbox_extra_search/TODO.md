# Extra Search Plugin - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#extra_search) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Functional search view for class/API lookup with improved robustness and error handling

### Completed
- ✅ Basic search view UI
- ✅ Eclipse JDT search engine integration
- ✅ Results display with navigation
- ✅ Robustness improvements (defensive null checks, proper resource management)
- ✅ Proper generics usage (eliminated raw types)
- ✅ Enhanced error logging and user feedback
- ✅ Extended deprecated class list (Observable, Hashtable, Dictionary, security.acl.*, etc.)

### In Progress
- None currently

### Pending
- [ ] Search result export functionality
- [ ] Deprecation detection and highlighting
- [ ] Suggested replacement API lookup
- [ ] Search templates for common scenarios

## Priority Tasks

### 1. Deprecation Detection
**Priority**: High  
**Effort**: 6-8 hours

Automatically detect and highlight deprecated API usage in search results:
- Check if found classes/methods are marked @Deprecated
- Display deprecation status in results table
- Show deprecation message/reason if available
- Link to replacement API documentation

**Benefits**:
- Faster identification of upgrade-critical code
- Better upgrade planning
- Reduced manual inspection

### 2. Search Templates
**Priority**: Medium  
**Effort**: 4-6 hours

Provide pre-defined searches for common upgrade scenarios:
- Eclipse 2024-09 → 2024-12 deprecated APIs
- Java 11 → Java 17 migration patterns
- Java 17 → Java 21 migration patterns
- Common library upgrades

**Implementation**:
- Define template format (JSON/XML)
- UI for selecting and running templates
- Allow users to save custom templates

### 3. Export Search Results
**Priority**: Medium  
**Effort**: 3-4 hours

Export search results to various formats:
- CSV export for spreadsheet analysis
- HTML report with links to code
- Markdown for documentation
- JSON for programmatic processing

**Benefits**:
- Share results with team
- Track progress over time
- Document upgrade efforts

### 4. Suggested Replacements
**Priority**: High  
**Effort**: 8-10 hours

Provide suggested replacement APIs for deprecated classes:
- Database of known API migrations
- Integration with Eclipse documentation
- Links to migration guides
- Example code snippets

**Challenges**:
- Maintaining up-to-date migration database
- Handling context-specific replacements
- Supporting multiple migration paths

## Known Issues

### Performance with Large Workspaces
**Status**: Open  
**Priority**: Medium

Searching large workspaces (100+ projects) can be slow. Consider:
- Background search with progress indicator
- Search result caching
- Incremental result display
- Scope limiting options

### External Library Search
**Status**: Open  
**Priority**: Low

Currently only searches workspace code, not external JARs. Consider adding:
- JAR file indexing
- Maven/Gradle dependency search
- Binary class file search

## Recent Improvements (2024)

### Code Robustness Enhancements
- **Resource Management**: Implemented try-with-resources for all streams to prevent resource leaks
- **Null Safety**: Added defensive null checks throughout to prevent NPEs in edge cases:
  - Workspace availability checks
  - Project array validation
  - Dialog settings initialization guards
- **Type Safety**: Eliminated raw types, using proper generics for all collections
- **Error Handling**: Replaced `printStackTrace()` with proper `JavaPlugin.log()` calls
- **User Feedback**: Added specific error messages for different failure scenarios

### Deprecated Class List Extensions
Added commonly deprecated/legacy JDK classes:
- `java.util.Observable` and `java.util.Observer` (deprecated observer pattern)
- `java.util.Hashtable`, `java.util.Dictionary`, `java.util.Properties` (legacy collections)
- `java.rmi.server.LogStream` (deprecated RMI class)
- `javax.security.auth.Policy` (deprecated security class)
- `java.security.acl.*` package classes (Acl, AclEntry, Group, Owner, Permission, NotOwnerException, etc.)

These additions help identify more legacy code during Eclipse/Java version upgrades.

## Future Enhancements

### Interactive Migration Wizard
**Priority**: Medium  
**Effort**: 12-15 hours

Create wizard for guided upgrades:
1. Select upgrade path (e.g., Eclipse 2024-09 → 2024-12)
2. Scan for deprecated APIs
3. Show replacement options
4. Apply automated migrations where possible
5. Generate migration report

### Integration with Quick Fixes
**Priority**: High  
**Effort**: 8-10 hours

Integrate with Eclipse's quick fix system:
- Trigger search from deprecation warnings
- Offer quick fixes based on search results
- Automatic migration for simple cases

### Search History and Favorites
**Priority**: Low  
**Effort**: 2-3 hours

Maintain search history and allow saving favorite searches:
- Recent searches dropdown
- Star/favorite searches
- Search result bookmarking

### Batch Processing
**Priority**: Low  
**Effort**: 6-8 hours

Support running multiple searches in batch:
- Define search sets
- Run all searches and aggregate results
- Compare results across different search runs

## Testing Strategy

### Current Coverage
Basic functional testing of search UI and navigation.

### Needed Tests
- Unit tests for search query construction
- Performance tests with large workspaces
- Integration tests for deprecation detection
- UI tests for result display and filtering

## Eclipse JDT Contribution

### Contribution Potential
This plugin could be valuable to Eclipse community for version upgrades. Consider:
- Proposing as Eclipse JDT enhancement
- Contributing deprecation database
- Integration with Eclipse migration tooling

### Prerequisites
- [ ] Clean up UI code to follow Eclipse conventions
- [ ] Add comprehensive test coverage
- [ ] Create user documentation
- [ ] Build deprecation database for recent Eclipse versions

## Technical Debt

### Search Engine Abstraction
Current implementation is tightly coupled to Eclipse JDT search. Consider:
- Abstract search interface
- Support for alternative search implementations
- Pluggable search providers

## Performance Optimization

### Indexing Strategy
Consider building custom index for faster searches:
- Pre-index deprecated APIs
- Cache search results
- Incremental index updates
- Background indexing

### Result Streaming
For large result sets, implement streaming:
- Display results as they're found
- Pagination for thousands of results
- Virtual scrolling in results table

## References

- [Eclipse JDT Search API](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/core/search/package-summary.html)
- [Eclipse Platform Search Framework](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/search/package-summary.html)

## Contact

For questions or suggestions about the extra search plugin, please open an issue in the repository.
