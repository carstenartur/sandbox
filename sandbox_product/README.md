# Product Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Product** module builds a complete Eclipse product distribution that includes all sandbox cleanup plugins. This creates a standalone Eclipse installation with all sandbox features pre-installed and ready to use.

## Key Features

- 📦 **Complete Eclipse Distribution** - Full Eclipse with sandbox plugins
- 🎯 **Multiple Platforms** - Builds for Windows, macOS, Linux
- 🚀 **Ready to Use** - No installation needed, just extract and run
- 🔧 **Pre-configured** - Cleanup preferences and settings included
- 🏗️ **Tycho Maven** - Built using Eclipse Tycho

## Quick Start

### Building the Product

```bash
# Build all modules including product
mvn clean verify

# Product artifacts in:
# sandbox_product/target/products/
```

### Running the Product

#### Windows
```bash
cd sandbox_product/target/products/sandbox/win32/win32/x86_64
./eclipse.exe
```

#### macOS
```bash
cd sandbox_product/target/products/sandbox/macosx/cocoa/x86_64
./Eclipse.app/Contents/MacOS/eclipse
```

#### Linux
```bash
cd sandbox_product/target/products/sandbox/linux/gtk/x86_64
./eclipse
```

## What's Included

### Eclipse Base
- Eclipse Platform
- Eclipse JDT (Java Development Tools)
- Eclipse PDE (Plugin Development Environment)

### Sandbox Plugins
All sandbox cleanup plugins:
- Encoding Quickfix
- Platform Helper
- JUnit Cleanup
- Functional Converter
- JFace Cleanup
- Tools (While-to-For)
- XML Cleanup
- Method Reuse
- Usage View
- Extra Search
- TriggerPattern

### Additional Features
- EGit (Git integration)
- Marketplace client
- Help system
- Documentation

## Product Configuration

### Product File

`sandbox.product` defines the product:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<product name="Sandbox" uid="sandbox.product" ...>
  <configIni use="default"/>
  
  <features>
    <feature id="org.eclipse.platform"/>
    <feature id="org.eclipse.jdt"/>
    <feature id="sandbox_encoding_quickfix_feature"/>
    <feature id="sandbox_junit_cleanup_feature"/>
    <!-- ... more features ... -->
  </features>
  
  <configurations>
    <plugin id="org.eclipse.core.runtime" autoStart="true"/>
    <!-- ... more configurations ... -->
  </configurations>
</product>
```

### Launch Configuration

`.product` file includes:
- VM arguments
- Program arguments
- Window title and icons
- Splash screen
- About dialog content

## Build Process

### Maven Build

The product is built by Tycho:

1. **Resolve Dependencies**
   - Uses target platform
   - Resolves all features and plugins

2. **Assemble Product**
   - Collects all required bundles
   - Creates product structure

3. **Package for Platforms**
   - Creates platform-specific archives
   - Includes launcher executables

4. **Generate Archives**
   - Creates .zip/.tar.gz files
   - Ready for distribution

### Build Configuration

```xml
<plugin>
  <groupId>org.eclipse.tycho</groupId>
  <artifactId>tycho-p2-director-plugin</artifactId>
  <executions>
    <execution>
      <id>materialize-products</id>
      <goals>
        <goal>materialize-products</goal>
      </goals>
    </execution>
    <execution>
      <id>archive-products</id>
      <goals>
        <goal>archive-products</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## Platform-Specific Builds

### Supported Platforms

| Platform | Architecture | Format |
|----------|--------------|--------|
| Windows | x86_64 | .zip |
| macOS | x86_64, arm64 | .tar.gz |
| Linux | x86_64 | .tar.gz |

### Architecture Notes

- **x86_64**: Standard 64-bit Intel/AMD processors
- **arm64**: Apple Silicon (M1/M2/M3) Macs
- 32-bit platforms not supported

## Product Artifacts

### Output Directory

```
sandbox_product/target/products/
├── sandbox-linux.gtk.x86_64.tar.gz
├── sandbox-macosx.cocoa.x86_64.tar.gz
├── sandbox-macosx.cocoa.arm64.tar.gz
├── sandbox-win32.win32.x86_64.zip
└── sandbox/
    ├── linux/gtk/x86_64/
    ├── macosx/cocoa/x86_64/
    ├── macosx/cocoa/arm64/
    └── win32/win32/x86_64/
```

### Distribution

Archives can be:
- Uploaded to GitHub releases
- Distributed to team members
- Hosted on download server
- Shared via P2 update site

## Using the Product

### First Launch

1. **Extract Archive**
   - Extract to desired location
   - No installation required

2. **Launch Eclipse**
   - Run platform-specific launcher
   - Select workspace location

3. **Verify Plugins**
   - Help → About Eclipse → Installation Details
   - Verify sandbox plugins are listed

4. **Configure Settings**
   - Window → Preferences → Sandbox
   - Configure cleanup preferences

### Running Cleanups

1. **Open Java Project**
   - Import or create Java project

2. **Configure Cleanup**
   - Source → Clean Up → Configure
   - Enable desired sandbox cleanups

3. **Apply Cleanup**
   - Select files/packages
   - Source → Clean Up
   - Review changes

## Customization

### Branding

Customize product appearance:
- Splash screen: `splash.bmp`
- Window icons: `icons/`
- About dialog: `about.ini`
- Product name: `.product` file

### Features

Add/remove features in `.product`:

```xml
<features>
  <feature id="my.custom.feature"/>
</features>
```

### Configuration

Pre-configure settings:
- Create `configuration/config.ini`
- Add preference files
- Configure default workspace settings

## Testing the Product

### Manual Testing

1. **Build Product**
   ```bash
   mvn clean verify
   ```

2. **Extract & Launch**
   - Extract platform-specific archive
   - Launch Eclipse

3. **Test Features**
   - Import test project
   - Apply cleanups
   - Verify transformations

4. **Test Platforms**
   - Repeat on each target platform

### Automated Testing

Consider:
- UI tests using SWTBot
- Smoke tests for product launch
- Cleanup validation tests

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Product build process
- **[TODO](TODO.md)** - Product improvements
- **[Tycho Documentation](https://tycho.eclipseprojects.io/)** - Build system docs

## Troubleshooting

### Build Fails

**Symptom**: Product build fails with resolution errors

**Solutions**:
- Verify all features are available
- Check target platform is current
- Ensure all dependencies resolved
- Review Tycho logs

### Product Won't Launch

**Symptom**: Eclipse fails to start

**Solutions**:
- Check Java version (requires Java 21)
- Verify platform matches OS
- Check error log in workspace
- Try with clean workspace

### Missing Plugins

**Symptom**: Sandbox plugins not available

**Solutions**:
- Verify features in .product file
- Check plugin dependencies
- Rebuild product from scratch
- Review Installation Details

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **See Also**: [Build Instructions](../README.md#build-instructions) - Main build documentation
