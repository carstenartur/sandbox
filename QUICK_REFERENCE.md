# Quick Reference: P2 Repository Operations

## Creating a Release

```bash
# 1. Ensure you're on main branch with latest changes
git checkout main
git pull

# 2. Create and push version tag
git tag v1.2.3
git push origin v1.2.3

# 3. Wait for GitHub Actions to complete (2-5 minutes)
# Check: https://github.com/carstenartur/sandbox/actions

# 4. Verify release is available
# Visit: https://carstenartur.github.io/sandbox/releases/
```

## Testing in Eclipse

### Install from Snapshot (Latest Development Build)

1. Open Eclipse
2. Help → Install New Software...
3. Add... → Name: "Sandbox Snapshots", Location: `https://carstenartur.github.io/sandbox/snapshots/latest/`
4. Select features and install

### Install from Release (Stable)

1. Open Eclipse
2. Help → Install New Software...
3. Add... → Name: "Sandbox", Location: `https://carstenartur.github.io/sandbox/releases/`
4. Select features and install

## Manually Trigger Workflows

### Deploy Snapshot
```bash
# Via GitHub UI:
Actions → Deploy Snapshot to GitHub Pages → Run workflow
```

### Deploy Release
```bash
# Via GitHub UI:
Actions → Deploy Release to GitHub Pages → Run workflow
# Enter version: 1.2.3 (without 'v' prefix)
```

### Cleanup Old Snapshots
```bash
# Via GitHub UI:
Actions → Cleanup Old Snapshots → Run workflow
```

## Check Repository Size

```bash
# Clone gh-pages branch
git clone --branch gh-pages --depth 1 https://github.com/carstenartur/sandbox.git gh-pages-check
cd gh-pages-check
du -sh .
rm -rf ../gh-pages-check
```

## Build Locally

```bash
# Build update site
cd /path/to/sandbox
export JAVA_HOME=/path/to/jdk-21
mvn clean verify -pl sandbox_updatesite -am -DskipTests

# Output location:
# sandbox_updatesite/target/repository/
```

## Troubleshooting

### "Site Not Found" in Eclipse

**Problem**: Eclipse cannot find the update site

**Solution**:
1. Verify URL is correct (no trailing slash issues)
2. Check GitHub Pages is enabled: Settings → Pages
3. Wait a few minutes after workflow completion
4. Clear Eclipse cache: Window → Preferences → Install/Update → Available Software Sites → Reload

### Workflow Fails: "Could not resolve target platform"

**Problem**: Missing dependencies

**Solution**:
```bash
# Build all modules first
mvn clean install -DskipTests
```

### Composite Site Shows Old Versions Only

**Problem**: Metadata not regenerated

**Solution**:
1. Re-run deploy-release workflow
2. Or create a new dummy tag to trigger regeneration

## Updating Eclipse Marketplace

When you have a new stable release:

1. Go to https://marketplace.eclipse.org/content/sandbox
2. Click "Edit" (requires login)
3. Update "Update Site URL" to: `https://carstenartur.github.io/sandbox/releases/`
4. Save changes

## Common URLs

| Purpose | URL |
|---------|-----|
| Landing Page | https://carstenartur.github.io/sandbox/ |
| Stable Releases | https://carstenartur.github.io/sandbox/releases/ |
| Latest Snapshot | https://carstenartur.github.io/sandbox/snapshots/latest/ |
| GitHub Actions | https://github.com/carstenartur/sandbox/actions |
| Repository Settings | https://github.com/carstenartur/sandbox/settings |
| Pages Settings | https://github.com/carstenartur/sandbox/settings/pages |

## File Locations

| What | Where |
|------|-------|
| Update site build | `sandbox_updatesite/target/repository/` |
| Category definition | `sandbox_updatesite/category.xml` |
| Update site pom | `sandbox_updatesite/pom.xml` |
| Snapshot workflow | `.github/workflows/deploy-snapshot.yml` |
| Release workflow | `.github/workflows/deploy-release.yml` |
| Cleanup workflow | `.github/workflows/cleanup-old-snapshots.yml` |

## Version Numbering

Follow semantic versioning: `MAJOR.MINOR.PATCH`

- **MAJOR**: Breaking changes
- **MINOR**: New features, backward compatible
- **PATCH**: Bug fixes, backward compatible

Examples:
- `v1.2.3` - Patch release
- `v1.3.0` - Minor feature release
- `v2.0.0` - Major release with breaking changes

## Emergency Rollback

If a release is broken:

1. **Do NOT delete the tag** (might break things)
2. **Create a new patch release** with fixes:
   ```bash
   git tag v1.2.4
   git push origin v1.2.4
   ```
3. **Update documentation** to recommend the new version
4. **Optionally**: Remove broken release from gh-pages branch manually

## Monitoring

### GitHub Actions Status

Check workflow runs:
```
https://github.com/carstenartur/sandbox/actions
```

### GitHub Pages Status

Check deployment:
```
https://github.com/carstenartur/sandbox/deployments
```

### Repository Size

Check weekly cleanup workflow logs:
```
Actions → Cleanup Old Snapshots → Latest run → View logs
```
