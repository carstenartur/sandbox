# Sandbox Cleanup Action TODO

## Critical Issues

### Docker Build Context Problem (BLOCKING)

**Status**: ❌ **BROKEN** - Action cannot build successfully

**Issue**: The Dockerfile is fundamentally incompatible with GitHub Actions' Docker action architecture.

**Problem Description**:
When GitHub Actions builds a Docker action with `image: 'Dockerfile'` in `action.yml`, the build context is limited to the action directory (`.github/actions/cleanup-action/`). This directory only contains:
- `Dockerfile`
- `entrypoint.sh`
- `action.yml`
- `README.md`
- `.dockerignore`

However, the Dockerfile line 32 attempts:
```dockerfile
COPY . /sandbox
```

This expects to copy the entire sandbox Maven project, but only copies the action directory files. Subsequently, the Maven build fails with:
```
[ERROR] The goal you specified requires a project to execute but there is no POM in this directory (/sandbox).
```

**Root Cause**:
GitHub Actions Docker actions always use the action directory as the build context, not the repository root. This is a fundamental limitation of GitHub Actions' local Docker action architecture.

**Investigation Needed**:

1. **Research GitHub Actions Docker Build Context Options**:
   - Investigate if there's any way to specify a custom build context for local Docker actions
   - Check if GitHub Actions supports referencing Dockerfiles outside the action directory
   - Review GitHub Actions documentation for Docker action best practices

2. **Evaluate Alternative Architectures**:
   - **Option A: Pre-built Docker Image**
     - Build the Docker image in a separate workflow
     - Publish to GitHub Container Registry (GHCR)
     - Reference the published image in `action.yml` using `image: 'docker://ghcr.io/...'`
     - Pros: Cleaner separation, faster PR workflows
     - Cons: Requires image publishing setup, version management

   - **Option B: Composite Action with Build Step**
     - Convert to a composite action (`runs: using: 'composite'`)
     - Add a pre-build step that runs Maven to build the product
     - Use the built product in subsequent steps
     - Pros: No Docker registry needed, stays as local action
     - Cons: Slower PR workflows, more complex action logic

   - **Option C: Move Dockerfile to Repository Root**
     - Place Dockerfile at repository root where it can access all files
     - Modify action to build using a workflow step instead of `image:` field
     - Pros: Direct access to all repository files
     - Cons: Cannot use simple `uses: ./.github/actions/...` syntax

   - **Option D: Separate Maven Module for Action**
     - Create a dedicated Maven sub-module that packages just what's needed for Docker
     - Build this module and copy artifacts into action directory during CI
     - Pros: Smaller Docker context, faster builds
     - Cons: Requires restructuring Maven project

3. **Analyze Build Requirements**:
   - Determine minimum files/artifacts needed for cleanup application
   - Evaluate if full sandbox build is necessary or if only specific modules needed
   - Consider if Eclipse product can be built externally and artifact cached

4. **Performance Considerations**:
   - Current approach would require 10-15 minute builds per PR
   - Evaluate caching strategies for Maven dependencies and built artifacts
   - Consider incremental build approaches

5. **Test Locally**:
   - Attempt to build Docker image with actual repository content
   - Verify the cleanup application works when properly built
   - Document memory/CPU requirements

**Recommended Approach** (after investigation):
The most robust solution appears to be **Option A: Pre-built Docker Image**:
1. Create a dedicated workflow that builds the Docker image when code changes
2. Publish to GHCR with version tags
3. Update `action.yml` to reference the published image
4. This separates build complexity from PR workflow execution

**Temporary Workaround**:
The `pr-auto-cleanup.yml` workflow has been temporarily disabled to unblock PR merges. Re-enable after the build issue is resolved.

**References**:
- GitHub Actions Docker Actions: https://docs.github.com/en/actions/creating-actions/creating-a-docker-container-action
- GitHub Issue discussing this: (none yet)
- Related PR that introduced the action: #420

**Next Steps**:
1. Research feasibility of each option
2. Create POC for preferred approach
3. Implement solution in separate PR
4. Test thoroughly before re-enabling auto-cleanup workflow
5. Update documentation with final architecture

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
