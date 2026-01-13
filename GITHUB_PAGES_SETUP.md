# GitHub Pages P2 Repository Setup

This document explains the GitHub Pages P2 repository setup for the Sandbox project.

## Overview

The repository now has two separate P2 repositories:

1. **sandbox_product**: Full Eclipse product with all dependencies (unchanged)
2. **sandbox_updatesite**: Lightweight P2 repository for Eclipse Marketplace and direct installation

## Architecture

### Maven Module: sandbox_updatesite

- **Location**: `/sandbox_updatesite/`
- **Packaging**: `eclipse-repository`
- **Purpose**: Builds a lightweight P2 repository containing only Sandbox features
- **Configuration**:
  - `pom.xml`: Defines all 12 Sandbox feature dependencies
  - `category.xml`: Categorizes features for Eclipse update site
  - `includeAllDependencies: false`: Excludes external Eclipse dependencies

### GitHub Pages Structure

```
/
├── index.html                      # Landing page with installation instructions
├── snapshots/                      # Snapshot builds (updated on every commit to main)
│   ├── compositeContent.xml        # Composite metadata
│   ├── compositeArtifacts.xml
│   └── latest/                     # Latest snapshot (overwritten on each build)
│       ├── artifacts.jar
│       ├── content.jar
│       ├── features/
│       └── plugins/
└── releases/                       # Release builds (version tags)
    ├── compositeContent.xml        # Composite metadata (references all versions)
    ├── compositeArtifacts.xml
    ├── 1.2.1/                      # Release version directories
    ├── 1.2.2/
    └── 1.2.3/
```

### GitHub Workflows

#### 1. deploy-snapshot.yml

**Trigger**: Push to `main` branch or manual dispatch

**Actions**:
1. Builds `sandbox_updatesite` module with Maven/Tycho
2. Copies P2 repository to `gh-pages/snapshots/latest/`
3. Updates composite metadata files
4. Creates `index.html` if it doesn't exist
5. Deploys to `gh-pages` branch using peaceiris/actions-gh-pages@v4

**Result**: Latest snapshot available at `https://carstenartur.github.io/sandbox/snapshots/latest/`

#### 2. deploy-release.yml

**Trigger**: Git tags matching `v*` pattern (e.g., `v1.2.3`) or manual dispatch

**Actions**:
1. Extracts version from git tag or manual input
2. Builds `sandbox_updatesite` module
3. Creates version-specific directory in `gh-pages/releases/{version}/`
4. Regenerates composite metadata to include all release versions
5. Commits and pushes to `gh-pages` branch

**Result**: 
- New release at `https://carstenartur.github.io/sandbox/releases/{version}/`
- Composite site at `https://carstenartur.github.io/sandbox/releases/` includes all versions

#### 3. cleanup-old-snapshots.yml

**Trigger**: Weekly (Sunday 2 AM UTC) or manual dispatch

**Actions**:
1. Checks `gh-pages` branch repository size
2. If size > 900MB (approaching 1GB limit):
   - Optionally removes old artifacts
   - Commits changes
3. Reports final size

**Purpose**: Prevents GitHub Pages from exceeding the 1GB repository size limit

## Release Process

### Creating a New Release

1. **Update version** (if needed):
   ```bash
   # Update version in pom.xml files
   mvn versions:set -DnewVersion=1.2.3
   ```

2. **Create and push a git tag**:
   ```bash
   git tag v1.2.3
   git push origin v1.2.3
   ```

3. **Automatic deployment**:
   - GitHub Actions workflow triggers automatically
   - Builds the update site
   - Deploys to `releases/1.2.3/`
   - Updates composite metadata

4. **Verify**:
   - Check GitHub Actions workflow completion
   - Visit `https://carstenartur.github.io/sandbox/releases/` to verify

### Manual Release (Emergency)

If automatic release fails, you can trigger manually:

1. Go to GitHub Actions
2. Select "Deploy Release to GitHub Pages" workflow
3. Click "Run workflow"
4. Enter version number (e.g., `1.2.3`)
5. Click "Run workflow"

## Update Site URLs

### For End Users

- **Stable Releases**: `https://carstenartur.github.io/sandbox/releases/`
  - Use in Eclipse: Help → Install New Software → Add...
  - Recommended for production use

- **Latest Snapshot**: `https://carstenartur.github.io/sandbox/snapshots/latest/`
  - Updated automatically on every commit to main
  - Use for testing latest features
  - May be unstable

### For Eclipse Marketplace

Update the Eclipse Marketplace entry with:
- **Update Site URL**: `https://carstenartur.github.io/sandbox/releases/`

## Composite P2 Repositories

Both release and snapshot sites use composite P2 repositories:

### Releases Composite

- References all version directories (1.2.1, 1.2.2, 1.2.3, etc.)
- Eclipse downloads from the latest compatible version
- Allows users to install older versions if needed
- Metadata regenerated on each new release

### Snapshots Composite

- References only `latest/` directory
- Always points to most recent snapshot
- Simpler structure since only one version is maintained

## Troubleshooting

### Workflow Fails: "Could not resolve target platform"

**Cause**: Build dependencies not available in local Maven repository

**Solution**: The `-am` flag in `mvn` command builds all required modules. If issue persists, build locally first:
```bash
mvn clean install -DskipTests
```

### Update Site Not Accessible

**Cause**: GitHub Pages not enabled or branch not deployed

**Solution**:
1. Go to repository Settings → Pages
2. Ensure Source is set to "gh-pages" branch
3. Wait a few minutes for deployment

### Old Versions Not Appearing in Composite Site

**Cause**: Composite metadata not regenerated

**Solution**: 
1. Re-run the deploy-release workflow
2. Or manually trigger it with a new version

## Maintenance

### Monitoring Repository Size

Run the cleanup workflow manually:
```bash
# Via GitHub UI: Actions → Cleanup Old Snapshots → Run workflow
```

Or check size locally:
```bash
git clone --branch gh-pages https://github.com/carstenartur/sandbox.git gh-pages-repo
cd gh-pages-repo
du -sh .
```

### Removing Old Releases

If repository size becomes an issue, manually remove old release directories:

1. Checkout gh-pages branch
2. Remove old version directories: `rm -rf releases/1.0.0`
3. Update composite metadata to remove references
4. Commit and push

## Security Considerations

- All workflows use `secrets.GITHUB_TOKEN` (automatically provided)
- No custom secrets required
- Workflows only write to `gh-pages` branch
- `contents: write` permission explicitly declared

## Future Enhancements

Possible improvements:

1. **Release Notes**: Auto-generate from git commits or CHANGELOG
2. **Version Validation**: Check that version matches pom.xml
3. **Announcement**: Post to discussions or create GitHub release
4. **Multiple Channels**: Separate stable/beta/alpha update sites
5. **Build Artifacts**: Upload P2 repository as GitHub release asset
6. **Notification**: Send notification when new release is available

## References

- [Eclipse P2 Composite Repositories](https://wiki.eclipse.org/Equinox/p2/Composite_Repositories_(new))
- [GitHub Pages Documentation](https://docs.github.com/en/pages)
- [GitHub Actions: peaceiris/actions-gh-pages](https://github.com/peaceiris/actions-gh-pages)
- [Tycho Repository Documentation](https://tycho.eclipseprojects.io/doc/latest/tycho-p2-repository-plugin/plugin-info.html)
