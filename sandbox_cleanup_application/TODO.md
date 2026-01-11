# Cleanup Application - TODO

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#cleanup_application) | [Architecture](ARCHITECTURE.md)

## Status Summary

**Current State**: Functional prototype with basic command-line interface

### Completed
- ✅ Basic command-line argument parsing
- ✅ Configuration file loading (properties format)
- ✅ File and directory processing
- ✅ Workspace integration
- ✅ Cleanup application via Eclipse refactoring API
- ✅ Error handling and logging
- ✅ Help text display

### In Progress
- 🔄 Comprehensive documentation (README complete, this TODO in progress)

### Pending
- [ ] Dry-run mode (preview changes without applying)
- [ ] Parallel file processing
- [ ] Progress reporting
- [ ] Change report generation
- [ ] VCS integration
- [ ] Automated tests

## Priority Tasks

### 1. Dry-Run Mode
**Priority**: High  
**Effort**: 4-6 hours

Add option to preview changes without modifying files:

```bash
eclipse -nosplash -application ... -dryrun -verbose src/
```

**Implementation**:
- Add `-dryrun` flag to argument parser
- Compute changes but don't call `change.perform()`
- Print diff-style output showing proposed changes
- Add option to export changes to file

**Benefits**:
- Safer for production use (validate before applying)
- Allows review of changes before commit
- Useful for CI verification

### 2. Progress Reporting
**Priority**: Medium  
**Effort**: 3-4 hours

Add progress feedback for long-running operations:

```bash
Processing 1234 files...
[====>    ] 45% (555/1234) - MyClass.java
```

**Implementation**:
- Add progress monitor to cleanup refactoring
- Print progress bar in normal/verbose mode
- Show current file being processed
- Estimate time remaining

**Benefits**:
- Better user experience for large codebases
- Shows application is working (not hung)
- Helps estimate batch processing time

### 3. Change Report Generation
**Priority**: Medium  
**Effort**: 6-8 hours

Generate detailed report of all changes made:

```bash
eclipse -nosplash -application ... -report changes.html src/
```

**Report Formats**:
- HTML: Interactive report with diffs
- JSON: Machine-readable for automation
- Text: Simple list of changed files

**Report Content**:
- List of processed files
- Number of changes per file
- Specific cleanups applied
- Before/after code snippets
- Summary statistics

**Benefits**:
- Audit trail of changes
- Integration with code review tools
- Documentation for code modernization projects

### 4. Parallel File Processing
**Priority**: Low  
**Effort**: 8-10 hours

Process multiple files concurrently for better performance:

```bash
eclipse -nosplash -application ... -threads 4 src/
```

**Implementation**:
- Use thread pool executor (e.g., 4-8 threads)
- Queue files for processing
- Synchronize workspace modifications
- Aggregate results safely

**Challenges**:
- Workspace thread safety
- Resource contention
- Error handling across threads
- Progress reporting

**Benefits**:
- Faster processing for large codebases
- Better CPU utilization
- Reduced total execution time

### 5. Selective Cleanup Application
**Priority**: Medium  
**Effort**: 3-4 hours

Allow specifying which cleanups to run:

```bash
eclipse -nosplash -application ... -only encoding,lambda src/
```

**Implementation**:
- Add `-only <cleanup-ids>` option
- Filter cleanup registry based on IDs
- Support comma-separated list
- Add `-exclude <cleanup-ids>` for inverse

**Configuration Example**:
```bash
# Only run encoding and lambda cleanups
-only org.sandbox.encoding,org.sandbox.lambda

# Run all except JUnit migration
-exclude org.sandbox.junit
```

**Benefits**:
- Faster execution (fewer cleanups)
- Targeted transformations
- Incremental adoption of cleanups

### 6. Automated Tests
**Priority**: High  
**Effort**: 10-12 hours

Create comprehensive test suite in `sandbox_cleanup_application_test`:

**Test Categories**:
1. **Argument Parsing Tests**
   - Valid arguments
   - Invalid arguments
   - Conflicting options
   - Help display

2. **Configuration Loading Tests**
   - Valid properties file
   - Missing configuration
   - Invalid property values
   - Empty configuration

3. **File Processing Tests**
   - Single file
   - Multiple files
   - Directory tree
   - Files outside workspace

4. **Cleanup Application Tests**
   - Verify cleanups applied
   - Check file modifications
   - Error handling
   - Skip behavior

5. **Integration Tests**
   - End-to-end scenarios
   - Real workspace setup
   - Multiple cleanup configurations

**Implementation**:
- Use JUnit 5 test framework
- Create test workspace fixtures
- Mock file system operations where possible
- Verify actual file changes

## Known Issues

### 1. Workspace Limitation
**Severity**: Medium

Files must be inside workspace directory. This prevents processing arbitrary files:

```bash
# Cannot process files outside workspace
eclipse -nosplash -application ... -data /home/user/workspace /tmp/Test.java
# Result: Skipped (outside workspace)
```

**Workaround**: Create symbolic links inside workspace

**Potential Fix**: 
- Auto-create temporary project in workspace
- Copy external files to workspace
- Process and copy back
- Clean up temporary project

### 2. No Exit Code Distinction
**Severity**: Low

Application returns non-zero for all errors, doesn't distinguish:
- Argument errors
- Configuration errors
- Processing errors
- Partial success (some files failed)

**Potential Fix**: Use different exit codes:
- 0: Success
- 1: Argument/configuration error
- 2: Processing error
- 3: Partial success

### 3. Limited Logging Control
**Severity**: Low

Only three logging levels: quiet, normal, verbose. No fine-grained control.

**Potential Enhancement**:
- Add log level: TRACE, DEBUG, INFO, WARN, ERROR
- Support log file output
- Configurable log format

## Future Enhancements

### VCS Integration
**Priority**: Low  
**Effort**: 10-12 hours

Integrate with version control systems:
- Auto-commit changes after cleanup
- Create branches for cleanup operations
- Generate commit messages based on applied cleanups
- Integration with Git, SVN

### Configuration Profiles
**Priority**: Low  
**Effort**: 4-6 hours

Support multiple named configuration profiles:

```bash
# Use built-in profile
eclipse -nosplash -application ... -profile minimal src/

# List available profiles
eclipse -nosplash -application ... -list-profiles
```

**Profiles**:
- `minimal`: Formatting only
- `moderate`: Common cleanups
- `aggressive`: All transformations
- `modernize`: Java version migration
- `style`: Code style enforcement

### IDE Integration
**Priority**: Low  
**Effort**: 6-8 hours

Provide Eclipse UI for running cleanup application:
- Menu item: "Run Cleanup on Selected Files"
- Configuration dialog
- Progress view
- Results view with diffs

### Maven Plugin
**Priority**: Medium  
**Effort**: 8-10 hours

Create Maven plugin wrapping cleanup application:

```xml
<plugin>
    <groupId>org.sandbox</groupId>
    <artifactId>cleanup-maven-plugin</artifactId>
    <version>1.0.0</version>
    <configuration>
        <config>cleanup.properties</config>
        <sourceDirectory>src/main/java</sourceDirectory>
    </configuration>
</plugin>
```

**Benefits**:
- Integrate with Maven build lifecycle
- Run during build phase
- Fail build on cleanup violations (lint mode)

## Testing Strategy

### Current Testing
- Manual testing only
- No automated tests

### Needed Tests
1. **Unit Tests**: Test individual components
2. **Integration Tests**: Test full application flow
3. **Performance Tests**: Test with large codebases
4. **Regression Tests**: Prevent bugs from reoccurring

### Test Coverage Goals
- 80% code coverage
- All argument parsing paths covered
- All error conditions tested
- Common use cases validated

## Performance Considerations

### Current Performance
- Sequential processing (one file at a time)
- Acceptable for small to medium projects (<1000 files)
- May be slow for large projects (>1000 files)

### Optimization Opportunities
1. **Parallel Processing**: Use multiple threads
2. **Batch Compilation**: Create compilation units in batch
3. **Incremental Processing**: Only process changed files
4. **Caching**: Cache workspace metadata and type bindings

### Benchmarking
Need to establish performance baselines:
- Small project (10 files): Target <5 seconds
- Medium project (100 files): Target <30 seconds
- Large project (1000 files): Target <5 minutes

## Documentation Improvements

### Completed Documentation
- ✅ README.md: Comprehensive user guide
- ✅ ARCHITECTURE.md: Design and implementation details
- ✅ TODO.md: This file

### Additional Documentation Needed
- [ ] API documentation (Javadoc)
- [ ] Configuration reference (all cleanup constants)
- [ ] Troubleshooting guide (common issues)
- [ ] Tutorial: Getting started
- [ ] Video demonstration

## Eclipse JDT Contribution

### Prerequisites for Contribution
- [ ] Complete automated test suite
- [ ] Comprehensive documentation
- [ ] Generalize to work without sandbox cleanups
- [ ] Follow Eclipse coding conventions
- [ ] Code review and community feedback

### Contribution Value
This application provides significant value to Eclipse ecosystem:
- Enables automation of code cleanups
- Supports CI/CD integration
- Complements JavaCodeFormatter
- Extends Eclipse functionality to command line

### Porting Checklist
- [ ] Remove sandbox-specific code
- [ ] Use only core Eclipse cleanups
- [ ] Move to `org.eclipse.jdt.ui` namespace
- [ ] Update extension point registration
- [ ] Submit Eclipse CQ (contribution questionnaire)
- [ ] Create Gerrit review

## Technical Debt

### Current Technical Debt
1. **No Tests**: Application lacks automated tests
2. **Limited Error Handling**: Some error cases not handled gracefully
3. **Hard-coded Paths**: Some paths are hard-coded instead of configurable
4. **No Logging Framework**: Uses System.out/err instead of proper logging

### Refactoring Needs
1. **Extract Argument Parser**: Create separate class for argument parsing
2. **Extract File Processor**: Separate file processing logic
3. **Extract Configuration Loader**: Separate configuration loading
4. **Add Interfaces**: Define interfaces for extensibility

### Code Quality Improvements
- Add Javadoc comments
- Follow Eclipse naming conventions
- Improve error messages
- Add parameter validation

## Community Feedback

### Questions to Community
1. Should this application support non-workspace files?
2. What output formats are most useful for reports?
3. Should parallel processing be enabled by default?
4. What's the desired default log level?

### Feature Requests
(To be populated based on user feedback)

## References

- [Eclipse Application Documentation](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_applications.htm)
- [JDT Cleanup Constants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)
- [JavaCodeFormatter Bug](https://bugs.eclipse.org/bugs/show_bug.cgi?id=75333)

## Contact

For questions, suggestions, or contributions:
- Open an issue in the repository
- Submit a pull request
- Contact: See project contributors
