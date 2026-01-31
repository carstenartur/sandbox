# Target Platform - TODO

## Status Summary

**Current State**: Stable configuration for Eclipse 2025-09

### Completed
- ✅ Eclipse 2025-09 target platform definition
- ✅ Orbit dependencies configured
- ✅ EGit integration
- ✅ Bouncy Castle libraries (version 1.81.0)
- ✅ JDT, SDK, and PDE features
- ✅ Source inclusion for debugging

### In Progress
- 🔄 Documentation (ARCHITECTURE.md complete, this TODO in progress)

### Pending
- [ ] Multi-version target platform support
- [ ] Automated target platform updates
- [ ] Target platform validation
- [ ] Local repository mirrors

## Priority Tasks

### 1. Multi-Version Target Platform Support
**Priority**: Medium  
**Effort**: 4-6 hours

Support multiple Eclipse versions for backporting:

**Goal**: Build against multiple Eclipse releases (2025-09, 2024-12, 2024-09)

**Implementation**:
```
sandbox_target/
├── eclipse-2025-09.target    # Latest release
├── eclipse-2024-12.target    # Previous release
└── eclipse-2024-09.target    # Older release
```

**Maven Configuration**:
```xml
<profiles>
    <profile>
        <id>eclipse-2025-09</id>
        <activation>
            <activeByDefault>true</activeByDefault>
        </activation>
        <properties>
            <target-file>eclipse-2025-09.target</target-file>
        </properties>
    </profile>
    <profile>
        <id>eclipse-2024-12</id>
        <properties>
            <target-file>eclipse-2024-12.target</target-file>
        </properties>
    </profile>
</profiles>
```

**Usage**:
```bash
# Build against latest (default)
mvn clean verify

# Build against 2024-12
mvn clean verify -Peclipse-2024-12

# Build against 2024-09
mvn clean verify -Peclipse-2024-09
```

**Benefits**:
- Support backporting features
- Test compatibility across versions
- Enable branch-specific builds
- Maintain multiple release streams

### 2. Automated Target Platform Updates
**Priority**: Low  
**Effort**: 6-8 hours

Automate detection and updates of new Eclipse releases:

**Features**:
1. **Version Check Script**:
   ```bash
   #!/bin/bash
   # check-eclipse-updates.sh
   
   CURRENT="2025-09"
   LATEST=$(curl -s https://download.eclipse.org/releases/ | 
            grep -oP '\d{4}-\d{2}' | sort -V | tail -1)
   
   if [ "$LATEST" != "$CURRENT" ]; then
       echo "New Eclipse version available: $LATEST"
       echo "Current: $CURRENT"
       exit 1
   fi
   ```

2. **Automated PR Creation**:
   - GitHub Actions workflow
   - Detect new Eclipse releases
   - Create PR with updated target platform
   - Run CI to verify build

**Benefits**:
- Stay current with Eclipse releases
- Automated notification of updates
- Reduce manual maintenance
- Prevent falling behind

### 3. Target Platform Validation
**Priority**: Medium  
**Effort**: 3-4 hours

Add validation to ensure target platform is correct:

**Checks**:
1. **Repository Availability**:
   ```bash
   # Verify all repositories are accessible
   curl -f https://download.eclipse.org/releases/2025-09/
   ```

2. **Feature Availability**:
   ```bash
   # Verify required features exist
   p2-admin -listFeatures -repository https://download.eclipse.org/releases/2025-09/ | 
       grep org.eclipse.jdt.feature.group
   ```

3. **Version Consistency**:
   - Check all references use same Eclipse version
   - Validate Orbit repository matches Eclipse release

**Integration**:
```bash
# Run as part of CI
mvn verify -Pvalidate-target-platform
```

**Benefits**:
- Catch configuration errors early
- Prevent build failures due to invalid URLs
- Ensure version consistency
- Improve reliability

### 4. Local Repository Mirrors
**Priority**: Low  
**Effort**: 8-10 hours

Create local mirrors of P2 repositories for faster builds:

**Approach**:
1. **Mirror Eclipse Releases**:
   ```bash
   # Mirror Eclipse 2025-09
   p2-mirror -source https://download.eclipse.org/releases/2025-09/ \
             -destination file:///var/local/p2-mirror/2025-09/
   ```

2. **Update Target Platform**:
   ```xml
   <repository location="file:///var/local/p2-mirror/2025-09/"/>
   ```

3. **Automated Synchronization**:
   - Cron job to sync with upstream
   - Keep local mirror up-to-date
   - Faster builds for all developers

**Benefits**:
- Faster dependency resolution (no network)
- Works offline
- Reduced bandwidth usage
- Consistent build performance

## Known Issues

### 1. Large Download Size
**Severity**: Low

Initial build downloads ~1-2 GB from P2 repositories.

**Impact**: Slow first build, especially on slow networks

**Workaround**: Use local P2 mirror or shared Maven repository

**Note**: Cached after first build

### 2. Network Dependency
**Severity**: Medium

Build requires network access to download dependencies.

**Impact**: Cannot build offline (first time)

**Workaround**: 
- Build once online to populate cache
- Subsequent builds work offline
- Use local P2 mirror

### 3. Version Drift
**Severity**: Low

Using `version="0.0.0"` means builds can change over time.

**Example**: Building today vs. 6 months from now may use different versions

**Impact**: Builds not fully reproducible

**Solution**: Pin specific versions for critical dependencies

## Future Enhancements

### Target Platform Profiles
**Priority**: Low  
**Effort**: 4-6 hours

Create predefined profiles for common scenarios:

```bash
# Minimal profile (fast build, fewer features)
mvn verify -Ptarget-minimal

# Full profile (all features, slow build)
mvn verify -Ptarget-full

# Testing profile (includes test frameworks)
mvn verify -Ptarget-testing
```

### Composite Target Platforms
**Priority**: Low  
**Effort**: 6-8 hours

Combine multiple target definitions:

```xml
<target name="composite">
    <location>
        <targetDefinition path="base.target"/>
    </location>
    <location>
        <targetDefinition path="optional.target"/>
    </location>
</target>
```

**Benefits**:
- Modular target platform
- Share common definitions
- Customize per use case

### Target Platform Diff Tool
**Priority**: Low  
**Effort**: 4-6 hours

Tool to compare target platforms:

```bash
# Compare two target platforms
target-diff eclipse-2025-09.target eclipse-2024-12.target

# Output:
# Added features:
#   - org.eclipse.jdt.new.feature 1.0.0
# Removed features:
#   - org.eclipse.old.feature 0.9.0
# Updated features:
#   - org.eclipse.jdt.core 3.19 → 3.20
```

**Benefits**:
- Understand impact of upgrades
- Review changes before committing
- Document migration path

## Maintenance Tasks

### When New Eclipse Release Available
1. [ ] Update eclipse.target with new version URLs
2. [ ] Update Orbit repository URL
3. [ ] Test build: `mvn clean verify`
4. [ ] Fix any compilation errors
5. [ ] Update related files (pom.xml, README.md, etc.)
6. [ ] Create PR with changes
7. [ ] Update documentation

### Quarterly Maintenance
- [ ] Check for new Eclipse releases
- [ ] Review dependency versions
- [ ] Test build against latest Eclipse
- [ ] Consider upgrading if stable

### After Adding New Feature Dependency
1. [ ] Add feature unit to eclipse.target
2. [ ] Verify build succeeds
3. [ ] Document why dependency was added
4. [ ] Test in clean environment

## Documentation Improvements

### Completed Documentation
- ✅ ARCHITECTURE.md: Design and configuration details
- ✅ TODO.md: This file

### Additional Documentation Needed
- [ ] Target platform migration guide
- [ ] Troubleshooting guide for common issues
- [ ] How to add new dependencies
- [ ] Best practices for target platform management

## Eclipse JDT Contribution

### Target Platform Differences

**Sandbox**: Custom target platform with specific features  
**Eclipse JDT**: Uses Eclipse SDK baseline

### Considerations for Contribution

When contributing to Eclipse JDT:
- JDT defines its own target platform
- API baseline different from sandbox
- May need to adjust dependencies
- Follow Eclipse baseline policies

## Performance Considerations

### Build Time Impact

**Initial Build**: 5-10 minutes (download dependencies)  
**Subsequent Builds**: 3-5 minutes (cached dependencies)  
**Offline Builds**: 2-3 minutes (no network)

### Optimization Opportunities

1. **Local P2 Mirror**: Save 2-5 minutes per build
2. **Shared Maven Repo**: Team shares downloaded artifacts
3. **Minimal Target**: Include only needed features
4. **Parallel Downloads**: Use Maven parallel downloads

## References

- [Eclipse Target Platform Guide](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm)
- [Tycho Target Configuration](https://tycho.eclipseprojects.io/doc/latest/tycho-packaging-plugin/target-platform-configuration-mojo.html)
- [Eclipse Releases Download](https://download.eclipse.org/releases/)
- [Eclipse Orbit Repository](https://download.eclipse.org/tools/orbit/)

## Contact

For questions about target platform configuration:
- Open an issue in the repository
- Submit a pull request
- Contact: See project contributors

## Impact on Other Modules

Target platform affects:
- **All plugin modules**: Compiled against target platform
- **Test modules**: Tests run against target platform
- **Product module**: Product built from target platform features
- **Feature modules**: Feature resolution uses target platform

Changes to target platform can affect entire project—test thoroughly.
