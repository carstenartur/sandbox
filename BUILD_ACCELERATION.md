# Build Acceleration Guide

This document describes the build acceleration improvements implemented in the Sandbox project to speed up local development and CI builds.

## Problem Statement

The Sandbox project previously built all modules on every build, including two heavy modules:

1. **`sandbox_product`**: Eclipse product materialization with `tycho-p2-director-plugin`
   - Creates complete Eclipse distributions for Win/Linux/macOS
   - Takes significant time even when only testing code changes
   
2. **`sandbox_updatesite`**: P2 update site repository assembly
   - Creates p2 repository for plugin distribution
   - Heavy operation not needed for typical development iteration

This meant every build took longer than necessary, even when developers just wanted to test code changes.

## Solution: Profile-Based Builds

The solution implements Maven profiles to control which modules are built:

### Profiles

| Profile | Modules Built | Use Case | Command |
|---------|---------------|----------|---------|
| **Default** (no profile) | All bundles, features, tests | Fast development iteration | `mvn verify` |
| **`product`** | Default + `sandbox_product` | Test Eclipse product locally | `mvn -Pproduct verify` |
| **`repo`** | Default + `sandbox_updatesite` | Create p2 update site | `mvn -Prepo verify` |
| **`product,repo`** | Everything | Full release build | `mvn -Pproduct,repo verify` |
| **`jacoco`** | Adds `sandbox_coverage` | Code coverage reporting | `mvn -Pjacoco verify` |
| **`web`** | Adds `sandbox_web` | WAR file with update site | `mvn -Dinclude=web verify` |

### Backward Compatibility

The command `mvn -Pproduct,repo verify` produces the **exact same result** as the previous full build. This ensures:
- Existing scripts continue to work
- Release builds remain unchanged
- CI can use full builds when needed

## Build Commands

### Quick Reference

```bash
# Fast development build (most common)
mvn -T 1C verify

# Skip tests for even faster iteration
mvn -T 1C -DskipTests verify

# Build with Eclipse product
mvn -Pproduct -T 1C verify

# Build with p2 repository
mvn -Prepo -T 1C verify

# Full release build (everything)
mvn -Pproduct,repo,jacoco -T 1C verify

# Using Make for convenience
make dev       # Fast build, skip tests
make product   # Build with product
make repo      # Build with repository
make release   # Full release build
make test      # Run tests with coverage
make clean     # Clean artifacts
```

### Build Flags

- **`-T 1C`**: Parallel build with 1 thread per CPU core (significantly faster)
- **`-DskipTests`**: Skip test execution (faster for code iteration)
- **`-Pprofile`**: Activate specific profile (e.g., `-Pproduct`)
- **`-Dinclude=web`**: Activate web profile (legacy property-based activation)

## CI/CD Improvements

### GitHub Actions Enhancements

The `.github/workflows/maven.yml` workflow now:

1. **Uses better caching**: Cache key includes both `pom.xml` and `sandbox_target/*.target` files
   ```yaml
   key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml', 'sandbox_target/*.target') }}
   ```

2. **Profile-based builds by event type**:
   - **Pull Requests**: Fast build without product/repo (faster feedback)
   - **Main branch pushes**: Full build with product/repo (complete release artifacts)

3. **Parallel builds**: Uses `-T 1C` flag for faster CI execution

### CI Build Logic

```bash
# PR builds - fast feedback
if [ "${{ github.event_name }}" == "pull_request" ]; then
  xvfb-run --auto-servernum mvn -e -V -T 1C --batch-mode -Dtycho.localArtifacts=ignore -Pjacoco clean verify
else
  # Main branch - full build
  xvfb-run --auto-servernum mvn -e -V -T 1C --batch-mode -Dtycho.localArtifacts=ignore -Pjacoco,product,repo clean verify
fi
```

## Implementation Details

### Changes to `pom.xml`

1. **Removed from default `<modules>`**:
   ```xml
   <!-- Removed: -->
   <module>sandbox_product</module>
   <module>sandbox_updatesite</module>
   ```

2. **Added to profiles**:
   ```xml
   <profile>
     <id>product</id>
     <modules>
       <module>sandbox_product</module>
     </modules>
   </profile>
   
   <profile>
     <id>repo</id>
     <modules>
       <module>sandbox_updatesite</module>
     </modules>
   </profile>
   ```

### Makefile

A convenience `Makefile` provides simple build commands:

```makefile
dev:      # Fast development build
	mvn -T 1C -DskipTests verify

product:  # Build with product
	mvn -Pproduct -T 1C verify

repo:     # Build with repository
	mvn -Prepo -T 1C verify

release:  # Full release build
	mvn -Pproduct,repo,jacoco -T 1C verify

test:     # Run tests with coverage
	xvfb-run --auto-servernum mvn -Pjacoco -T 1C verify

clean:    # Clean all artifacts
	mvn clean
```

## Performance Impact

### Expected Improvements

- **Local development**: 30-50% faster builds by skipping product/repo
- **PR CI builds**: Faster feedback by using default profile
- **Parallel builds**: 20-40% faster with `-T 1C` on multi-core systems
- **Better caching**: More accurate cache hits with target platform in key

### Time Savings Example

Typical build times (estimated, actual times vary by hardware):

| Build Type | Before | After | Savings |
|------------|--------|-------|---------|
| Full build | 10 min | 10 min | 0% (same) |
| Dev build (old) | 10 min | - | - |
| Dev build (new) | - | 5-7 min | 30-50% |
| PR CI | 10 min | 5-7 min | 30-50% |

**Note**: Times are illustrative. Actual build times depend on:
- CPU cores available
- Network speed (first build downloads dependencies)
- Cache effectiveness
- Number of modules with changes

## Migration Guide

### For Developers

No changes needed for most workflows:

- **Before**: `mvn verify`
- **After**: `mvn verify` (same, but faster!)

For full builds:
- **Before**: `mvn -Pjacoco verify`
- **After**: `mvn -Pproduct,repo,jacoco verify` (explicit, same result)

### For CI/CD

The GitHub Actions workflow has been updated automatically. Other CI systems should:

1. Use default profile for fast PR checks
2. Use `-Pproduct,repo` for release builds
3. Add `-T 1C` for parallel builds
4. Update cache keys to include `sandbox_target/*.target`

### For Release Scripts

Update release commands to include profiles explicitly:

```bash
# Old
mvn clean verify

# New (explicit)
mvn -Pproduct,repo,jacoco clean verify
```

## Troubleshooting

### Build doesn't produce product artifacts

**Problem**: Running `mvn verify` doesn't create Eclipse product.

**Solution**: Add `-Pproduct` profile:
```bash
mvn -Pproduct verify
```

### Build doesn't create update site

**Problem**: Running `mvn verify` doesn't create p2 repository.

**Solution**: Add `-Prepo` profile:
```bash
mvn -Prepo verify
```

### Parallel build fails

**Problem**: Build fails with `-T 1C` flag.

**Solution**: 
1. Check if any module has parallelization issues
2. Try with lower thread count: `-T 2` or `-T 4`
3. Fall back to sequential: remove `-T` flag

### Cache not working in CI

**Problem**: CI always downloads all dependencies.

**Solution**: Verify cache configuration includes target platform:
```yaml
key: ${{ runner.os }}-maven-${{ hashFiles('**/pom.xml', 'sandbox_target/*.target') }}
```

## Verifying the Implementation

### Check which modules are built

```bash
# Default build (no product/repo)
mvn help:active-profiles

# With product profile
mvn help:active-profiles -Pproduct

# With repo profile
mvn help:active-profiles -Prepo

# With both profiles
mvn help:active-profiles -Pproduct,repo
```

### Test different build scenarios

```bash
# 1. Fast dev build
mvn -T 1C -DskipTests verify
# Verify: sandbox_product and sandbox_updatesite NOT in reactor

# 2. Product build
mvn -Pproduct -T 1C verify
# Verify: sandbox_product/target/products/ exists

# 3. Repo build
mvn -Prepo -T 1C verify
# Verify: sandbox_updatesite/target/repository/ exists

# 4. Full build
mvn -Pproduct,repo,jacoco -T 1C verify
# Verify: Both product and repository exist
```

## Future Enhancements

Possible future improvements:

1. **Test splitting**: Separate unit tests from integration tests
2. **Module subsets**: Build only changed modules and dependencies
3. **Docker caching**: Cache Maven repository in Docker images
4. **Incremental builds**: Skip unchanged modules (Tycho limitation)
5. **Build matrix**: Test against multiple Eclipse versions in parallel

## Summary

The build acceleration implementation provides:

✅ **Faster development**: Default builds skip heavy product/repo steps  
✅ **Flexibility**: Profiles for different build scenarios  
✅ **Backward compatible**: Full builds work as before  
✅ **CI optimization**: Fast PR checks, complete main builds  
✅ **Parallel builds**: `-T 1C` flag for multi-core speedup  
✅ **Better caching**: Improved cache keys for CI  
✅ **Developer UX**: Makefile for convenient commands  
✅ **Documentation**: Comprehensive build guide  

The result is a faster, more efficient build process that adapts to different development scenarios while maintaining full release build capability.
