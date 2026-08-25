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

### Release Workflow (`deploy-release.yml`)

**Triggers**: Manual dispatch from GitHub Actions UI

**What it does**:
- Performs comprehensive preflight validation checks
- Sets release version in all modules (pom.xml, MANIFEST.MF, feature.xml)
- Builds and verifies the release
- Creates Git tag and maintenance branch
- Generates release notes from closed issues
- Creates GitHub Release
- Deploys to gh-pages
- Automatically bumps to next SNAPSHOT version
- Creates PR for next development iteration

**Workflow Structure**:

The workflow uses a **fail-fast approach** with two jobs:

1. **Preflight Job** (runs first):
   - Extracts current version from pom.xml
   - Validates current version is a SNAPSHOT
   - Calculates suggested release version (removes -SNAPSHOT)
   - Calculates suggested next version (increments patch)
   - Validates release version format (semver X.Y.Z)
   - Checks if release tag already exists
   - Checks for SNAPSHOT references in codebase
   - Validates Maven configuration
   - Shows summary in GitHub Actions UI

2. **Release Job** (depends on preflight):
   - Only runs if preflight succeeds
   - Executes the actual release process
   - Uses outputs from preflight job

**Required Input**:
- **release_version** (required): Release version in semver format (e.g., 1.2.2)
  - Must match X.Y.Z format
  - Must NOT contain -SNAPSHOT suffix
  - Will show warning if different from suggested version

**Optional Inputs**:
- **skip_tests** (optional, default: false): Skip tests during release build
  - Use when tests are known to pass and you want faster builds
  - Not recommended for production releases

- **dry_run** (optional, default: false): Validate everything without publishing
  - Tests the entire workflow without side effects
  - Skips: tag creation, branch creation, GitHub release, gh-pages deployment
  - Still creates the PR for next version (to test full workflow)
  - Perfect for testing workflow changes

**How to Run**:

1. Go to **Actions** tab in GitHub
2. Select **Release Workflow**
3. Click **Run workflow**
4. Enter release version (e.g., `1.2.2`)
5. Optionally enable `skip_tests` or `dry_run`
6. Click **Run workflow**

**Preflight Checks**:

The preflight job validates:

| Check | Purpose | Fails on |
|-------|---------|----------|
| Current Version | Ensures starting from SNAPSHOT | Non-SNAPSHOT version |
| Version Format | Validates semver format | Invalid format (not X.Y.Z) |
| Tag Existence | Prevents duplicate releases | Tag already exists |
| SNAPSHOT References | Detects SNAPSHOT strings | N/A (informational) |
| Maven Config | Validates project structure | Maven validation failure |

**Outputs**:

The preflight job provides these outputs to the release job:
- `current_version`: Current version from pom.xml (e.g., 1.2.2-SNAPSHOT)
- `suggested_release`: Suggested release version (e.g., 1.2.2)
- `suggested_next`: Suggested next SNAPSHOT version (e.g., 1.2.3-SNAPSHOT)

**Example Workflow Run**:

```
Current version: 1.2.2-SNAPSHOT
Input version: 1.2.2
Preflight checks: ✅ All passed
Release version: 1.2.2
Next version: 1.2.3-SNAPSHOT (automatic)
```

**What Happens**:

1. **Preflight** (2-3 minutes):
   - ✅ Validates all preconditions
   - ✅ Calculates versions
   - ✅ Shows summary in UI

2. **Release** (15-20 minutes):
   - Sets version to 1.2.2
   - Builds and verifies
   - Creates tag `v1.2.2`
   - Creates maintenance branch `maintenance/1.2.x` (if new)
   - Generates release notes
   - Creates GitHub Release
   - Deploys to gh-pages

3. **Post-Release** (automatic):
   - Bumps version to 1.2.3-SNAPSHOT
   - Creates PR `release/prepare-next-1.2.3-SNAPSHOT`
   - PR includes release notes

**Dry Run Example**:

Perfect for testing workflow changes without side effects:

```
Input: release_version=1.2.2, dry_run=true

Preflight: ✅ All checks passed
Build: ✅ Builds successfully
Tag: ⏭️  Skipped (would create v1.2.2)
Branch: ⏭️  Skipped (would create maintenance/1.2.x)
Release: ⏭️  Skipped (would create GitHub Release)
gh-pages: ⏭️  Skipped (would deploy)
PR: ✅ Created (release/prepare-next-1.2.3-SNAPSHOT)

Result: Workflow validated, nothing published
```

**Version Calculation Logic**:

```bash
Current: 1.2.2-SNAPSHOT
  ↓
Release: 1.2.2 (remove -SNAPSHOT)
  ↓
Next: 1.2.3-SNAPSHOT (increment patch)
```

**Protected Branch Handling**:

The workflow is designed to work with protected main branches:
- ✅ Does NOT push directly to main
- ✅ Creates PR for version changes
- ✅ Allows manual review before merging
- ✅ Tag and maintenance branch are created directly (not on main)

**Best Practices**:

1. **Before Release**:
   - Ensure all PRs for the release are merged
   - Check that tests are passing
   - Review CHANGELOG or release notes

2. **Running Release**:
   - Use suggested version from preflight output
   - Don't skip tests for production releases
   - Use dry_run first to validate workflow

3. **After Release**:
   - Merge the PR for next version promptly
   - Update Eclipse Marketplace (reminder in workflow output)
   - Announce release to users
**Troubleshooting**:

| Issue | Cause | Solution |
|-------|-------|----------|
| "Tag already exists" | Release already created | Check existing releases, increment version |
| "Current version is not SNAPSHOT" | Wrong starting version | Ensure pom.xml has -SNAPSHOT version |
| "Invalid version format" | Wrong input format | Use X.Y.Z format (e.g., 1.2.2) |
| Maven validation fails | Project structure issue | Fix Maven errors first |
| Build fails | Code or dependency issue | Fix build issues before releasing |
| PR creation fails | Branch already exists | Delete old branch or use different version |

**Migration from Old Workflow**:

The new workflow simplifies the release process:

**Old**: Manual input of both versions
```yaml
Inputs:
  - release_version: 1.2.2
  - next_snapshot_version: 1.2.3-SNAPSHOT  # Manual
```

**New**: Automatic calculation
```yaml
Inputs:
  - release_version: 1.2.2
  # next_snapshot_version: Calculated automatically!
```

**Benefits**:
- ✅ Less error-prone (no typos in next version)
- ✅ Consistent version incrementing
- ✅ Preflight catches issues early
- ✅ Better visibility with summary output
- ✅ Safer with dry-run mode

### 1. Fix NLS Comments (`fix-nls.yml`)

**Triggers**: Automatically on PR opened/synchronized (on PRs that modify `.java` files)
- Only runs for PRs created by `copilot[bot]`
- Or when PR is labeled with `auto-fix-nls`

**What it does**:
- Scans plugin source directories (not test modules)
- Adds missing `//$NON-NLS-n$` comments to string literals
- Commits and pushes changes if needed
- Preserves existing NLS comments (no duplicates)

**Directories processed**:
- ✅ All `sandbox_*/` and `sandbox-*/` src directories (plugin modules)
- ❌ Excludes `*_test/` directories (test modules)
- ❌ Excludes `sandbox_test_commons/`, `sandbox_web/`, etc.

**Limitations**:
- Only processes single-line statements ending with `;`, `)`, or `}`
- Multi-line string concatenations are not fully supported
- Uses simple quote counting (may not handle all edge cases with escape sequences)

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

## CI & Testing Workflows

The canonical product verification and the quality-evidence publication are deliberately separate:

- `maven.yml` provides the normal Maven/Tycho build signal for relevant pushes and pull requests. It does not publish the authoritative `/tests/` or `/coverage/` pages.
- `test-report.yml` inventories test sources and guards against accidentally losing registered tests. It is not the public test-report publisher.
- `coverage.yml` runs the complete quality-evidence path and is the sole publisher of the measured test and coverage pages described below.

### Verified test and coverage metrics (`coverage.yml`)

**Triggers**:
- relevant pull requests targeting `main`;
- relevant pushes to `main`;
- manual workflow dispatch;
- daily schedule, skipped when no commit was made in the preceding 24 hours.

**Verification command**:

```bash
xvfb-run --auto-servernum mvn \
  --no-transfer-progress \
  -e -V -T 1C --batch-mode \
  -Dtycho.localArtifacts=ignore \
  -Pjacoco,reports,product,repo,benchmark,cli-dist,maven-plugin \
  clean verify
```

The workflow uses established report consumers rather than repository-specific XML parsers:

- `mikepenz/action-junit-report@v6` reads all Surefire and Failsafe `TEST-*.xml` files and supplies the exact total, passed, failed, and skipped testcase counts;
- `cicirello/jacoco-badge-generator@v2` reads the aggregate `sandbox_coverage/target/site/jacoco-aggregate/jacoco.csv` and generates the instruction-coverage Shields endpoint;
- Maven's `reports` profile produces the module HTML reports copied below `/tests/`.

Pull-request and non-`main` branch runs execute and validate the complete generation path but never mutate GitHub Pages. A successful push to `main`, manual run on `main`, or applicable scheduled run publishes all of the following in one update:

- `badges/tests.json`;
- `badges/coverage.json`;
- `quality-summary.json`, including the source commit and measured values;
- `tests/index.html` and every discovered Maven module report;
- the aggregate JaCoCo HTML report below `/coverage/`.

The evidence artifact name includes both `github.run_number` and `github.run_attempt`, so reruns cannot collide with an immutable artifact from an earlier attempt. Failed builds retain whatever JUnit and JaCoCo evidence is available but cannot replace the last successful public values.

### Published reports

- Test totals and module reports: <https://carstenartur.github.io/sandbox/tests/>
- Aggregate JaCoCo report: <https://carstenartur.github.io/sandbox/coverage/>
- Commit-bound machine-readable summary: <https://carstenartur.github.io/sandbox/quality-summary.json>

For the detailed evidence contract and local reproduction instructions, see [`docs/quality-metrics.md`](../../docs/quality-metrics.md).

## Related Documentation

- [Published test and coverage metrics](../../docs/quality-metrics.md)
- [Sandbox Cleanup Application](../../sandbox_cleanup_application/README.md)
- [Custom Action README](./../actions/cleanup-action/README.md)
- [GitHub Actions Documentation](https://docs.github.com/en/actions)

## License

Eclipse Public License 2.0

SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0

---

## Detailed Release Process

This section describes how to create and publish a new release of the Sandbox project using the automated release workflow.

### Prerequisites

- Write access to the repository
- All tests passing on the `main` branch
- Decide on the release version number (e.g., `1.2.2`)
- Decide on the next SNAPSHOT version (e.g., `1.2.3-SNAPSHOT`)

### Automated Release Workflow

The release process is **fully automated** through GitHub Actions. To create a release:

#### 1. Trigger the Release Workflow

1. Go to the [GitHub Actions tab](https://github.com/carstenartur/sandbox/actions)
2. Select **"Release Workflow"** from the workflows list
3. Click **"Run workflow"** button
4. Fill in the required inputs:
   - **Release version**: The version to release (e.g., `1.2.2`)
   - **Next SNAPSHOT version**: The next development version (e.g., `1.2.3-SNAPSHOT`)
5. Click **"Run workflow"** to start the automated release process

#### 2. What the Workflow Does Automatically

The workflow performs all release steps automatically:

1. ✅ **Validates inputs** to ensure release_version has no `-SNAPSHOT` suffix and next_snapshot_version includes it
2. ✅ **Updates version** in all `pom.xml`, `MANIFEST.MF`, `feature.xml`, and `*.product` files using `tycho-versions-plugin` for all modules **except** `sandbox-functional-converter-core`, which maintains independent versioning
3. ✅ **Verifies** that no SNAPSHOT references remain (except in `sandbox-functional-converter-core`)
4. ✅ **Commits** the release version changes
5. ✅ **Builds and verifies** the release
6. ✅ **Creates and pushes git tag** (`vX.Y.Z`) immediately
7. ✅ **Creates and pushes maintenance branch** (`maintenance/X.Y.x`) immediately for potential backports
8. ✅ **Generates release notes** from closed issues since the last release
9. ✅ **Creates GitHub release** with auto-generated notes
10. ✅ **Deploys** the P2 update site to GitHub Pages at `https://carstenartur.github.io/sandbox/releases/X.Y.Z/`
11. ✅ **Updates composite metadata** to include the new release
12. ✅ **Bumps version** to the next SNAPSHOT version
13. ✅ **Commits and pushes** the SNAPSHOT version back to `main`
14. ✅ **Reminds** to update Eclipse Marketplace listing

#### 3. Post-Release Steps

After the workflow completes successfully:

1. **Verify the release**:
   - Check the [Releases page](https://github.com/carstenartur/sandbox/releases) for the new release
   - Verify the update site is available at `https://carstenartur.github.io/sandbox/releases/X.Y.Z/`

2. **Update Eclipse Marketplace** (if applicable):
   - Go to [Eclipse Marketplace](https://marketplace.eclipse.org/)
   - Update the listing with the new update site URL

3. **Test the release**:
   - Install the plugins from the new update site in a clean Eclipse installation
   - Verify core functionality works as expected

### Workflow Inputs

The automated workflow requires two inputs:

- **`release_version`** (required): 
  - The version number to release (e.g., `1.2.2`)
  - Must NOT include `-SNAPSHOT` suffix
  - Should follow [Semantic Versioning](https://semver.org/)

- **`next_snapshot_version`** (required):
  - The next development version (e.g., `1.2.3-SNAPSHOT`)
  - MUST include `-SNAPSHOT` suffix
  - Typically the next patch, minor, or major version

### Example Release

To release version `1.2.2` and prepare for `1.2.3-SNAPSHOT`:

1. Navigate to Actions → Release Workflow → Run workflow
2. Enter `release_version`: `1.2.2`
3. Enter `next_snapshot_version`: `1.2.3-SNAPSHOT`
4. Click "Run workflow"
5. Monitor the workflow progress in the Actions tab
6. Once complete, the main branch will be at `1.2.3-SNAPSHOT`, ready for development

### Version Numbering

This project follows [Semantic Versioning](https://semver.org/):

- **MAJOR** version (X.0.0): Incompatible API changes
- **MINOR** version (0.X.0): New functionality in a backward-compatible manner
- **PATCH** version (0.0.X): Backward-compatible bug fixes

### Release Artifacts

Each release produces:
- **Eclipse Product**: Installable Eclipse IDE with bundled plugins (`sandbox_product/target`)
- **P2 Update Site**: For installing plugins into existing Eclipse (`sandbox_web/target`)
- **WAR File**: Web-deployable update site
- **Maven Artifacts**: Published to GitHub Packages

### Troubleshooting

**Build fails during release:**
- Ensure all tests pass locally: `mvn clean verify -Pjacoco`
- Check Java version: `java -version` (must be 21+)
- Verify Maven version: `mvn -version` (3.9.x recommended)

**GitHub Actions workflow fails:**
- Check workflow run logs in the Actions tab
- Ensure the tag was pushed correctly: `git ls-remote --tags origin`
- Verify permissions for GitHub Packages publishing