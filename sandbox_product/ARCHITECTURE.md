# Eclipse Product Build - Architecture

## Overview

The **Product Module** (`sandbox_product`) defines and builds a complete standalone Eclipse product that includes all sandbox cleanup plugins bundled together. It produces an installable Eclipse IDE with pre-configured features ready to use.

## Purpose

- Build standalone Eclipse product with sandbox features
- Package all cleanup plugins into distributable format
- Provide P2 update site (category.xml) for feature installation
- Generate product archives for multiple platforms (Linux, Windows, macOS)
- Configure product launchers and VM arguments

## Module Type

**Build Configuration Module** - Eclipse Product Definition

This module:
- ✅ Contains Eclipse product definition (`sandbox.product`)
- ✅ Contains P2 category definition (`category.xml`)
- ✅ Defines product features, branding, launcher configuration
- ✅ Produces installable Eclipse product archives
- ❌ No source code (only configuration)

## Core Files

### 1. sandbox.product

**Purpose**: Defines the Eclipse product configuration

**Key Configuration**:
```xml
<product name="Sandbox Product" 
         uid="sandbox.bundle.producteclipse" 
         id="org.eclipse.platform.ide" 
         application="org.sandbox.jdt.core.JavaCleanup"
         version="1.2.2.qualifier" 
         type="features">
```

**Components**:

#### Application
```xml
application="org.sandbox.jdt.core.JavaCleanup"
```
- Default application: Cleanup command-line tool
- Can be changed to `org.eclipse.ui.ide.workbench` for IDE mode

#### Launcher Arguments
```xml
<launcherArgs>
    <programArgs>-clearPersistedState --launcher.defaultAction openFile</programArgs>
    <vmArgs>-Xms512m -Xmx1024m -XX:+UseG1GC -Dosgi.requiredJavaVersion=21</vmArgs>
</launcherArgs>
```

**VM Configuration**:
- Minimum heap: 512 MB
- Maximum heap: 1024 MB
- Garbage collector: G1GC with string deduplication
- Required Java: 21
- Module system: `--add-modules=ALL-SYSTEM`

#### Features Included
```xml
<features>
    <feature id="org.eclipse.platform"/>
    <feature id="org.eclipse.jdt"/>
    <feature id="org.sandbox.encoding_quickfix_feature"/>
    <feature id="org.sandbox.platform_helper_feature"/>
    <feature id="org.sandbox.functional_converter_feature"/>
    <!-- ... all sandbox features -->
</features>
```

#### Plugins Included
All plugins from included features are automatically resolved.

### 2. category.xml

**Purpose**: Defines P2 repository categories for update site

**Structure**:
```xml
<site>
    <category-def name="cleanup" label="JDT Cleanup Extensions">
        <description>Custom JDT cleanup implementations</description>
    </category-def>
    
    <feature id="org.sandbox.encoding_quickfix_feature" version="0.0.0">
        <category name="cleanup"/>
    </feature>
    <!-- ... more features -->
</site>
```

**Usage**: Enables users to browse and install features by category in Eclipse.

## Build Process

### Tycho Product Plugin

The product is built using `tycho-p2-director-plugin`:

```xml
<plugin>
    <groupId>org.eclipse.tycho</groupId>
    <artifactId>tycho-p2-director-plugin</artifactId>
    <executions>
        <execution>
            <id>create-product-distributions</id>
            <goals>
                <goal>materialize-products</goal>
                <goal>archive-products</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### Build Flow

```
1. Maven Build (mvn verify)
   ↓
2. Tycho Resolves Features
   ├─ Resolves dependencies from target platform
   ├─ Collects all required plugins
   └─ Validates feature dependencies
   ↓
3. Product Materialization
   ├─ Creates product layout (Eclipse directory structure)
   ├─ Copies plugins and features
   ├─ Generates launcher scripts
   └─ Configures settings
   ↓
4. Product Archiving
   ├─ Creates platform-specific archives
   ├─ Linux: .tar.gz
   ├─ Windows: .zip
   └─ macOS: .tar.gz
   ↓
5. P2 Repository Generation
   └─ Creates update site with category.xml
```

### Output Structure

```
sandbox_product/target/
├── products/
│   └── org.sandbox.product/
│       ├── linux/gtk/x86_64/          # Linux product
│       │   └── eclipse/
│       ├── win32/win32/x86_64/        # Windows product
│       │   └── eclipse/
│       └── macosx/cocoa/x86_64/       # macOS product
│           └── Eclipse.app/
├── repository/                         # P2 update site
│   ├── plugins/
│   ├── features/
│   ├── artifacts.jar
│   ├── content.jar
│   └── category.xml
└── org.sandbox.product-*.tar.gz       # Archived products
```

## Product Variants

### 1. Standalone IDE Product

**Configuration**: Set application to IDE workbench
```xml
<product application="org.eclipse.ui.ide.workbench">
```

**Usage**:
```bash
cd sandbox_product/target/products/org.sandbox.product/linux/gtk/x86_64/eclipse
./eclipse
```

**Features**:
- Full Eclipse IDE
- All sandbox cleanup plugins installed
- Ready to use for development

### 2. Headless Cleanup Application

**Configuration**: Set application to cleanup tool (current default)
```xml
<product application="org.sandbox.jdt.core.JavaCleanup">
```

**Usage**:
```bash
./eclipse -nosplash -application org.sandbox.jdt.core.JavaCleanup \
    -data /workspace -config cleanup.properties MyClass.java
```

**Features**:
- Command-line cleanup execution
- No UI dependencies
- CI/CD-friendly

## P2 Update Site

### Repository Structure

The generated P2 repository enables plugin installation:

```
sandbox_product/target/repository/
├── artifacts.jar              # Artifact metadata
├── content.jar                # Content metadata
├── category.xml               # Feature categories
├── plugins/
│   ├── org.sandbox.encoding_quickfix_1.2.2.jar
│   ├── org.sandbox.platform_helper_1.2.2.jar
│   └── ... (all plugins)
└── features/
    ├── org.sandbox.encoding_quickfix_feature_1.2.2.jar
    └── ... (all features)
```

### Installing from Update Site

1. **Local Installation**:
   ```
   Help → Install New Software
   Add... → Local → /path/to/sandbox_product/target/repository
   ```

2. **Web-hosted Installation** (via sandbox_web module):
   ```
   Help → Install New Software
   Add... → https://your-server.com/sandbox-updates
   ```

## Platform-Specific Configuration

### Linux

```xml
<linux icon="icons/icon.xpm"/>
```

- Uses XPM format icon
- GTK toolkit
- X11 display required

### Windows

```xml
<win useIco="false">
    <bmp/>
</win>
```

- Optionally uses ICO or BMP icon
- Win32 native widgets

### macOS

```xml
<macosx icon="icons/Eclipse.icns"/>
<vmArgsMac>-XstartOnFirstThread -Xdock:icon=../Resources/Eclipse.icns</vmArgsMac>
```

- Uses ICNS icon format
- Requires `-XstartOnFirstThread` for SWT
- Custom dock icon

## Launcher Configuration

### Program Arguments

```
-clearPersistedState      # Clear cached state on startup
--launcher.defaultAction openFile  # Default action for file association
--launcher.appendVmargs   # Append VM args instead of replacing
```

### VM Arguments

```
-Xms512m                  # Min heap: 512 MB
-Xmx1024m                 # Max heap: 1024 MB
-XX:+UseG1GC              # Use G1 garbage collector
-XX:+UseStringDeduplication  # Enable string deduplication
-Dosgi.requiredJavaVersion=21  # Require Java 21
-Dosgi.dataAreaRequiresExplicitInit=true  # Explicit workspace init
--add-modules=ALL-SYSTEM  # Enable all Java modules
```

## Design Patterns

### Feature-Based Product

Product is composed of features, not individual plugins:
- **Benefit**: Easier dependency management
- **Benefit**: Cleaner update site organization
- **Benefit**: Supports partial installation

### P2 Provisioning

Uses Eclipse P2 for provisioning:
- **Benefit**: Transitive dependency resolution
- **Benefit**: Update support
- **Benefit**: Conflict detection

## Branding and Customization

### Product Branding

**Current**: Uses default Eclipse branding
**Customization**: Can add custom branding plugin

```xml
<product ... branding="org.sandbox.branding">
```

### Splash Screen

```xml
<splash location="org.eclipse.platform"/>
```

**Current**: Uses Eclipse platform splash
**Customization**: Create custom splash screen plugin

### About Dialog

**Current**: Default Eclipse about dialog
**Customization**: Add `about.html`, `about.properties`, `about.mappings`

## Build Output

### Product Archives

```bash
# List generated products
ls -lh sandbox_product/target/*.tar.gz

# Typical output:
# org.sandbox.product-linux.gtk.x86_64.tar.gz    (150 MB)
# org.sandbox.product-win32.win32.x86_64.zip     (150 MB)
# org.sandbox.product-macosx.cocoa.x86_64.tar.gz (150 MB)
```

### Product Size

**Total size**: ~150-200 MB per platform (compressed)
**Uncompressed**: ~400-500 MB (includes Eclipse platform + plugins)

## Usage

### Building the Product

```bash
# Standard build
mvn clean verify

# Build with WAR (includes update site)
mvn -Dinclude=web verify

# Skip tests
mvn clean package -DskipTests
```

### Running the Product

```bash
# Extract product
tar -xzf sandbox_product/target/org.sandbox.product-linux.gtk.x86_64.tar.gz

# Launch Eclipse IDE
cd eclipse
./eclipse

# Or run headless cleanup
./eclipse -nosplash -application org.sandbox.jdt.core.JavaCleanup -data /tmp/workspace -help
```

## Troubleshooting

### "Product cannot be found"

**Cause**: Feature dependencies not resolved

**Solution**: Check that all features are listed in sandbox.product

### "Application not found"

**Cause**: Application plugin not included or ID wrong

**Solution**: Verify `application="org.sandbox.jdt.core.JavaCleanup"` is correct

### Large Product Size

**Cause**: Including unnecessary features

**Solution**: Remove optional features from sandbox.product

### Platform-Specific Build Failures

**Cause**: Platform-specific launchers missing

**Solution**: Ensure target platform includes `org.eclipse.equinox.executable`

## Eclipse JDT Contribution

**Sandbox**: Custom product with sandbox features  
**Eclipse JDT**: Integrated into Eclipse SDK

When contributing to Eclipse JDT:
- No separate product needed
- Features integrated into JDT feature
- Part of Eclipse SDK distribution

## References

- [Eclipse Products](https://help.eclipse.org/latest/topic/org.eclipse.pde.doc.user/guide/tools/editors/product_editor/overview.htm)
- [Tycho Product Plugin](https://tycho.eclipseprojects.io/doc/latest/tycho-p2-director-plugin/materialize-products-mojo.html)
- [P2 Repository](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)

## Summary

The product module is a build configuration component that:
- Defines standalone Eclipse product
- Packages all sandbox features together
- Generates platform-specific distributions
- Creates P2 update site for installation
- Enables both IDE and headless usage

It contains no code, only configuration, but produces the primary deliverable of the project: a working Eclipse installation with all sandbox cleanup features.
