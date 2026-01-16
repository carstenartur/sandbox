# Sandbox Cleanup Action TODO

## Critical Issues

### Docker Build Context Problem - RESOLVED ✅

**Status**: ✅ **RESOLVED** - Converted to composite action

**Solution**: Converted from Docker action to composite action (PR #XXX)

**Problem Description**:
The original Docker action had a fundamental limitation: GitHub Actions builds Docker actions with the action directory as the build context, not the repository root. The `COPY . /sandbox` line only copied action directory files, causing Maven builds to fail with:
```
[ERROR] The goal you specified requires a project to execute but there is no POM in this directory (/sandbox).
```

**Resolution**:
Converted to a **composite action** (`runs: using: 'composite'`) which provides:
1. **Full repository access** via `$GITHUB_WORKSPACE`
2. **Eclipse product caching** using `actions/cache@v4` to speed up subsequent runs
3. **Conditional builds** - Only builds on cache miss, dramatically reducing workflow time
4. **Maven caching** via `actions/setup-java@v4` for dependency resolution

**Architecture**:
The composite action now:
1. Sets up Java 21 with Maven caching
2. Caches the built Eclipse product (`/tmp/eclipse`)
3. Installs Xvfb and GTK dependencies for headless Eclipse
4. Conditionally builds sandbox (only if cache miss)
5. Conditionally extracts Eclipse product (only if cache miss)
6. Runs the cleanup application with proper workspace paths

**Performance Improvement**:
- First run: ~10-15 minutes (full build)
- Cached runs: ~2-3 minutes (no build needed)

**Cleanup**:
- Removed obsolete `Dockerfile` (no longer needed)
- Removed obsolete `entrypoint.sh` (logic moved to action.yml)
- Removed obsolete `.dockerignore` (no Docker build)

**Next Steps**:
- Re-enable `pr-auto-cleanup.yml` workflow by uncommenting the PR trigger
- Test with actual PRs to validate functionality
- Monitor workflow performance and adjust caching strategy if needed

---

## Known Limitations

### Forked Repository Support

**Issue**: The cleanup workflow does not support pull requests from forked repositories.

**Root Cause**: The default `GITHUB_TOKEN` provided to workflows does not have write permissions to forked repositories. This prevents:
1. Pushing cleanup branches to the fork
2. Creating pull requests within the fork

**Impact**: 
- For PRs from the main repository: ✅ Full functionality (cleanup PR created automatically)
- For PRs from forked repositories: ⚠️ Limited functionality (cleanup runs but PR creation fails)

**Workarounds for Forked PRs**:
1. **Manual approach**: Contributors can run the cleanup locally using the sandbox cleanup application
2. **Branch access**: The cleanup branch is created but no PR is automatically opened; reviewers with repo access could manually create the PR
3. **Personal Access Token**: Repository admins could potentially use a PAT with broader permissions (not recommended for security reasons)

**Future Enhancement**: Consider implementing a different workflow pattern for forked PRs, such as:
- Posting cleanup suggestions as review comments instead of creating PRs
- Using a bot account with explicit fork permissions
- Providing cleanup as a downloadable patch file

---

## Future Enhancements

### Performance Optimizations
- [ ] Implement Docker layer caching strategy
- [ ] Reduce Docker image size
- [ ] Support incremental cleanup (only changed files)

### Features
- [ ] Add dry-run mode that shows what would be cleaned without modifying files
- [x] Generate cleanup change reports as PR comments
- [x] Create separate cleanup PRs targeting original PRs (allows accept/reject/modify workflow)
- [ ] Support custom Eclipse preferences
- [ ] Add option to fail PR if cleanup produces changes (enforce clean code)
- [ ] Add fine-grained control: separate commits by cleanup category (formatting vs refactoring)

### Testing
- [ ] Add integration tests for Docker action
- [ ] Test with various cleanup profiles
- [ ] Validate behavior with different repository structures

### Documentation
- [ ] Add video tutorial for using cleanup action
- [ ] Document all available cleanup constants
- [ ] Create troubleshooting guide with common issues

---

## Completed

- ✅ Initial Docker action structure created
- ✅ Entrypoint script implementation
- ✅ README documentation
- ✅ Three cleanup profiles (minimal, standard, aggressive)
- ✅ Integration with pr-auto-cleanup workflow
- ✅ Manual cleanup workflow support
