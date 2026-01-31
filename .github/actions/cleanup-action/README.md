# Sandbox Cleanup GitHub Action

## Overview

This directory contains a **composite GitHub Action** that wraps the **sandbox cleanup application** to enable automated Eclipse JDT code cleanup in GitHub Actions workflows.

## What This Action Does

The action:
1. Sets up Java 21 with Maven caching
2. Caches the built Eclipse product for faster subsequent runs
3. Builds the sandbox cleanup application (only on cache miss)
4. Extracts the Eclipse product (only on cache miss)
5. Runs the cleanup application on your Java source code
6. Applies all configured Eclipse JDT cleanups plus sandbox-specific cleanups

## Action Inputs

| Input | Description | Required | Default |
|-------|-------------|----------|---------|
| `config-file` | Path to cleanup configuration properties file | Yes | - |
| `source-dir` | Directory containing Java source files (relative to repo root) | No | `.` |
| `verbose` | Enable verbose output | No | `false` |
| `quiet` | Suppress all output except errors | No | `false` |

## Usage

### In Your Workflow

```yaml
- name: Run Sandbox Cleanup
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/standard.properties'
    source-dir: 'src'
    verbose: 'true'
```

### Available Cleanup Profiles

Three pre-configured cleanup profiles are provided in `.github/cleanup-profiles/`:

#### 1. Minimal (`minimal.properties`)
- Code formatting
- Import organization
- Remove unused imports

**Use when**: You want only essential formatting changes.

#### 2. Standard (`standard.properties`)
- Everything in Minimal, plus:
- Use blocks in control statements
- Remove unnecessary code (casts, unused variables)
- Add missing annotations
- Lambda conversion
- Basic sandbox cleanups

**Use when**: You want balanced code quality improvements (recommended).

#### 3. Aggressive (`aggressive.properties`)
- Everything in Standard, plus:
- Qualify static member accesses
- Remove trailing whitespace
- Enhanced for-loop conversion
- All sandbox-specific cleanups (encoding, platform helper, JUnit, etc.)

**Use when**: You want comprehensive code modernization.

## Example Workflows

### 1. Automatic PR Cleanup

See `.github/workflows/pr-auto-cleanup.yml` for a complete example.

This workflow automatically runs on PRs and commits cleanup changes back to the PR branch.

### 2. Manual Cleanup

See `.github/workflows/manual-cleanup.yml` for a complete example.

This workflow can be manually triggered with customizable options:
- Choose cleanup profile
- Select branch
- Choose source directory
- Enable/disable commit

## How It Works

### Composite Action Architecture

This is a **composite action** that runs directly in the GitHub Actions runner (no Docker build required).

**Step 1: Java Setup**
- Sets up Java 21 (Temurin distribution)
- Configures Maven caching for dependency resolution

**Step 2: Eclipse Product Caching**
- Checks cache for built Eclipse product at `/tmp/eclipse`
- Cache key based on `**/pom.xml` hashes
- Restores from cache if available (saves 10-15 minutes)

**Step 3: Install Dependencies**
- Installs Xvfb and GTK libraries for headless Eclipse
- Required for Eclipse UI components in headless mode

**Step 4: Build (Cache Miss Only)**
- Only runs if Eclipse product not in cache
- Compiles entire sandbox project with all plugins
- Uses `xvfb-run` for headless build
- Takes ~10-15 minutes on first run

**Step 5: Extract Product (Cache Miss Only)**
- Only runs if Eclipse product not in cache
- Extracts Eclipse product from `sandbox_product/target/products`
- Places product at `/tmp/eclipse`

**Step 6: Run Cleanup**
- Starts Xvfb virtual display server
- Creates Eclipse workspace directory
- Executes Eclipse cleanup application with configured options
- Processes all Java files in specified directory

### Performance

- **First run (cache miss)**: ~10-15 minutes (full Maven build)
- **Cached runs**: ~2-3 minutes (no build needed, only cleanup execution)

## Cleanup Configuration

### Creating Custom Profiles

Create a new `.properties` file in `.github/cleanup-profiles/`:

```properties
# My Custom Profile
cleanup.format_source_code=true
cleanup.organize_imports=true
# Add more cleanup options...
```

### Available Cleanup Constants

#### Eclipse JDT Core Constants

See [CleanUpConstants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java) for the complete list.

Common options:
- `cleanup.format_source_code` - Format code
- `cleanup.organize_imports` - Organize imports
- `cleanup.remove_unused_imports` - Remove unused imports
- `cleanup.use_blocks` - Add blocks to control statements
- `cleanup.remove_unnecessary_casts` - Remove unnecessary casts
- `cleanup.add_missing_override_annotations` - Add @Override
- `cleanup.use_lambda` - Convert to lambda expressions
- `cleanup.convert_functional_interfaces` - Convert functional interfaces

#### Sandbox-Specific Constants

See `sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java` for sandbox constants.

Sandbox options:
- `REMOVE_UNNECESSARY_ARRAY_CREATION` - Remove unnecessary array creation
- `USE_STRINGBUILDER` - Use StringBuilder instead of StringBuffer
- `USEFUNCTIONALLOOP_CLEANUP` - Convert loops to functional style
- `encoding.strategy` - Apply explicit UTF-8 encoding
- `PLATFORM_HELPER_CLEANUP` - Modernize Eclipse Platform API usage
- `JUNIT_CLEANUP` - Migrate JUnit 3/4 to JUnit 5

## Performance Considerations

### Build Time

- **First run (cache miss)**: ~10-15 minutes (builds entire sandbox project)
- **Cached runs**: ~2-3 minutes (Eclipse product already built)

### Optimization Strategies

1. **Eclipse product caching**: Action automatically caches the built Eclipse product
2. **Maven caching**: Maven dependencies are cached via `actions/setup-java`
3. **Process specific directories**: Use `source-dir` input to limit scope
4. **Use minimal profile**: Choose appropriate cleanup profile for your needs
5. **Conditional execution**: Only run on PRs that modify Java files

### Cache Invalidation

The Eclipse product cache is invalidated when:
- Any `pom.xml` file changes
- Manual cache clearing via GitHub Actions UI

### Resource Usage

- **Memory**: Requires ~2GB RAM for Eclipse and Maven build
- **Disk**: ~500MB for Eclipse product cache
- **CPU**: Scales with number of files to process

## Troubleshooting

### Action Fails to Build

**Symptom**: Maven build fails during action execution

**Solutions**:
- Check Java version (must be Java 21)
- Verify all dependencies are available in Maven Central/Eclipse repositories
- Check that sandbox_product builds successfully locally: `mvn clean install -DskipTests`
- Clear GitHub Actions cache if stale: Settings → Actions → Caches

### Files Not Processed

**Symptom**: "File outside workspace" warnings

**Cause**: Eclipse requires files to be in the workspace

**Solution**: This is expected behavior. The action processes files in the repository. Files outside the repository cannot be processed.

### Cleanup Produces No Changes

**Possible causes**:
1. Files are already clean according to configuration
2. Files have compilation errors
3. Configuration file not found or invalid

**Debug**:
- Enable `verbose: 'true'` in action inputs
- Check action logs for specific error messages
- Verify configuration file path is correct

### Build Takes Too Long

**Solutions**:
- Build time is only slow on first run or cache miss
- Eclipse product caching automatically speeds up subsequent runs
- Check cache status in GitHub Actions workflow logs
- Run cleanup less frequently (e.g., only on specific PR labels)
- Use more specific `source-dir` to limit files processed

## Limitations

### Fork Pull Requests

**The auto-cleanup workflow does not work for PRs from external forks.** This is a GitHub Actions security limitation:

- GitHub provides a read-only `GITHUB_TOKEN` to workflows triggered by fork PRs
- The workflow cannot push commits back to the fork repository
- This is by design to prevent malicious code execution in fork PRs

**Workarounds**:
1. Contributors can run the Manual Cleanup workflow on their fork before creating the PR
2. Maintainers can manually check out the PR branch and run cleanup locally
3. Modify the workflow to post cleanup suggestions as a PR comment instead of auto-committing

**Note**: PRs from branches within the same repository work fine.

### Workspace Boundaries

Files must be within the GitHub Actions workspace to be processed. The action validates paths to prevent access to files outside the workspace.

### Build Time

The first build takes 10-15 minutes as it compiles the entire sandbox project. The Eclipse product is automatically cached for subsequent runs, reducing runtime to 2-3 minutes.

## Advanced Usage

### Custom Eclipse Configuration

To customize the Eclipse configuration, you can:

1. Add environment variables in the cleanup step
2. Mount custom configuration files from the repository
3. Pass additional Eclipse command-line arguments

### Cache Management

To clear the Eclipse product cache:

1. Go to repository Settings → Actions → Caches
2. Find caches starting with `eclipse-product-`
3. Delete the cache to force a rebuild on next run

This is useful when:
- Testing changes to the build process
- Resolving build issues
- Major dependency updates

### Integrate with Other Tools

Combine with other actions for comprehensive code quality:

```yaml
- name: Run Sandbox Cleanup
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/standard.properties'

- name: Run SpotBugs
  run: mvn compile spotbugs:check

- name: Run Tests
  run: mvn test
```

## Files in This Directory

- `action.yml` - Composite action definition with all steps
- `README.md` - This file (usage documentation)
- `TODO.md` - Status tracking and future enhancements

## Related Documentation

- [Sandbox Cleanup Application](../../../sandbox_cleanup_application/README.md) - Command-line usage
- [Sandbox Cleanup Application Architecture](../../../sandbox_cleanup_application/ARCHITECTURE.md) - Technical details
- [GitHub Actions Documentation](https://docs.github.com/en/actions/creating-actions/creating-a-docker-container-action)

## Contributing

To improve this action:

1. **Optimize build time**: Improve caching strategies, reduce build scope
2. **Add features**: Support incremental cleanup, dry-run mode, change reports
3. **Improve documentation**: Add more examples, troubleshooting tips
4. **Test thoroughly**: Test with different configurations and edge cases

### Architecture History

This action was originally implemented as a Docker action but was converted to a composite action to resolve build context limitations. See `TODO.md` for the full migration rationale.

## License

This action is part of the sandbox project and is available under the terms of the Eclipse Public License 2.0.

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
