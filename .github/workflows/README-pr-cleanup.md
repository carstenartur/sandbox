# PR Code Cleanup GitHub Action

## Overview

The **PR Code Cleanup** GitHub Action automatically applies Eclipse JDT cleanup transformations to pull request code. It provides the same powerful code cleanup capabilities as the [sandbox cleanup application](../../sandbox_cleanup_application/README.md) but integrated directly into your GitHub workflow.

## Features

- 🤖 **Automatic cleanup** on pull request events (opened, synchronized, reopened)
- ⚙️ **Configurable cleanup rules** via properties file
- 🔄 **Auto-commit** changes back to the PR branch
- 💬 **PR comments** notifying about applied cleanups
- 🎯 **Selective application** - only processes Java files
- 🚀 **Modern Java features** - converts legacy code to use lambdas, streams, etc.
- 🧹 **Code quality** - removes unnecessary code, adds missing annotations
- 📐 **Formatting** - ensures consistent code style

## How It Works

1. **Trigger**: The action runs when a PR is opened, updated, or manually triggered
2. **Build**: Builds the sandbox cleanup application with all cleanup plugins
3. **Extract**: Extracts the Eclipse product with all required dependencies
4. **Configure**: Loads cleanup configuration from `cleanup-config.properties`
5. **Process**: Runs Eclipse JDT cleanup on all Java files in the repository
6. **Commit**: If changes are detected, commits and pushes them to the PR branch
7. **Notify**: Adds a comment to the PR explaining what was cleaned up

## Usage

### Enable for Your Repository

The action is already configured in `.github/workflows/pr-code-cleanup.yml` and will automatically run on pull requests to the `main` branch.

### Manual Trigger

You can also manually trigger the cleanup action from the GitHub Actions UI:

1. Go to **Actions** tab in your repository
2. Select **PR Code Cleanup** workflow
3. Click **Run workflow**
4. Optionally specify a PR number

### Customize Cleanup Configuration

The cleanup rules are defined in the workflow file under the "Configure cleanup settings" step. To customize:

1. Edit `.github/workflows/pr-code-cleanup.yml`
2. Modify the `cleanup-config.properties` content in the configuration step
3. Add or remove cleanup options as needed

### Available Cleanup Options

The action supports all Eclipse JDT cleanup constants plus sandbox-specific cleanups:

#### Core Eclipse Cleanups

```properties
# Formatting
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.remove_unused_imports=true

# Code Style
cleanup.use_blocks=true
cleanup.always_use_blocks=true

# Member Accesses
cleanup.qualify_static_field_accesses_with_declaring_class=true
cleanup.qualify_static_method_accesses_with_declaring_class=true

# Unnecessary Code
cleanup.remove_unnecessary_casts=true
cleanup.remove_unused_private_members=true
cleanup.remove_unused_local_variables=true
cleanup.remove_redundant_type_arguments=true

# Missing Code
cleanup.add_missing_override_annotations=true
cleanup.add_missing_deprecated_annotations=true

# Modernization
cleanup.use_lambda=true
cleanup.convert_functional_interfaces=true
cleanup.simplify_boolean_return=true
cleanup.use_string_is_blank=true
```

#### Sandbox-Specific Cleanups

```properties
# Array creation optimization
REMOVE_UNNECESSARY_ARRAY_CREATION=true

# StringBuilder usage
USE_STRINGBUILDER=true

# Functional loop conversion
USEFUNCTIONALLOOP_CLEANUP=true

# Encoding cleanups (UTF-8)
encoding.strategy=true

# Platform helper cleanups
PLATFORM_HELPER_CLEANUP=true

# JUnit migration (JUnit 3/4 → JUnit 5)
JUNIT_CLEANUP=true
```

For a complete list of available cleanup options, see:
- [Eclipse JDT CleanUpConstants](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/master/org.eclipse.jdt.core.manipulation/core%20extension/org/eclipse/jdt/internal/corext/fix/CleanUpConstants.java)
- [Sandbox MYCleanUpConstants](../../sandbox_common/src/org/sandbox/jdt/internal/corext/fix2/MYCleanUpConstants.java)

## Configuration Examples

### Minimal Cleanup (Formatting Only)

```properties
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.remove_unused_imports=true
```

### Aggressive Modernization

```properties
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.use_lambda=true
cleanup.convert_functional_interfaces=true
cleanup.use_string_is_blank=true
USEFUNCTIONALLOOP_CLEANUP=true
USE_STRINGBUILDER=true
```

### Code Quality Focus

```properties
cleanup.remove_unnecessary_casts=true
cleanup.remove_unused_private_members=true
cleanup.remove_unused_local_variables=true
cleanup.add_missing_override_annotations=true
cleanup.add_missing_deprecated_annotations=true
REMOVE_UNNECESSARY_ARRAY_CREATION=true
```

## Workflow Triggers

### Automatic Triggers

The action automatically runs on:
- Pull request opened
- Pull request synchronized (new commits pushed)
- Pull request reopened

```yaml
on:
  pull_request:
    types: [opened, synchronize, reopened]
    branches: [main]
```

### Manual Trigger

```yaml
on:
  workflow_dispatch:
    inputs:
      pr_number:
        description: 'PR number to apply cleanups to'
        required: false
        type: number
```

## Permissions

The action requires the following permissions:

```yaml
permissions:
  contents: write      # To commit and push changes
  pull-requests: write # To add comments to PRs
```

## Troubleshooting

### Action Fails to Build

**Issue**: Maven build fails during cleanup application build.

**Solution**: 
- Check Java version (must be Java 21)
- Verify all dependencies are available
- Check the build logs for specific errors

### No Changes Committed

**Issue**: Cleanup runs but no changes are committed.

**Possible causes**:
1. No Java files found (excludes target/, .git/, bin/, build/)
2. Files are already clean according to the configuration
3. Files have compilation errors preventing cleanup

**Debug**:
- Check the "Find Java source files" step output
- Review verbose cleanup logs
- Verify cleanup configuration is correct

### Files Outside Workspace Not Processed

**Issue**: Some files are skipped with "outside workspace" message.

**Explanation**: Eclipse JDT requires files to be inside the workspace directory. The action uses `GITHUB_WORKSPACE` as the workspace, which includes all repository files.

**Solution**: This is usually not an issue. If specific files are being skipped, check if they're in excluded directories (target/, bin/, etc.)

### Eclipse Product Not Found

**Issue**: "Eclipse executable not found" error.

**Solution**: 
- Ensure the product build completes successfully
- Check that `sandbox_product` builds correctly
- Verify the product extraction step finds the Eclipse archive

### Cleanup Application Returns Errors

**Issue**: Cleanup application exits with errors.

**Expected behavior**: The workflow continues even if some files couldn't be cleaned (compilation errors, etc.). Check the logs to see which files were processed successfully.

## Limitations

1. **Workspace Requirement**: Files must be mappable to Eclipse workspace resources. The action uses the GitHub workspace as the Eclipse workspace.

2. **Build Time**: Building the Eclipse product takes time (~5-10 minutes). Consider running the action only when needed.

3. **Java Files Only**: Only processes `.java` and `.jav` files.

4. **Compilation Context**: Cleanups that require type resolution may not work if files have compilation errors or missing dependencies.

5. **Large Repositories**: Processing many files can take significant time. Consider limiting to changed files only.

## Performance Considerations

### Optimization Strategies

1. **Incremental Cleanup**: Modify the workflow to only process changed files:
```bash
# Get changed Java files
git diff --name-only origin/main...HEAD | grep '.java$' > /tmp/changed_files.txt
```

2. **Conditional Execution**: Only run on specific paths:
```yaml
on:
  pull_request:
    paths:
      - '**.java'
      - 'src/**'
```

3. **Caching**: The workflow uses Maven dependency caching to speed up builds.

## Integration with CI/CD

### Running Before Tests

To ensure cleanups are applied before running tests, configure the workflow to run early:

```yaml
jobs:
  cleanup:
    runs-on: ubuntu-latest
    # ... cleanup steps ...
    
  test:
    needs: cleanup
    runs-on: ubuntu-latest
    # ... test steps ...
```

### Preventing Conflicts

If multiple people push to the same PR, the action may create merge conflicts. Consider:
- Running cleanup on a schedule instead of every push
- Requiring clean code before PR creation
- Using branch protection rules

## Advanced Usage

### Custom Eclipse Configuration

To use a custom Eclipse configuration or different cleanup plugins:

1. Modify the build step to include additional plugins
2. Update the product configuration in `sandbox_product/sandbox.product`
3. Add custom cleanup rules to the configuration

### External Configuration File

Instead of embedding configuration in the workflow, use an external file:

```yaml
- name: Load cleanup configuration
  run: |
    cp .github/cleanup-profiles/aggressive.properties cleanup-config.properties
```

Then create configuration profiles in `.github/cleanup-profiles/`.

### Conditional Cleanup

Apply different cleanup rules based on PR labels:

```yaml
- name: Choose cleanup profile
  run: |
    if [[ "${{ contains(github.event.pull_request.labels.*.name, 'major-refactor') }}" == "true" ]]; then
      echo "Using aggressive cleanup profile"
      cp .github/cleanup-profiles/aggressive.properties cleanup-config.properties
    else
      echo "Using minimal cleanup profile"
      cp .github/cleanup-profiles/minimal.properties cleanup-config.properties
    fi
```

## Security Considerations

- **Token Permissions**: The action uses `GITHUB_TOKEN` with write permissions. This is necessary to commit changes.
- **Untrusted PRs**: The action runs on PR events, including from forks. GitHub Actions automatically restricts permissions for fork PRs.
- **Code Execution**: The cleanup process only applies automated refactorings and doesn't execute arbitrary code.

## Comparison with Other Tools

### vs. Spotless / google-java-format

| Feature | PR Cleanup Action | Spotless/google-java-format |
|---------|------------------|----------------------------|
| Formatting | ✅ Yes | ✅ Yes |
| Refactoring | ✅ Yes (extensive) | ❌ No |
| Type resolution | ✅ Yes | ❌ No |
| Lambda conversion | ✅ Yes | ❌ No |
| API migration | ✅ Yes | ❌ No |
| Setup complexity | Higher | Lower |

**Use PR Cleanup Action when**: You need comprehensive code modernization and refactoring.

**Use Spotless when**: You only need consistent formatting.

## Examples

### Example PR Comment

When the action applies cleanups, it adds a comment like:

> 🤖 **Automated Code Cleanup Applied**
>
> The Eclipse JDT cleanup transformations have been automatically applied to this PR. The cleanup includes:
> - ✨ Code formatting and import organization
> - 📝 Adding missing annotations (@Override, @Deprecated)
> - 🧹 Removing unnecessary code (casts, unused variables)
> - 🚀 Modernizing code (lambdas, functional interfaces)
> - 💎 Code style improvements
>
> Please review the changes and ensure they meet your expectations.

### Example Commit Message

```
Apply automated Eclipse JDT cleanup transformations

This commit applies the following cleanup operations:
- Code formatting and import organization
- Adding missing @Override and @Deprecated annotations
- Removing unnecessary code (casts, unused variables, etc.)
- Modernizing code (lambda expressions, functional interfaces)
- Applying code style improvements (blocks in control statements)

Cleanup applied by GitHub Action using sandbox cleanup application.
```

## Related Documentation

- [Sandbox Cleanup Application](../../sandbox_cleanup_application/README.md) - Command-line cleanup tool
- [Sandbox Cleanup Application Architecture](../../sandbox_cleanup_application/ARCHITECTURE.md) - Technical details
- [Eclipse JDT Cleanup Framework](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/org/eclipse/jdt/ui/cleanup/package-summary.html)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## Contributing

To improve this action:

1. Test with different cleanup configurations
2. Optimize build and execution time
3. Add support for incremental cleanup (only changed files)
4. Improve error handling and reporting
5. Add dry-run mode for preview

## License

This GitHub Action is part of the sandbox project and is available under the terms of the Eclipse Public License 2.0.

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
