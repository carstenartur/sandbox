# Cleanup Application - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#cleanup_application) | [TODO](TODO.md)

## Overview

The **Cleanup Application** provides a command-line interface for running Eclipse JDT code cleanups on Java source files without launching the full Eclipse IDE. It's implemented as an Equinox application that leverages the Eclipse JDT cleanup framework to process Java files in batch mode.

## Purpose

- Enable automated code cleanup in CI/CD pipelines
- Apply cleanup transformations from command line
- Process multiple files and directories in batch
- Integrate with build systems and automation tools
- Support all registered Eclipse cleanup plugins

## Architecture

### Application Entry Point

The application follows Eclipse's application pattern with two main classes:

```
CodeCleanupApplicationWrapper (Entry Point)
    ↓ validates workspace
    ↓ initializes Eclipse platform
CodeCleanupApplication (Main Logic)
    ↓ parses arguments
    ↓ loads configuration
    ↓ processes files
```

### Core Components

#### 1. CodeCleanupApplicationWrapper

**Location**: `org.sandbox.jdt.cleanup.application.CodeCleanupApplicationWrapper`

**Purpose**: Application entry point that validates Eclipse workspace availability before delegating to main application logic.

**Responsibilities**:
- Implement `IApplication` interface
- Validate workspace with `-data` parameter
- Delegate to `CodeCleanupApplication` for actual work
- Handle graceful shutdown

**Key Method**:
```java
public Object start(IApplicationContext context) throws Exception {
    // Validate workspace availability
    // Delegate to CodeCleanupApplication
    // Return exit code
}
```

#### 2. CodeCleanupApplication

**Location**: `org.sandbox.jdt.cleanup.application.CodeCleanupApplication`

**Purpose**: Core application logic for parsing arguments, loading configuration, and processing files.

**Responsibilities**:
- Parse command-line arguments
- Load cleanup configuration from properties file
- Resolve files and directories recursively
- Map file paths to workspace resources
- Create compilation units
- Apply cleanup transformations
- Save modified files

**Key Methods**:
```java
public IStatus run(String[] args) {
    // Parse arguments: -config, -verbose, -quiet, files
    // Load cleanup configuration
    // Process each file/directory
    // Return status
}

private void processFile(File file, IContainer workspaceRoot) {
    // Map file to IFile resource
    // Create ICompilationUnit
    // Apply cleanups
    // Save changes
}
```

### Processing Flow

```
1. Application Start
   ↓
2. Parse Command-Line Arguments
   │  ├─ Extract -config <file>
   │  ├─ Extract -verbose/-quiet flags
   │  └─ Extract file/directory paths
   ↓
3. Load Cleanup Configuration
   │  └─ Read properties file with cleanup constants
   ↓
4. Resolve Files
   │  ├─ Expand directories recursively
   │  └─ Filter for .java and .jav files
   ↓
5. For Each File:
   │  ├─ Check if inside workspace
   │  ├─ Map to IFile resource
   │  ├─ Create ICompilationUnit
   │  ├─ Apply registered cleanups
   │  └─ Save changes to disk
   ↓
6. Report Status
   └─ Return exit code
```

## Integration with Eclipse JDT

### Compilation Unit Creation

Files must be mapped to Eclipse workspace resources to create `ICompilationUnit` instances:

```java
IPath filePath = Path.fromOSString(file.getAbsolutePath());
IFile iFile = ResourcesPlugin.getWorkspace().getRoot().getFileForLocation(filePath);
ICompilationUnit cu = JavaCore.createCompilationUnitFrom(iFile);
```

**Requirement**: Files must be inside the workspace directory tree.

### Cleanup Registry

The application uses all cleanups registered via Eclipse extension points:

- Core JDT cleanups (`org.eclipse.jdt.ui.cleanUps`)
- Sandbox custom cleanups (encoding, platform helper, functional converter, JUnit, etc.)
- Any other installed cleanup plugins

**Configuration**: Cleanups are enabled/configured via the properties file using cleanup constant keys.

### Cleanup Application

Cleanups are applied using Eclipse's cleanup refactoring API:

```java
CleanUpRefactoring refactoring = new CleanUpRefactoring();
refactoring.addCompilationUnit(cu);

// Configure cleanups from properties
Map<String, String> options = loadCleanupOptions();
refactoring.setOptions(options);

// Perform refactoring
IStatus status = refactoring.checkFinalConditions(monitor);
if (status.isOK()) {
    Change change = refactoring.createChange(monitor);
    change.perform(monitor);
}
```

## Design Patterns

### Command Pattern

The application follows the command pattern with argument parsing and execution separated:

```
Arguments → Configuration → Execution
```

### Visitor Pattern

File processing uses a visitor-like pattern to traverse directory trees and process each Java file:

```java
void processDirectory(File dir) {
    for (File file : dir.listFiles()) {
        if (file.isDirectory()) {
            processDirectory(file);  // Recursive
        } else if (isJavaFile(file)) {
            processFile(file);
        }
    }
}
```

### Template Method Pattern

The cleanup application provides the framework (template) for applying cleanups, while specific cleanup implementations are pluggable via Eclipse's extension mechanism.

## Configuration System

### Properties File Format

Cleanup configuration uses standard Java properties format:

```properties
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.use_lambda=true
```

**Loading**:
```java
Properties props = new Properties();
try (InputStream input = new FileInputStream(configFile)) {
    props.load(input);
}
Map<String, String> cleanupOptions = new HashMap<>();
for (String key : props.stringPropertyNames()) {
    cleanupOptions.put(key, props.getProperty(key));
}
```

### Cleanup Constants

Configuration keys correspond to constants from:
- `org.eclipse.jdt.ui.cleanup.CleanUpConstants` (Core JDT)
- `MYCleanUpConstants` (Sandbox cleanups)

Example constants:
- `cleanup.format_source_code`
- `cleanup.organize_imports`
- `cleanup.remove_unused_imports`
- `cleanup.use_lambda`
- `encoding.strategy` (Sandbox)
- `USEFUNCTIONALLOOP_CLEANUP` (Sandbox)

## Workspace Requirements

### Why Workspace is Required

The Eclipse JDT API requires a workspace for several reasons:

1. **Resource Mapping**: Files must be mapped to `IFile` resources
2. **Compilation Context**: Classpath and project settings are read from workspace
3. **Dependency Resolution**: Type resolution requires project configuration
4. **Refactoring Infrastructure**: Eclipse refactoring framework requires workspace context

### Workspace Validation

The application validates workspace availability early:

```java
if (!Platform.isRunning() || ResourcesPlugin.getWorkspace() == null) {
    System.err.println("A workspace must be specified with the -data parameter");
    return EXIT_RELAUNCH;
}
```

### File Location Constraint

Only files inside the workspace can be processed:

```
Workspace: /home/user/workspace
File: /home/user/workspace/MyProject/src/Test.java ✓ Can process
File: /tmp/Test.java ✗ Cannot process (outside workspace)
```

**Rationale**: Eclipse resource model requires all files to be under workspace root.

## Error Handling

### Validation Errors

- Missing workspace (`-data` not provided)
- Configuration file not found
- No files specified
- Conflicting options (`-quiet` with `-verbose`)

**Response**: Print error to stderr, return non-zero exit code

### Processing Errors

- File outside workspace → Skip with warning
- Invalid Java file → Skip with warning
- Compilation errors → Log error, continue with next file
- Cleanup failure → Log error, continue with next file

**Philosophy**: Best-effort processing - continue even if some files fail

### Exit Codes

- `0` (IApplication.EXIT_OK): Success or help displayed
- Non-zero: Fatal error occurred

## Extension Points

### Custom Cleanups

The application automatically includes all registered cleanups:

**Registration** (in plugin.xml):
```xml
<extension point="org.eclipse.jdt.ui.cleanUps">
    <cleanUp
        class="org.sandbox.jdt.internal.corext.fix.MyCustomCleanUp"
        id="org.sandbox.jdt.cleanup.myCustomCleanup">
    </cleanUp>
</extension>
```

**Configuration** (in properties file):
```properties
my.custom.cleanup=true
my.custom.option=value
```

### Future Extensions

The architecture supports future enhancements:

- Progress reporting via callback interface
- Parallel file processing
- Dry-run mode (preview changes without applying)
- Change report generation
- Integration with version control systems

## Package Structure

```
org.sandbox.jdt.cleanup.application
├── CodeCleanupApplicationWrapper   # Entry point
└── CodeCleanupApplication          # Main logic
```

**Eclipse JDT Correspondence**:
- Similar to `org.eclipse.jdt.core.JavaCodeFormatter` application
- Uses same cleanup infrastructure as Eclipse IDE
- Can be ported by adapting to Eclipse package structure

## Build and Deployment

### Plugin Structure

- **plugin.xml**: Registers application extension point
- **META-INF/MANIFEST.MF**: Declares dependencies on Eclipse JDT and platform
- **build.properties**: Specifies build configuration

### Dependencies

Required Eclipse plugins:
- `org.eclipse.equinox.app` - Application framework
- `org.eclipse.core.runtime` - Eclipse runtime
- `org.eclipse.core.resources` - Workspace and resources
- `org.eclipse.jdt.core` - JDT core functionality
- `org.eclipse.jdt.ui` - JDT UI and cleanup framework

### Execution

The application is executed via Eclipse launcher:

```bash
eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
    -data <workspace> -config <properties> <files>
```

## Performance Considerations

### Single-Threaded Processing

Current implementation processes files sequentially:
- Simple and safe
- Avoids concurrent modification issues
- May be slow for large codebases

### Optimization Opportunities

1. **Parallel Processing**: Process multiple files concurrently
2. **Batch Compilation**: Create compilation units in batch
3. **Change Batching**: Accumulate changes and apply in single transaction
4. **Workspace Caching**: Cache workspace metadata to avoid repeated lookups

### Scalability

For large projects (1000+ files):
- Consider splitting into multiple invocations
- Use parallel shell scripts
- Configure cleanup to skip expensive operations

## Testing Strategy

### Unit Testing Challenges

Testing is challenging because:
- Requires Eclipse platform to be running
- Needs valid workspace
- Depends on Eclipse plugin infrastructure

### Integration Testing

Test via actual command-line invocation:

```bash
# Create test workspace
mkdir -p test-workspace/project/src
echo "public class Test {}" > test-workspace/project/src/Test.java

# Run application
eclipse -nosplash \
    -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
    -data test-workspace \
    -config test-config.properties \
    test-workspace/project/src/Test.java

# Verify changes
diff test-workspace/project/src/Test.java expected-Test.java
```

### Automated Testing

Use test module `sandbox_cleanup_application_test` for automated tests:
- Mock workspace setup
- Create test compilation units
- Verify cleanup application
- Check error handling

## Comparison with JavaCodeFormatter

### Similarities

Both applications:
- Run as Equinox applications
- Require workspace with `-data`
- Process files from command line
- Use Eclipse JDT APIs

### Differences

| Feature | JavaCodeFormatter | CleanupApplication |
|---------|------------------|-------------------|
| Primary Function | Code formatting only | All cleanup operations |
| Configuration | Formatter profile | Cleanup properties |
| Scope | Formatting rules | Refactoring + formatting |
| Extensibility | Fixed functionality | Plugin-based cleanups |

### Use Cases

**JavaCodeFormatter**:
- Enforce code style
- Format before commit
- Style checking in CI

**CleanupApplication**:
- Code modernization
- API migration
- Comprehensive refactoring
- Custom transformations

## Future Enhancements

### Planned Features

1. **Dry-Run Mode**: Preview changes without applying
2. **Change Report**: Generate report of all changes made
3. **Selective Application**: Apply only specific cleanup IDs
4. **Parallel Processing**: Process multiple files concurrently
5. **VCS Integration**: Integrate with Git/SVN for change tracking

### Architecture Evolution

The architecture is designed to support:
- Plugin-based output formatters (text, HTML, JSON)
- Custom progress listeners
- Pre/post-processing hooks
- Alternative configuration formats (XML, YAML)

## References

- [Eclipse Application Model](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_applications.htm)
- [JDT Cleanup Framework](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/ui/cleanup/package-summary.html)
- [Eclipse Batch Processing](https://wiki.eclipse.org/FAQ_How_do_I_run_Eclipse_headlessly%3F)
- [JavaCodeFormatter Application](https://bugs.eclipse.org/bugs/show_bug.cgi?id=75333)

## Eclipse JDT Contribution

This application demonstrates a working batch cleanup tool that could be contributed to Eclipse JDT. Before contribution:

- [ ] Generalize to work with standard Eclipse cleanups only
- [ ] Add comprehensive documentation
- [ ] Create Eclipse contributor agreement
- [ ] Submit to Eclipse Gerrit for review
- [ ] Ensure compatibility with Eclipse coding standards

The application provides value to Eclipse users who need command-line cleanup capabilities for automation and CI/CD integration.
