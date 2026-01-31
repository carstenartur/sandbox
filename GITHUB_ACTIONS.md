# GitHub Actions Integration for Automated Code Cleanup

## Overview

This repository includes a **Docker-based GitHub Action** that provides automated Eclipse JDT code cleanup using the sandbox cleanup application. This allows you to apply configurable code cleanups directly in your GitHub workflows.

## Quick Start

### Automatic Cleanup on Pull Requests

The workflow is already configured! When you open or update a PR with Java file changes, the `pr-auto-cleanup.yml` workflow will:
1. Automatically apply code cleanups
2. Commit changes back to your PR
3. Add a comment explaining what was cleaned

### Manual Cleanup

Run cleanup manually from the GitHub Actions UI:
1. Go to **Actions** → **Manual Cleanup**
2. Click **Run workflow**
3. Choose your options (profile, branch, directory)
4. Click **Run workflow**

## Features

✅ **Automated PR Cleanup** - Runs automatically on pull requests  
✅ **Configurable Profiles** - Choose minimal, standard, or aggressive cleanup  
✅ **Uses Sandbox Application** - All sandbox-specific cleanups included  
✅ **Auto-Commit** - Changes automatically committed to PR branch  
✅ **Manual Trigger** - Run on-demand with customizable options  
✅ **Docker-Based** - Isolated environment with all dependencies  

## What Gets Cleaned Up

Depending on the profile chosen, cleanups include:

### Core Eclipse JDT Cleanups
- Code formatting and import organization
- Add missing @Override and @Deprecated annotations
- Remove unnecessary code (casts, unused variables, redundant semicolons)
- Use blocks in control statements
- Convert to lambda expressions and functional interfaces
- Simplify boolean expressions
- Use String.isBlank() where applicable

### Sandbox-Specific Cleanups
- **Encoding**: Replace platform-dependent encoding with explicit UTF-8
- **Platform Helper**: Modernize Eclipse Platform API usage (Status.error(), etc.)
- **Functional Converter**: Convert imperative loops to functional style (streams, forEach)
- **JUnit Migration**: Migrate JUnit 3/4 tests to JUnit 5
- **Array Creation**: Remove unnecessary array creation
- **StringBuilder**: Use StringBuilder instead of StringBuffer where appropriate

## Available Cleanup Profiles

Located in `.github/cleanup-profiles/`:

| Profile | Description | Use Case |
|---------|-------------|----------|
| **minimal** | Formatting and imports only | Conservative changes, minimal impact |
| **standard** | Balanced improvements (recommended) | General code quality improvements |
| **aggressive** | All cleanups + full modernization | Comprehensive code transformation |

## Documentation

- **[Workflows README](.github/workflows/README.md)** - How to use the workflows, examples, customization
- **[Action README](.github/actions/cleanup-action/README.md)** - Docker action details, troubleshooting
- **[Sandbox Cleanup Application](sandbox_cleanup_application/README.md)** - Command-line tool documentation

## File Structure

```
.github/
├── actions/
│   └── cleanup-action/           # Docker-based GitHub Action
│       ├── Dockerfile            # Builds sandbox + Eclipse product
│       ├── entrypoint.sh         # Runs cleanup application
│       ├── action.yml            # Action definition
│       └── README.md             # Action documentation
├── cleanup-profiles/             # Cleanup configurations
│   ├── minimal.properties
│   ├── standard.properties
│   └── aggressive.properties
└── workflows/                    # GitHub Actions workflows
    ├── pr-auto-cleanup.yml       # Auto cleanup on PRs
    ├── manual-cleanup.yml        # Manual cleanup trigger
    └── README.md                 # Workflows guide
```

## Usage Examples

### Use in Your Own Workflow

```yaml
name: My Custom Cleanup
on: [push]

jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      
      - name: Run Sandbox Cleanup
        uses: ./.github/actions/cleanup-action
        with:
          config-file: '.github/cleanup-profiles/standard.properties'
          source-dir: 'src'
          verbose: 'true'
      
      - name: Commit changes
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add .
          git commit -m "Apply cleanup" || echo "No changes"
          git push
```

### Create Custom Profile

Create `.github/cleanup-profiles/my-profile.properties`:

```properties
# Your custom cleanup configuration
cleanup.format_source_code=true
cleanup.organize_imports=true
cleanup.use_lambda=true
# Add more options as needed
```

Then use it:

```yaml
- uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/my-profile.properties'
```

## How It Works

### Build Phase (Docker)
1. Uses Java 21 base image
2. Installs Xvfb and dependencies for headless Eclipse
3. Builds the entire sandbox project with Maven/Tycho
4. Extracts Eclipse product from `sandbox_product/target/products`
5. Packages everything in a Docker image

### Runtime Phase
1. Starts virtual display (Xvfb) for Eclipse UI components
2. Creates Eclipse workspace
3. Runs sandbox cleanup application with specified configuration
4. Processes all Java files in specified directory
5. Applies all enabled cleanup transformations

## Performance Notes

- **First Run**: ~10-15 minutes (builds entire sandbox project)
- **Subsequent Runs**: Faster due to Docker layer caching
- **Optimization**: Consider pre-building and publishing Docker image to GitHub Container Registry

## Troubleshooting

### Build Failures
**Issue**: Docker build fails during Maven compilation

**Solution**: 
- Check Java version (must be Java 21)
- Verify Maven dependencies are accessible
- Check build logs for specific errors

### No Changes Applied
**Issue**: Cleanup runs but no files are modified

**Possible Causes**:
- Files already comply with configuration
- Files have compilation errors
- Configuration file path incorrect

**Debug**: Enable `verbose: 'true'` and check action logs

### Workflow Takes Too Long
**Solutions**:
- Use minimal profile for faster execution
- Limit to specific directories with `source-dir`
- Pre-build and publish Docker image
- Run only on specific file patterns

See the [Action README](.github/actions/cleanup-action/README.md) for detailed troubleshooting.

## Customization

### Change Auto-Cleanup Profile

Edit `.github/workflows/pr-auto-cleanup.yml`:

```yaml
- uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/aggressive.properties'  # Change here
```

### Disable Auto-Cleanup

Option 1: Delete `.github/workflows/pr-auto-cleanup.yml`  
Option 2: Disable workflow in GitHub UI (Actions → Auto PR Cleanup → ⋯ → Disable)

### Clean Specific Directories Only

```yaml
- uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/standard.properties'
    source-dir: 'src/main/java'  # Only clean this directory
```

## Comparison to Command-Line Tool

| Feature | GitHub Action | Command-Line Tool |
|---------|--------------|-------------------|
| **Integration** | Automated in CI/CD | Manual execution |
| **Setup** | Docker-based, self-contained | Requires Eclipse installation |
| **Configuration** | Properties files | Properties files |
| **Cleanups** | All sandbox + JDT cleanups | All sandbox + JDT cleanups |
| **Use Case** | Automated PR cleanup | Local development, scripts |

Both use the same underlying **sandbox cleanup application**.

## Contributing

Improvements welcome:
- Optimize Docker build time
- Add more cleanup profiles
- Support incremental cleanup (changed files only)
- Add dry-run mode for previewing changes
- Improve error handling and reporting

## Related Documentation

- **[Sandbox Cleanup Application](sandbox_cleanup_application/README.md)** - Command-line tool
- **[Sandbox Cleanup Architecture](sandbox_cleanup_application/ARCHITECTURE.md)** - Technical details
- **[Main README](README.md)** - Project overview
- **[GitHub Actions Docs](https://docs.github.com/en/actions)** - GitHub Actions documentation

## License

Eclipse Public License 2.0 (EPL-2.0)

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

---

**Need Help?** Check the [Workflows README](.github/workflows/README.md) for more examples and FAQs.
