# GitHub Actions for Automated Code Cleanup

This directory contains GitHub Actions workflows and a custom action for automated Eclipse JDT code cleanup using the sandbox cleanup application.

## Quick Start

### For Pull Requests (Automatic)

The `pr-auto-cleanup.yml` workflow automatically runs on PRs that modify Java files and applies the "standard" cleanup profile.

**It will**:
- ✅ Format code and organize imports
- ✅ Add missing annotations
- ✅ Remove unnecessary code
- ✅ Convert to modern Java features
- ✅ Apply sandbox-specific cleanups
- ✅ Commit changes back to the PR

**To disable**: Delete or rename `.github/workflows/pr-auto-cleanup.yml`

### For Manual Cleanup

Use the `manual-cleanup.yml` workflow when you want to:
- Clean a specific branch
- Choose a different cleanup profile (minimal/standard/aggressive)
- Clean only a specific directory
- Preview changes without committing

**To run**:
1. Go to **Actions** tab in GitHub
2. Select **Manual Cleanup** workflow
3. Click **Run workflow**
4. Choose your options

## Available Workflows

### 1. Fix NLS Comments (`fix-nls.yml`)

**Triggers**: Automatically on PR opened/synchronized (when `.java` files change)
- Only runs for PRs created by `copilot[bot]`
- Or when PR is labeled with `auto-fix-nls`

**What it does**:
- Scans plugin source directories (not test modules)
- Adds missing `//$NON-NLS-n$` comments to string literals
- Commits and pushes changes if needed
- Preserves existing NLS comments (no duplicates)

**Directories processed**:
- ✅ All `sandbox_*/src/` directories (plugin modules)
- ❌ Excludes `*_test/` directories (test modules)
- ❌ Excludes `sandbox_test_commons/`, `sandbox_web/`, etc.

**Use cases**:
- GitHub Copilot creates code without NLS comments
- Automatic cleanup of Eclipse internationalization warnings
- Ensures string literals are properly marked for translation

**Manual trigger**: Add the `auto-fix-nls` label to any PR to manually trigger this workflow.

### 2. Auto PR Cleanup (`pr-auto-cleanup.yml`)

**Triggers**: Automatically on PR opened/synchronized (when `.java` files change)

**What it does**:
- Checks out PR branch
- Runs cleanup with "standard" profile
- Commits and pushes changes if any
- Adds comment to PR explaining changes

**Configuration**: Edit the workflow file to:
- Change cleanup profile: modify `config-file` parameter
- Change source directory: modify `source-dir` parameter
- Disable auto-commit: remove the commit step

### 3. Manual Cleanup (`manual-cleanup.yml`)

**Triggers**: Manual dispatch from GitHub Actions UI

**What it does**:
- Allows you to specify branch, profile, and directory
- Shows git diff of changes
- Optionally commits and pushes changes
- Provides summary in workflow output

**Options**:
- **branch**: Which branch to clean (default: current)
- **cleanup_profile**: minimal/standard/aggressive
- **source_dir**: Directory to clean (default: `.`)
- **commit_changes**: Whether to push changes (default: true)
- **verbose**: Enable verbose logging (default: true)

## Cleanup Profiles

Located in `.github/cleanup-profiles/`:

| Profile | Use Case | Changes Applied |
|---------|----------|-----------------|
| **minimal** | Conservative - only formatting | Format code, organize imports |
| **standard** | Recommended - balanced improvements | Format + annotations + basic modernization |
| **aggressive** | Comprehensive - maximum modernization | All cleanups + sandbox-specific transformations |

See individual `.properties` files for detailed configuration.

## Custom Action

The workflows use a custom Docker-based action located in `.github/actions/cleanup-action/`.

This action:
1. Builds the sandbox cleanup application with all plugins
2. Extracts the Eclipse product
3. Runs cleanup on your Java files
4. Supports all Eclipse JDT + sandbox cleanup options

**Documentation**: See `.github/actions/cleanup-action/README.md`

## Customization

### Create a Custom Cleanup Profile

1. Create `.github/cleanup-profiles/my-profile.properties`:
```properties
cleanup.format_source_code=true
cleanup.organize_imports=true
# Add your preferred cleanup options
```

2. Use it in a workflow:
```yaml
- name: Run Sandbox Cleanup
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/my-profile.properties'
```

### Modify Auto-Cleanup Behavior

Edit `.github/workflows/pr-auto-cleanup.yml`:

**Change profile**:
```yaml
config-file: '.github/cleanup-profiles/aggressive.properties'  # Instead of standard
```

**Clean only specific directory**:
```yaml
source-dir: 'src/main/java'  # Instead of '.'
```

**Disable PR comments**:
```yaml
# Comment out or remove the "Comment on PR" step
```

### Add Cleanup to Existing Workflow

Add this step to any workflow:

```yaml
- name: Checkout code
  uses: actions/checkout@v6

- name: Run cleanup
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/standard.properties'
    verbose: 'true'
    
- name: Commit changes
  run: |
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"
    git add .
    git commit -m "Apply cleanup" || echo "No changes"
    git push || echo "Nothing to push"
```

## Examples

### Example 1: Cleanup Before Release

```yaml
name: Pre-Release Cleanup
on:
  push:
    tags:
      - 'v*'

jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/cleanup-action
        with:
          config-file: '.github/cleanup-profiles/aggressive.properties'
          verbose: 'true'
      # ... create release ...
```

### Example 2: Cleanup Specific Module

```yaml
name: Module Cleanup
on:
  workflow_dispatch:
    inputs:
      module:
        description: 'Module to clean'
        required: true

jobs:
  cleanup:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/cleanup-action
        with:
          config-file: '.github/cleanup-profiles/standard.properties'
          source-dir: '${{ inputs.module }}/src'
```

### Example 3: Cleanup Check (No Commit)

```yaml
name: Cleanup Check
on: [pull_request]

jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v6
      - uses: ./.github/actions/cleanup-action
        with:
          config-file: '.github/cleanup-profiles/standard.properties'
      - name: Check if cleanup needed
        run: |
          if [ -n "$(git status --porcelain)" ]; then
            echo "::error::Code needs cleanup! Run the manual cleanup workflow."
            git diff
            exit 1
          fi
```

## FAQ

### Q: Why is the first run slow?

**A**: The Docker action builds the entire sandbox project on first run (~10-15 minutes). Subsequent runs use Docker layer caching and are faster.

**Solution**: Consider pre-building and publishing the Docker image to GitHub Container Registry (see action README).

### Q: Does this work with PRs from forks?

**A**: **No, automatic cleanup does not work for PRs from external forks.** This is a GitHub Actions security limitation. The `GITHUB_TOKEN` provided to workflows triggered by fork PRs has read-only access and cannot push commits back to the fork.

**Workarounds**:
- Contributors can run the Manual Cleanup workflow on their fork before creating the PR
- Maintainers can manually check out the PR branch and run cleanup locally
- The workflow could be modified to post cleanup suggestions as a PR comment instead of auto-committing (future enhancement)

**Note**: PRs from branches within the same repository work fine.

### Q: Can I use this action in external repositories?

**A**: Not directly - this action is specific to the sandbox repository because it builds the sandbox plugins. For external repos, you can:
1. Copy the action and modify it
2. Publish the sandbox cleanup as a standalone tool
3. Use the standard Eclipse JDT cleanup actions available on GitHub Marketplace

### Q: What if cleanup breaks my code?

**A**: 
1. Review the PR before merging - the cleanup commits are visible
2. Use the "minimal" profile for less invasive changes
3. Configure your cleanup profile to exclude problematic transformations
4. Test your code after cleanup (add test steps to workflow)

### Q: Can I apply different profiles to different directories?

**A**: Yes! Run the action multiple times:

```yaml
- name: Cleanup core (aggressive)
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/aggressive.properties'
    source-dir: 'core/src'

- name: Cleanup legacy (minimal)
  uses: ./.github/actions/cleanup-action
  with:
    config-file: '.github/cleanup-profiles/minimal.properties'
    source-dir: 'legacy/src'
```

### Q: How do I disable auto-cleanup temporarily?

**A**: 
1. **For one PR**: Add `[skip cleanup]` to the PR title or description (requires workflow modification)
2. **For all PRs**: Disable the workflow in GitHub UI (Actions → Auto PR Cleanup → ⋯ → Disable workflow)
3. **Permanently**: Delete `.github/workflows/pr-auto-cleanup.yml`

### Q: What does the NLS fix workflow do?

**A**: The NLS (Non-Localized String) fix workflow automatically adds `//$NON-NLS-n$` comments to string literals in Java files. This is required by Eclipse to suppress warnings about strings that should not be internationalized.

**How it works**:
- Scans only plugin source directories (`sandbox_*/src/`)
- Skips test modules (directories ending with `_test`)
- Counts string literals on each line
- Adds sequential NLS comments (e.g., `//$NON-NLS-1$ //$NON-NLS-2$`)
- Preserves existing NLS comments (no duplicates)

**When it runs**:
- Automatically for PRs created by GitHub Copilot
- Manually by adding the `auto-fix-nls` label to any PR

**Example**:
```java
// Before:
return "Hello World";

// After:
return "Hello World"; //$NON-NLS-1$
```

## Troubleshooting

See `.github/actions/cleanup-action/README.md` for detailed troubleshooting.

**Common issues**:
- **Build failures**: Check Java version, Maven dependencies
- **Files not processed**: Files must be in repository
- **No changes**: Code might already be clean, or config might be invalid
- **Slow performance**: Use Docker caching or pre-built images

## Contributing

Improvements welcome:
- Optimize build time
- Add more cleanup profiles
- Improve error handling
- Add dry-run mode
- Support incremental cleanup

## Related Documentation

- [Sandbox Cleanup Application](../../sandbox_cleanup_application/README.md)
- [Custom Action README](./../actions/cleanup-action/README.md)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## License

Eclipse Public License 2.0

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
