# Sandbox Cleanup Application

Command-line application for running Eclipse JDT code cleanups on Java source files from the command line.

## Overview

The Sandbox Cleanup Application is an Equinox application that allows you to run Eclipse JDT cleanup operations on Java files without launching the full Eclipse IDE. It's similar to the Eclipse Java Code Formatter command-line tool.

This application processes Java source files using configured cleanup rules, applying transformations such as:
- Organizing imports
- Formatting source code
- Removing unused imports
- Applying custom cleanups from the sandbox plugins

## Prerequisites

- **Eclipse Platform**: Eclipse with Equinox framework (2025-09 or later recommended)
- **Workspace**: A valid Eclipse workspace must be specified with `-data` parameter
- **Java Version**: Java 21 or later
- **Configuration File**: A properties file containing cleanup configuration options

## Application ID

```
sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup
```

## Command-Line Syntax

```bash
eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup -data <workspace> [OPTIONS] <files or directories>
```

### Required Arguments

- `-data <workspace>`: Path to Eclipse workspace directory (required by Equinox)
- `<files or directories>`: One or more Java files or directories to process

### Optional Arguments

| Option | Description |
|--------|-------------|
| `-config <file>` | Path to properties file containing cleanup configuration |
| `-quiet` | Only print error messages (suppresses progress information) |
| `-verbose` | Print detailed progress information for each file processed |
| `-help` | Display usage information and exit |

### Constraints

- `-quiet` and `-verbose` are mutually exclusive
- At least one file or directory must be specified
- Files must be inside the workspace (files outside workspace are skipped)
- Only Java-like files (`.java`, `.jav`) are processed

## Cleanup Configuration File

The cleanup configuration is specified using a standard Java properties file containing keys from Eclipse JDT's cleanup constants.

### Sample Configuration File

Create a file named `cleanup-config.properties`:

```properties
# Core Cleanup Options
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.remove_unused_imports=true

# Code Style
cleanup.use_blocks=true
cleanup.use_blocks_only_for_return_and_throw=false
cleanup.always_use_blocks=true

# Member Accesses
cleanup.qualify_static_field_accesses_with_declaring_class=true
cleanup.qualify_static_method_accesses_with_declaring_class=true
cleanup.qualify_static_member_accesses_through_subtypes_with_declaring_class=true

# Unnecessary Code
cleanup.remove_unnecessary_casts=true
cleanup.remove_unused_private_members=true
cleanup.remove_unused_local_variables=true

# Missing Code
cleanup.add_missing_override_annotations=true
cleanup.add_missing_override_annotations_interface_methods=true
cleanup.add_missing_deprecated_annotations=true

# String Operations
cleanup.use_string_is_blank=true
cleanup.simplify_boolean_return=true

# Variable Declarations
cleanup.use_var=false
cleanup.convert_functional_interfaces=true

# Java Feature Modernization
cleanup.use_lambda=true
cleanup.use_anonymous_class_creation=false
```

### Available Cleanup Constants

The cleanup configuration uses constants from `org.eclipse.jdt.ui.cleanup.CleanUpConstants`. Common options include:

| Constant | Description | Values |
|----------|-------------|--------|
| `cleanup.format_source_code` | Format source code | `true`/`false` |
| `cleanup.organize_imports` | Organize import statements | `true`/`false` |
| `cleanup.remove_unused_imports` | Remove unused imports | `true`/`false` |
| `cleanup.remove_unnecessary_casts` | Remove unnecessary casts | `true`/`false` |
| `cleanup.add_missing_override_annotations` | Add @Override annotations | `true`/`false` |
| `cleanup.use_blocks` | Use blocks in if/while/for/do statements | `true`/`false` |
| `cleanup.use_lambda` | Convert to lambda where possible | `true`/`false` |

For a complete list, see: [Eclipse JDT CleanUpConstants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)

## Usage Examples

### Example 1: Single File with Configuration

Clean up a single Java file using a specific configuration:

```bash
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data /path/to/workspace \
  -config cleanup-config.properties \
  MyClass.java
```

### Example 2: Directory Tree with Verbose Output

Process all Java files in a directory tree with verbose logging:

```bash
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data /path/to/workspace \
  -config cleanup-config.properties \
  -verbose \
  src/main/java
```

### Example 3: Multiple Files with Quiet Mode

Process specific files silently (only show errors):

```bash
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data /path/to/workspace \
  -config cleanup-config.properties \
  -quiet \
  FileA.java FileB.java FileC.java
```

### Example 4: Display Help

Show usage information:

```bash
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -help
```

### Example 5: Full Project Cleanup

Clean up an entire project directory:

```bash
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data /home/user/workspace \
  -config /home/user/my-cleanups.properties \
  /home/user/workspace/MyProject/src
```

## Exit Behavior

The application returns standard exit codes:

| Exit Code | Meaning |
|-----------|---------|
| `0` (`IApplication.EXIT_OK`) | Success - all files processed successfully, or help displayed |
| `1` or non-zero | Error occurred (missing config, invalid arguments, workspace error) |

### Exit Conditions

- **EXIT_OK (0)**:
  - `-help` flag was provided
  - All files processed successfully (or skipped if outside workspace)
  - No fatal errors occurred

- **Non-zero**:
  - Workspace not specified or invalid
  - Configuration file not found or invalid
  - No files specified
  - Conflicting options (`-quiet` and `-verbose` together)

## File Processing Behavior

### Files Inside Workspace

Only files located inside the specified workspace directory are processed:

```
Workspace: /home/user/workspace
File: /home/user/workspace/MyProject/src/Test.java ✓ Processed
File: /home/user/other/Test.java ✗ Skipped (outside workspace)
```

### Java File Detection

The application only processes files with Java-like extensions:
- `.java` - Standard Java source files
- `.jav` - Alternative Java extension

Other files in the directory are ignored.

## Log Output

### Verbose Mode (`-verbose`)

```
Using configuration file: cleanup-config.properties
Starting cleanup...
Cleaning up /home/user/workspace/MyProject/src/Test.java
Cleaning up /home/user/workspace/MyProject/src/Helper.java
Cleanup done.
```

### Normal Mode (default)

```
Using configuration file: cleanup-config.properties
Starting cleanup...
Cleanup done.
```

### Quiet Mode (`-quiet`)

```
(No output unless errors occur)
```

### Error Messages

Error messages are always printed to stderr:

```
Skipping file outside workspace: /tmp/Test.java
Fatal error during cleanup of MyClass.java: Compilation unit cannot be resolved
```

## Workspace Requirement

The application requires a valid Eclipse workspace for several reasons:

1. **Resource Resolution**: Java files must be mapped to workspace `IFile` resources
2. **Compilation Units**: JDT requires workspace context to create `ICompilationUnit` instances
3. **Dependency Resolution**: Classpath and project settings are read from workspace metadata
4. **Cleanup Registry**: Registered cleanups are loaded from the Eclipse plugin registry

### Creating a Workspace

If you don't have an existing workspace, create an empty one:

```bash
mkdir -p /tmp/cleanup-workspace
eclipse -nosplash \
  -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data /tmp/cleanup-workspace \
  -config cleanup.properties \
  /tmp/cleanup-workspace/src/Test.java
```

**Note**: Files must be located inside the workspace directory tree to be processed.

## Troubleshooting

### "A workspace must be specified with the -data parameter"

**Cause**: The `-data` argument was not provided or points to an invalid location.

**Solution**: Always provide `-data <workspace>` before other arguments.

### "File doesn't exist" or "Try to enter the full path"

**Cause**: Specified file path is invalid or relative path cannot be resolved.

**Solution**: Use absolute paths for files and directories.

### "No configuration file specified"

**Cause**: The `-config` option was provided without a file path, or path is in wrong position.

**Solution**: Ensure `-config` is followed by a valid file path: `-config cleanup.properties`

### "Cannot use -quiet with -verbose"

**Cause**: Both `-quiet` and `-verbose` were specified.

**Solution**: Choose one mode or neither (normal mode).

### "Skipping file outside workspace"

**Cause**: The file is not located inside the workspace directory.

**Solution**: 
- Move files into workspace, or
- Use a workspace that contains the files, or
- Create symbolic links inside workspace pointing to the files

### "Fatal error during cleanup"

**Cause**: The cleanup process encountered an unrecoverable error (compilation errors, refactoring failures).

**Solution**: Check that:
- File is valid Java source
- File is in a proper project structure with classpath
- No severe compilation errors exist

## Advanced Usage

### Batch Processing with Shell Scripts

Create a shell script to process multiple projects:

```bash
#!/bin/bash
WORKSPACE="/home/user/workspace"
CONFIG="/home/user/cleanup-config.properties"
ECLIPSE="/opt/eclipse/eclipse"

for project in ProjectA ProjectB ProjectC; do
  echo "Processing $project..."
  $ECLIPSE -nosplash \
    -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
    -data $WORKSPACE \
    -config $CONFIG \
    -verbose \
    $WORKSPACE/$project/src
done
```

### Integration with CI/CD

Use in continuous integration pipelines:

```yaml
# Example GitHub Actions workflow
- name: Run Java Cleanup
  run: |
    eclipse -nosplash \
      -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
      -data $GITHUB_WORKSPACE \
      -config .github/cleanup-config.properties \
      -quiet \
      src/main/java
```

### Custom Cleanup Profiles

Create different configurations for different scenarios:

```bash
# Minimal cleanup (formatting only)
eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data workspace -config minimal.properties src/

# Aggressive cleanup (all transformations)
eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data workspace -config aggressive.properties src/

# Code style enforcement
eclipse -nosplash -application sandbox_cleanup_application.org.sandbox.jdt.core.JavaCleanup \
  -data workspace -config style.properties src/
```

## Implementation Details

### Application Classes

- **`CodeCleanupApplicationWrapper`**: Entry point that validates workspace availability
- **`CodeCleanupApplication`**: Main application logic, argument parsing, and file processing

### Processing Flow

1. Parse command-line arguments
2. Validate workspace availability
3. Load cleanup configuration from properties file
4. For each file/directory:
   - Recursively process directories
   - Skip non-Java files
   - Map file to workspace `IFile` resource
   - Create `ICompilationUnit`
   - Apply cleanup refactoring
   - Save changes

### Cleanup Registry

The application uses all registered cleanups from the Eclipse plugin registry:
- Core JDT cleanups
- Sandbox custom cleanups (encoding, platform helper, functional converter, etc.)
- Any other installed cleanup plugins

## Related Documentation

- [Eclipse JDT Cleanup Framework](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/ui/cleanup/package-summary.html)
- [Equinox Applications](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/runtime_applications.htm)
- [Eclipse Batch Processing](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/product_running.htm)

## License

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0/

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
