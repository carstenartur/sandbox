# Target Platform Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Target Platform** module defines the Eclipse target platform used for building all sandbox plugins. It specifies the exact Eclipse version, features, and dependencies required for compilation and runtime, ensuring reproducible builds across different environments.

## Key Features

- 🎯 **Target Platform Definition** - eclipse.target file with all dependencies
- 📦 **Eclipse 2025-09** - Currently targets latest Eclipse release
- 🔒 **Version Locking** - Pinned dependencies for reproducible builds
- 🔌 **P2 Repositories** - Configured update sites and repositories
- 🏗️ **Maven/Tycho Integration** - Used by Tycho build

## Quick Start

### Using the Target Platform

The target platform is automatically used during Maven builds:

```bash
cd /path/to/sandbox
mvn clean verify
```

Tycho resolves dependencies from the target platform defined in `eclipse.target`.

### Opening in Eclipse

1. **Open Target Definition**
   - Navigate to `sandbox_target/eclipse.target`
   - Double-click to open in Target Definition Editor

2. **Set as Active Target**
   - Click "Set as Active Target Platform" in editor
   - Eclipse resolves dependencies from this target

3. **Resolve Target**
   - Click "Reload Target Platform"
   - Wait for Eclipse to download and resolve dependencies

## What's Included

The target platform includes:

### Core Eclipse
- **Eclipse SDK** - Core platform and JDT
- **Eclipse PDE** - Plugin Development Environment
- **Eclipse Platform** - Base platform components

### JDT Components
- **JDT Core** - Java compiler and model
- **JDT UI** - Java development tools UI
- **JDT Debug** - Java debugging support

### Additional Components
- **Eclipse Orbit** - Third-party libraries
- **JustJ JRE** - Embedded Java runtime
- **EGit** - Git integration
- **License Features** - Eclipse license information

### P2 Repositories

Configured repositories:
- Eclipse 2025-09 release
- Eclipse Orbit latest
- JustJ JRE repository
- EGit/EGit GitHub Connector

## Target Platform File

The `eclipse.target` file structure:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<target name="Eclipse 2025-09">
  <locations>
    <location includeAllPlatforms="false" includeConfigurePhase="true" 
             includeMode="planner" includeSource="true" type="InstallableUnit">
      <unit id="org.eclipse.sdk.feature.group" version="..."/>
      <unit id="org.eclipse.jdt.feature.group" version="..."/>
      <!-- ... more units ... -->
      <repository location="https://download.eclipse.org/releases/2025-09"/>
    </location>
    <!-- ... more locations ... -->
  </locations>
</target>
```

## Benefits

### Reproducible Builds
- Same dependencies across all machines
- Consistent compilation results
- Predictable behavior

### Version Control
- Target platform versioned in Git
- Changes tracked and reviewable
- Easy to rollback

### Multi-Version Support
- Can maintain multiple target files
- Support different Eclipse versions
- Test against multiple platforms

## Configuration

### Changing Eclipse Version

To target a different Eclipse version:

1. **Copy Target File**
   ```bash
   cp eclipse.target eclipse-2024-12.target
   ```

2. **Update Repository URLs**
   - Change `2025-09` to `2024-12` in repository locations

3. **Update Version Numbers**
   - Update feature/plugin versions to match new Eclipse

4. **Update Parent POM**
   - Reference new target file in `pom.xml`

5. **Test Build**
   ```bash
   mvn clean verify
   ```

### Adding Dependencies

To add new dependencies:

1. **Open Target Definition**
   - Edit `eclipse.target` in Target Editor

2. **Add Software Site**
   - Click "Add..." → "Software Site"
   - Enter repository URL

3. **Select Features**
   - Browse available features
   - Select required features
   - Click "Finish"

4. **Reload Target**
   - Save file
   - Click "Reload Target Platform"

## Version Information

### Current Target

- **Eclipse Version**: 2025-09
- **Java Version**: Java 21 required
- **JDT Version**: Latest from Eclipse 2025-09
- **Platform Version**: Eclipse Platform 4.33

### Compatibility

The target platform ensures:
- All plugins compile against same Eclipse version
- API compatibility across modules
- Test infrastructure compatibility

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Target platform structure
- **[TODO](TODO.md)** - Version update plans
- **[Eclipse Version Configuration](../README.md#eclipse-version-configuration)** - Detailed upgrade guide

## Maven/Tycho Integration

The target platform integrates with Maven/Tycho build:

### Parent POM Reference

```xml
<plugin>
  <groupId>org.eclipse.tycho</groupId>
  <artifactId>target-platform-configuration</artifactId>
  <configuration>
    <target>
      <artifact>
        <groupId>sandbox</groupId>
        <artifactId>sandbox_target</artifactId>
        <version>1.2.1-SNAPSHOT</version>
      </artifact>
    </target>
  </configuration>
</plugin>
```

### Resolution Process

1. Maven builds sandbox_target module first
2. Tycho reads eclipse.target file
3. Dependencies resolved from P2 repositories
4. Other modules compile against resolved target

## Maintenance

### Regular Updates

Periodically update target platform:
- When new Eclipse version released
- When dependencies need updates
- When security patches available

### Testing Updates

Test target platform changes:
1. Update target file
2. Build project: `mvn clean verify`
3. Run tests: `mvn test`
4. Verify all plugins work
5. Commit if successful

See [TODO.md](TODO.md) for update schedule and plans.

## Troubleshooting

### Resolution Failures

**Symptom**: "Unable to satisfy dependency" errors

**Solutions**:
- Verify repository URLs are accessible
- Check feature/plugin versions exist
- Reload target platform
- Clear P2 cache

### Slow Resolution

**Symptom**: Target platform takes long to resolve

**Solutions**:
- Use local P2 mirror
- Cache P2 repository locally
- Reduce number of repositories
- Use specific versions (not "latest")

### Version Conflicts

**Symptom**: Different plugins need different versions

**Solutions**:
- Find compatible version range
- Update all dependencies together
- Use Eclipse update site for consistency

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related**: [Eclipse Version Configuration](../README.md#eclipse-version-configuration) - Guide for updating Eclipse versions
