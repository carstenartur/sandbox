# Web Module (WAR Packaging) - Architecture

> **Navigation**: [Main README](../README.md) | [Build Instructions](../README.md#build-instructions) | [TODO](TODO.md)

## Overview

The **Web Module** (`sandbox_web`) packages the P2 update site into a WAR (Web Application Archive) file for deployment to web servers. This enables users to install sandbox plugins via HTTP/HTTPS from Eclipse.

## Purpose

- Package P2 update site as web-deployable WAR file
- Enable HTTP/HTTPS-based plugin installation
- Provide hosted update site for Eclipse users
- Include all feature plugins in single archive
- Support continuous delivery of plugin updates

## Module Type

**Build Packaging Module** - WAR Generation

This module:
- ✅ Packages sandbox_product repository as WAR
- ✅ Maven packaging type: `<packaging>war</packaging>`
- ✅ Depends on all feature modules
- ✅ Output: `sandbox-updates.war`
- ❌ No source code

## How It Works

### Build Process

```
1. Maven Dependency Plugin
   ↓ Unpacks all feature dependencies
   ↓ Extracts plugins and features
   ↓
2. Maven WAR Plugin
   ↓ Packages as WAR file
   ↓ Includes artifacts.jar, content.jar
   ↓ Includes category.xml
   ↓
3. Output: sandbox-updates.war
```

### WAR Structure

```
sandbox-updates.war
├── plugins/
│   ├── org.sandbox.encoding_quickfix_*.jar
│   ├── org.sandbox.platform_helper_*.jar
│   └── ... (all plugins)
├── features/
│   ├── org.sandbox.encoding_quickfix_feature_*.jar
│   └── ... (all features)
├── artifacts.jar
├── content.jar
└── category.xml
```

## Deployment

### Web Server Deployment

```bash
# Deploy to Tomcat
cp target/sandbox-updates.war /var/lib/tomcat9/webapps/

# Access update site
http://localhost:8080/sandbox-updates/
```

### Eclipse Installation

```
Help → Install New Software
Add... → http://your-server.com/sandbox-updates/
Select features → Install
```

## Integration Points

### Eclipse P2 Integration

The WAR module integrates with Eclipse's P2 (Provisioning Platform) update mechanism:

1. **P2 Repository Structure**: The WAR contains a complete P2 repository with:
   - `artifacts.jar` - Metadata about plugin artifacts
   - `content.jar` - Metadata about features and capabilities
   - `category.xml` - Feature categorization for UI presentation
   - `plugins/` directory - OSGi bundles
   - `features/` directory - Eclipse features

2. **HTTP Access**: Eclipse's P2 client can directly access the WAR via HTTP/HTTPS:
   - Standard P2 metadata discovery
   - Progressive download of required plugins
   - Version management and update detection

3. **Build Integration**: Integrates with sandbox_product module:
   - Depends on sandbox_product's P2 repository generation
   - Packages output from Tycho build process
   - Preserves P2 metadata integrity

### Maven Build Integration

The module leverages Maven plugins for packaging:

1. **maven-dependency-plugin**: Unpacks feature dependencies
2. **maven-war-plugin**: Creates web application archive
3. **Tycho integration**: Consumes P2 repositories from other modules

## Algorithms and Design Decisions

### WAR Packaging Algorithm

**Decision**: Package entire P2 repository as static web content

**Rationale**:
- P2 repositories are designed for HTTP access
- No server-side logic needed (static files)
- Simple deployment to any web server
- Efficient for Eclipse's P2 client caching

**Process**:
```
1. Tycho builds plugins → sandbox_product generates P2 repo
2. maven-dependency-plugin unpacks features
3. maven-war-plugin packages:
   - Copy plugins/ directory
   - Copy features/ directory  
   - Include P2 metadata (artifacts.jar, content.jar)
   - Include category.xml
4. Output: sandbox-updates.war (deployable archive)
```

### Why WAR Instead of ZIP?

**Decision**: Use WAR packaging instead of ZIP

**Rationale**:
- **Direct Deployment**: WAR files can be deployed to Tomcat/Jetty without extraction
- **Standard Format**: Java web application standard
- **Automated Deployment**: CI/CD tools recognize WAR format
- **Hosting Services**: Many hosting services accept WAR uploads directly

**Trade-offs**:
- Slightly larger than ZIP (includes WEB-INF structure)
- Requires web server (but simple static file server sufficient)
- More flexible than ZIP for adding dynamic features in future

### Dependency Management Strategy

**Decision**: Depend on all `*_feature` modules

**Rationale**:
- Ensures all features are built before packaging
- Maven resolves transitive dependencies automatically
- Single update site contains all sandbox plugins
- Simplifies deployment (one artifact for everything)

**Alternative Considered**: Selective feature inclusion - rejected because:
- Increases complexity (multiple WAR files)
- User confusion (which WAR has which features?)
- Deployment overhead (manage multiple update sites)

## Cross-References

### Root README Sections

This architecture document relates to:

- [Build Instructions](../README.md#build-instructions) - How to build the WAR file
- [Installation](../README.md#installation) - How to use the update site from WAR
- [What's Included](../README.md#whats-included) - Complete list of features packaged in WAR
- [Release Process](../README.md#release-process) - WAR publishing during releases

### Related Modules

- **sandbox_product** - Generates the P2 repository that this module packages
- **All *_feature modules** - Dependencies that provide the actual plugins
- **Root pom.xml** - Defines repository URLs used in P2 metadata

## References

- [P2 Update Sites](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)
- [Maven WAR Plugin](https://maven.apache.org/plugins/maven-war-plugin/)
- [Maven Dependency Plugin](https://maven.apache.org/plugins/maven-dependency-plugin/)
- [Eclipse Tycho](https://tycho.eclipseprojects.io/)

## Summary

The web module is a build packaging component that:
- Creates web-deployable update site from sandbox_product P2 repository
- Enables HTTP-based plugin installation via standard Eclipse update mechanism
- Simplifies distribution and updates through WAR deployment
- Integrates seamlessly with CI/CD and web server infrastructure
