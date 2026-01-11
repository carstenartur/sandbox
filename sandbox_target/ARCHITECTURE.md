# Target Platform - Architecture

## Overview

The **Target Platform** module (`sandbox_target`) defines the Eclipse platform version and dependencies that the sandbox project is built against. It specifies which Eclipse features, plugins, and third-party libraries are available during compilation and runtime.

## Purpose

- Define Eclipse release version (currently 2025-09)
- Specify required Eclipse features (JDT, SDK, PDE)
- Declare external dependencies from Eclipse Orbit
- Ensure consistent build environment across all developers
- Pin dependency versions for reproducible builds

## Module Type

**Build Configuration Module** - Contains target platform definition

This module:
- ✅ Contains Eclipse target platform file (`eclipse.target`)
- ✅ Defines P2 repositories for dependency resolution
- ✅ Specifies required Eclipse features and plugins
- ✅ Used by Tycho Maven plugin during build
- ❌ No source code (src/ directory absent)
- ❌ Not an Eclipse plugin itself

## Target Platform File

### File Structure

**Location**: `sandbox_target/eclipse.target`

The file defines multiple P2 repository locations, each providing specific components:

```xml
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<?pde version="3.8"?>
<target includeMode="Feature" name="target platform for sandbox">
    <locations>
        <location includeAllPlatforms="false" includeConfigurePhase="true" 
                  includeMode="planner" includeSource="true" type="InstallableUnit">
            <repository location="https://download.eclipse.org/releases/2025-09/"/>
            <unit id="org.eclipse.jdt.feature.group" version="0.0.0"/>
            <!-- more units -->
        </location>
    </locations>
</target>
```

### Repository Locations

#### 1. Eclipse 2025-09 Release

**URL**: `https://download.eclipse.org/releases/2025-09/`

**Components**:
- `org.eclipse.jdt.feature.group` - Java Development Tools
- `org.eclipse.sdk.feature.group` - Eclipse SDK
- `org.eclipse.pde.feature.group` - Plugin Development Environment
- `org.eclipse.equinox.executable.feature.group` - Eclipse executable
- `org.eclipse.jdt.astview.feature.feature.group` - AST View tool
- `org.eclipse.jdt.jeview.feature.feature.group` - Java Element View
- `org.eclipse.pde.spies.feature.group` - PDE Spy tools

#### 2. Eclipse Orbit (Dependencies)

**URL**: `https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2025-09/`

**Components**:
- `org.apache.commons.commons-io` - Apache Commons IO
- `org.apache.commons.lang3` - Apache Commons Lang3

**Purpose**: Orbit provides third-party libraries as OSGi bundles.

#### 3. Eclipse License Feature

**URL**: `https://download.eclipse.org/cbi/updates/license`

**Components**:
- `org.eclipse.license.feature.group` - Eclipse Public License information

#### 4. EGit (Git Integration)

**URL**: `https://download.eclipse.org/egit/updates/`

**Components**:
- `org.eclipse.egit.feature.group` - Eclipse Git Team Provider
- `org.eclipse.jgit.feature.group` - JGit library

#### 5. Orbit Maven-OSGi (Bouncy Castle)

**URL**: `https://download.eclipse.org/tools/orbit/simrel/maven-osgi/release/4.37.0`

**Components**:
- `bcutil` version 1.81.0 - Bouncy Castle utilities
- `bcprov` version 1.81.0 - Bouncy Castle cryptography provider

**Note**: Pinned to specific release (4.37.0) for reproducible builds.

## How It Works

### Tycho Integration

The target platform is consumed by Tycho Maven plugin:

```xml
<!-- In parent pom.xml -->
<plugin>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>target-platform-configuration</artifactId>
    <configuration>
        <target>
            <artifact>
                <groupId>org.sandbox</groupId>
                <artifactId>sandbox_target</artifactId>
                <version>${project.version}</version>
            </artifact>
        </target>
    </configuration>
</plugin>
```

### Build Process

```
1. Tycho reads sandbox_target/eclipse.target
   ↓
2. Resolves P2 repositories
   ├─ Downloads Eclipse features
   ├─ Downloads Orbit dependencies
   └─ Downloads EGit plugins
   ↓
3. Builds dependency graph
   ↓
4. Compiles plugins against target platform
   ↓
5. Validates OSGi dependencies
```

### Version Resolution

**Strategy**: `version="0.0.0"` means "use latest available"

```xml
<unit id="org.eclipse.jdt.feature.group" version="0.0.0"/>
```

**Rationale**:
- Always get the latest from specified release (2025-09)
- No need to update version numbers
- Consistent across point releases (2025-09.1, 2025-09.2)

**Alternative** (for reproducible builds):
```xml
<unit id="org.eclipse.jdt.feature.group" version="3.20.0.v20250101-1234"/>
```

## Target Platform Benefits

### 1. Consistent Build Environment

All developers and CI systems use the same Eclipse version:
- Same API availability
- Same dependency versions
- Reproducible builds
- No "works on my machine" issues

### 2. Dependency Management

Tycho resolves all dependencies from target platform:
- No need for manual JAR management
- Automatic dependency resolution
- Transitive dependencies handled
- OSGi manifest validation

### 3. IDE Integration

Eclipse PDE can use target platform:
- Set as active target platform in Eclipse
- Development environment matches build
- Code completion uses correct APIs
- Compilation errors match build errors

### 4. Version Migration

Upgrading Eclipse version is centralized:
- Update URLs in eclipse.target
- One file to change
- All modules updated together
- Consistent across project

## Updating Eclipse Version

### Process

To migrate to a new Eclipse release (e.g., 2026-03):

1. **Update eclipse.target**:
   ```xml
   <repository location="https://download.eclipse.org/releases/2026-03/"/>
   ```

2. **Update Orbit repository**:
   ```xml
   <repository location="https://download.eclipse.org/tools/orbit/simrel/orbit-aggregation/2026-03/"/>
   ```

3. **Test build**:
   ```bash
   mvn clean verify -Pjacoco
   ```

4. **Fix compilation errors**: Update code for API changes

5. **Update documentation**: Note new Eclipse version

### Related Files

When updating Eclipse version, also update:
- `/pom.xml` - Repository URLs
- `/sandbox_product/category.xml` - Repository references
- `/sandbox_product/sandbox.product` - Repository locations
- `/sandbox_oomph/sandbox.setup` - P2 repository URL
- `/README.md` - Documentation

See README.md section "Eclipse Version Configuration" for complete list.

## Design Patterns

### Single Source of Truth

The target platform file is the authoritative source for:
- Eclipse version
- Feature versions
- Dependency versions

All other references derive from this.

### Declarative Configuration

Dependencies are declared, not scripted:
- XML format
- No imperative code
- Tool-independent (PDE, Tycho)

## Package Structure

```
sandbox_target/
├── eclipse.target          # Target platform definition
├── pom.xml                # Maven module configuration
├── LICENSE.txt            # Eclipse Public License
└── .settings/             # Eclipse project settings
```

## IDE Usage

### Setting Active Target Platform

1. Open Eclipse IDE
2. Window → Preferences → Plug-in Development → Target Platform
3. Click "Add..." → "Software Site"
4. Browse to `sandbox_target/eclipse.target`
5. Click "Set as Target Platform"

### Benefits

- Eclipse resolves dependencies from target platform
- Code completion uses target platform APIs
- Compilation uses target platform
- Matches build environment exactly

## Troubleshooting

### "Cannot resolve target definition"

**Cause**: Network issue or invalid repository URL

**Solution**:
```bash
# Clear P2 cache
rm -rf ~/.m2/repository/p2
mvn clean verify
```

### "Unit not found in repository"

**Cause**: Feature/plugin not available in specified Eclipse version

**Solution**: 
- Check feature ID is correct
- Verify Eclipse version supports the feature
- Update to newer Eclipse version if needed

### "Conflicting dependencies"

**Cause**: Multiple versions of same plugin requested

**Solution**:
- Pin specific versions
- Exclude transitive dependencies
- Update feature to compatible version

## Best Practices

### 1. Use Explicit Eclipse Version

Don't use "latest":
```xml
<!-- Good -->
<repository location="https://download.eclipse.org/releases/2025-09/"/>

<!-- Bad -->
<repository location="https://download.eclipse.org/releases/latest/"/>
```

### 2. Pin Critical Dependencies

For critical dependencies, specify exact version:
```xml
<unit id="bcutil" version="1.81.0"/>
```

### 3. Include Source

Always include source for debugging:
```xml
<location ... includeSource="true">
```

### 4. Document Custom Repositories

Add comments explaining why each repository is needed:
```xml
<!-- Bouncy Castle for cryptography -->
<location ...>
```

## Eclipse JDT Correspondence

**Sandbox**: Defines custom target platform  
**Eclipse JDT**: Uses Eclipse SDK as baseline

When contributing to Eclipse JDT:
- JDT builds against SDK baseline
- No custom target platform needed
- API Tools enforce baseline compatibility

## References

- [Eclipse Target Platform](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/concepts/target.htm)
- [Tycho Target Platform](https://tycho.eclipseprojects.io/doc/latest/tycho-packaging-plugin/target-platform-configuration-mojo.html)
- [Eclipse Releases](https://download.eclipse.org/releases/)
- [Eclipse Orbit](https://download.eclipse.org/tools/orbit/)

## Summary

The target platform module is a critical build configuration component that:
- Defines Eclipse version and dependencies
- Ensures consistent build environment
- Enables reproducible builds
- Simplifies dependency management
- Centralizes version configuration

It contains no code, only configuration, but is essential for building the project correctly.
