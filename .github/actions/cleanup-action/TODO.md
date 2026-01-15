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

## Future Enhancements

### Performance Optimizations
- [ ] Implement Docker layer caching strategy
- [ ] Reduce Docker image size
- [ ] Support incremental cleanup (only changed files)

### Features
- [ ] Add dry-run mode that shows what would be cleaned without modifying files
- [ ] Generate cleanup change reports as PR comments
- [ ] Support custom Eclipse preferences
- [ ] Add option to fail PR if cleanup produces changes (enforce clean code)

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
