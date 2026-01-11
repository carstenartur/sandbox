# Sandbox Cleanup GitHub Action

## Overview

This directory contains a Docker-based GitHub Action that wraps the **sandbox cleanup application** to enable automated Eclipse JDT code cleanup in GitHub Actions workflows.

## What This Action Does

The action:
1. Builds the sandbox cleanup application with all custom cleanup plugins
2. Extracts the Eclipse product
3. Runs the cleanup application on your Java source code
4. Applies all configured Eclipse JDT cleanups plus sandbox-specific cleanups

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

### Docker Build Process

1. **Base Image**: Uses `eclipse/eclipse-temurin:21-jdk` with Java 21
2. **Install Dependencies**: Installs Xvfb, Maven, and required libraries
3. **Build Sandbox**: Compiles the entire sandbox project with all plugins
4. **Extract Product**: Extracts the Eclipse product from `sandbox_product/target/products`
5. **Configure**: Sets up environment variables and entrypoint script

### Runtime Process

1. **Start Xvfb**: Starts virtual display server for headless Eclipse
2. **Prepare Workspace**: Creates Eclipse workspace directory
3. **Run Cleanup**: Executes Eclipse cleanup application with configured options
4. **Process Files**: Applies cleanups to all Java files in specified directory

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

- **First run**: ~10-15 minutes (builds entire sandbox project)
- **Subsequent runs**: Uses Docker layer caching, but still requires building

### Optimization Strategies

1. **Cache Docker layers**: GitHub Actions automatically caches Docker layers between runs
2. **Process specific directories**: Use `source-dir` input to limit scope
3. **Use minimal profile**: Choose appropriate cleanup profile for your needs
4. **Conditional execution**: Only run on PRs that modify Java files

### Resource Usage

- **Memory**: Requires ~2GB RAM for Eclipse
- **Disk**: ~1GB for Docker image
- **CPU**: Scales with number of files

## Troubleshooting

### Action Fails to Build

**Symptom**: Docker build fails during Maven compilation

**Solutions**:
- Check Java version (must be Java 21)
- Verify all dependencies are available in Maven Central/Eclipse repositories
- Check sandbox_product builds successfully locally

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

### Docker Build Takes Too Long

**Solutions**:
- Use GitHub's Docker layer caching (automatic)
- Consider pre-building and publishing the Docker image to GitHub Container Registry
- Run cleanup less frequently (e.g., only on specific PR labels)

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

The first Docker build takes 10-15 minutes as it compiles the entire sandbox project. Consider pre-building the Docker image for production use.

## Advanced Usage

### Pre-built Docker Image

To speed up workflow execution, you can pre-build and publish the Docker image:

1. Build and tag the image:
```bash
docker build -t ghcr.io/your-org/sandbox-cleanup:latest .github/actions/cleanup-action
```

2. Push to GitHub Container Registry:
```bash
docker push ghcr.io/your-org/sandbox-cleanup:latest
```

3. Update `action.yml` to use the published image:
```yaml
runs:
  using: 'docker'
  image: 'docker://ghcr.io/your-org/sandbox-cleanup:latest'
```

### Custom Eclipse Configuration

To customize the Eclipse configuration:

1. Modify `Dockerfile` to copy custom configuration files
2. Set environment variables for Eclipse preferences
3. Mount configuration from workflow using Docker volumes (advanced)

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

- `Dockerfile` - Docker image definition
- `entrypoint.sh` - Container entrypoint script
- `action.yml` - GitHub Action metadata and inputs
- `README.md` - This file

## Related Documentation

- [Sandbox Cleanup Application](../../../sandbox_cleanup_application/README.md) - Command-line usage
- [Sandbox Cleanup Application Architecture](../../../sandbox_cleanup_application/ARCHITECTURE.md) - Technical details
- [GitHub Actions Documentation](https://docs.github.com/en/actions/creating-actions/creating-a-docker-container-action)

## Contributing

To improve this action:

1. **Optimize build time**: Reduce Docker image size, cache more effectively
2. **Add features**: Support incremental cleanup, dry-run mode, change reports
3. **Improve documentation**: Add more examples, troubleshooting tips
4. **Test thoroughly**: Test with different configurations and edge cases

## License

This action is part of the sandbox project and is available under the terms of the Eclipse Public License 2.0.

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
