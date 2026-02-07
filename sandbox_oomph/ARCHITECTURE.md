# Oomph Setup - Architecture

> **Navigation**: [Main README](../README.md) | [Plugin README](../README.md#oomph) | [TODO](TODO.md)

## Overview

The **Oomph Module** (`sandbox_oomph`) contains Eclipse Oomph setup files that automate the configuration of a development environment for the sandbox project. Oomph is Eclipse's automated installation and configuration system.

## Purpose

- Automate Eclipse development environment setup
- Clone repository and import projects
- Configure workspace preferences
- Set up target platform
- Install required Eclipse features
- Reduce manual setup steps for contributors

## Module Type

**Development Environment Configuration** - Oomph Setup Files

This module:
- ✅ Contains Oomph setup models (*.setup files)
- ✅ Automates project onboarding
- ✅ Configures Eclipse workspace
- ❌ Not built by Maven (configuration only)
- ❌ Not an Eclipse plugin

## Setup Files

### 1. sandbox.setup

**Purpose**: Main project setup model

**Features**:
- Repository cloning configuration
- Branch selection (main)
- Project import configuration
- Target platform activation
- Workspace preference settings
- P2 repository configuration for Eclipse 2025-09

### 2. sandboxproject.setup

**Purpose**: Project-specific settings

**Features**:
- Java compiler settings (Java 21)
- Code style preferences
- Save actions
- Formatter settings

### 3. sandbox-installer.setup

**Purpose**: Product installer configuration

**Features**:
- Eclipse product selection
- Feature installation
- Version selection

## How It Works

### Oomph Setup Process

```
1. User downloads Eclipse Installer
   ↓
2. Import setup file (sandbox.setup)
   ↓
3. Oomph Installer reads configuration
   ├─ Installs Eclipse 2025-09
   ├─ Clones sandbox repository
   ├─ Imports Maven projects
   ├─ Sets target platform
   └─ Configures workspace
   ↓
4. Ready-to-use development environment
```

### Usage

```bash
# Method 1: Eclipse Installer
1. Download Eclipse Installer
2. Advanced Mode → + (Add setup)
3. Browse to sandbox.setup
4. Follow wizard

# Method 2: Import into existing Eclipse
File → Import... → Oomph → Projects into Workspace
Browse to sandbox.setup
```

## Configuration

### Repository Configuration

```xml
<setupTask xsi:type="git:GitCloneTask">
    <location>${workspace.location}/sandbox</location>
    <remoteURI>https://github.com/carstenartur/sandbox.git</remoteURI>
    <checkoutBranch>main</checkoutBranch>
</setupTask>
```

### P2 Repository

```xml
<setupTask xsi:type="p2:P2DirectorTask">
    <repository url="https://download.eclipse.org/releases/2025-09"/>
</setupTask>
```

## Benefits

### For Contributors

- **5-minute setup**: Automated environment configuration
- **Consistency**: All developers use same configuration
- **No manual steps**: Git clone, import, configure all automated
- **Correct target platform**: Automatically set

### For Project Maintainers

- **Reduced support**: Fewer "how to set up" questions
- **Onboarding**: New contributors productive quickly
- **Updates**: Change setup once, all developers benefit

## Eclipse JDT Correspondence

**Sandbox**: Custom Oomph setup for sandbox project  
**Eclipse JDT**: Has similar Oomph setup in JDT repository

## Integration Points

### Eclipse Oomph Integration

This module integrates with Eclipse's Oomph installer framework:

1. **Setup Model Format**: Uses Oomph's `.setup` XML format for configuration
   - Declarative task definitions
   - Variable resolution (e.g., `${workspace.location}`)
   - Dependency ordering (tasks execute in correct sequence)

2. **Git Integration**: Oomph's GitCloneTask handles repository operations
   - Clones from GitHub using HTTPS
   - Checks out specified branch (main)
   - Configures remote tracking

3. **P2 Integration**: Oomph's P2DirectorTask installs Eclipse features
   - Resolves dependencies from Eclipse 2025-09 repository
   - Installs required JDT, PDE, and SDK features
   - Configures target platform

4. **Project Import**: Oomph's ProjectImportTask discovers Maven projects
   - Scans cloned repository for pom.xml files
   - Imports as Maven projects with Tycho configuration
   - Sets up project dependencies

### Maven/Tycho Integration

The Oomph setup prepares the workspace for Maven/Tycho builds:

1. **Java Compiler Settings**: Configures Eclipse for Java 21
2. **Maven Project Nature**: Ensures projects are recognized as Maven
3. **Tycho Configuration**: Target platform resolution uses P2 repositories

### Eclipse Workspace Integration

Configures Eclipse workspace preferences:

1. **Code Style**: Java formatter, save actions, cleanup preferences
2. **Compiler Settings**: Java 21 compliance level
3. **Build Path**: Correct classpath with Tycho-managed dependencies

## Algorithms and Design Decisions

### Setup Task Ordering

**Decision**: Execute setup tasks in specific order

**Rationale**:
- Git clone must complete before project import
- P2 feature installation before target platform activation
- Workspace preferences after project import

**Order**:
```
1. Git Clone Task
2. P2 Director Task (install features)
3. Project Import Task
4. Preference Setting Tasks
5. Target Platform Activation
```

### Branch Selection Strategy

**Decision**: Default to `main` branch, not `master`

**Rationale**:
- GitHub default branch renamed to `main`
- Consistent with modern Git conventions
- Aligns with Root README documentation
- [See Root README: Eclipse Version Configuration](../README.md#eclipse-version-configuration)

**Implementation**:
```xml
<checkoutBranch>main</checkoutBranch>
```

### Eclipse Version Targeting

**Decision**: Make Eclipse version configurable through Oomph variable

**Rationale**:
- Allows users to update target Eclipse version after initial installation
- Provides flexibility for testing against different Eclipse releases
- Eliminates need to edit setup files manually
- Aligns with Root README build instructions
- [See Root README: Build Instructions](../README.md#build-instructions)

**Implementation**:
```xml
<setupTask xsi:type="setup:VariableTask"
    name="eclipse.target.version"
    value="2025-12"
    label="Eclipse Release Version"
    defaultValue="2025-12">
  <description>The Eclipse release version to use...</description>
</setupTask>
<repository url="https://download.eclipse.org/releases/${eclipse.target.version}"/>
```

**Benefits**:
- Users can change version in Oomph preferences at any time
- Re-running setup will update to selected version
- Consistent with multi-version support strategy
- Simplifies testing against different Eclipse versions

**How to Update Version After Installation**:
1. In Eclipse: Help → Perform Setup Tasks...
2. Find "Eclipse Release Version" variable
3. Change value (e.g., from "2025-12" to "2025-09")
4. Click OK to re-trigger setup with new version
5. Target platform automatically updates

### Eclipse Heap Size Configuration

**Decision**: Make Eclipse heap size configurable through Oomph variable

**Rationale**:
- Different project sizes require different memory allocations
- Allows users to adjust heap size based on available system memory
- Eliminates need to manually edit eclipse.ini after installation
- Provides flexibility for performance tuning

**Implementation**:
```xml
<setupTask xsi:type="setup:VariableTask"
    name="eclipse.heap.size"
    value="2048m"
    label="Eclipse Heap Size"
    defaultValue="2048m">
  <description>The maximum heap size for Eclipse IDE...</description>
</setupTask>
<setupTask xsi:type="setup:EclipseIniTask"
    option="-Xmx"
    value="${eclipse.heap.size}"
    vm="true"/>
```

**Benefits**:
- Users can adjust memory allocation without editing configuration files
- Easy to increase heap size for large projects
- Changes take effect after Eclipse restart
- Prevents out-of-memory errors on large workspaces

**Common Values**:
- `2048m` (2 GB) - Default, suitable for most projects
- `4096m` (4 GB) - Recommended for large projects
- `8192m` (8 GB) - For very large projects or workspaces

**How to Update Heap Size After Installation**:
1. In Eclipse: Help → Perform Setup Tasks...
2. Find "Eclipse Heap Size" variable
3. Change value (e.g., from "2048m" to "4096m")
4. Click OK to apply changes
5. Restart Eclipse for new heap size to take effect

### Why No Automated Build Trigger?

**Decision**: Setup configures environment but doesn't trigger initial build

**Rationale**:
- Build takes significant time (several minutes)
- User may want to review setup before building
- Allows manual inspection of imported projects
- Build can fail if network issues during dependency download

**User Action**: After setup, run `mvn clean verify` manually

## Cross-References

### Root README Sections

This architecture document relates to:

- [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - Oomph setup must match Eclipse version
- [Build Instructions](../README.md#build-instructions) - Oomph prepares environment for Maven build
- [Prerequisites](../README.md#prerequisites) - Java 21 requirement configured by Oomph
- [Contributing](../README.md#contributing) - Oomph simplifies contributor onboarding

### Related Files

- **sandbox.setup** - Main setup model referenced in this document
- **sandboxproject.setup** - Project-specific settings
- **.github/workflows/maven.yml** - CI uses similar steps as Oomph (clone, build)

## References

- [Eclipse Oomph](https://projects.eclipse.org/projects/tools.oomph)
- [Oomph Authoring Guide](https://wiki.eclipse.org/Eclipse_Oomph_Authoring)
- [Eclipse Installer](https://wiki.eclipse.org/Eclipse_Installer)
- [Oomph Setup Model](https://wiki.eclipse.org/Oomph_Setup_Model)

## Summary

The Oomph module provides automated development environment setup:
- Simplifies onboarding process (5-minute setup vs. 30+ minutes manual)
- Ensures consistent developer configuration (same Eclipse version, preferences, target platform)
- Reduces manual setup errors (eliminates common misconfigurations)
- Improves contributor experience (productive in minutes, not hours)
- Aligns with requirements in Root README (Java 21, Eclipse 2025-09, main branch)
