# Web Module (WAR Packaging) - Architecture

## Overview

The **Web Module** (`sandbox_web`) packages the P2 update site into a WAR (Web Application Archive) file for deployment to web servers. This enables users to install sandbox plugins via HTTP/HTTPS from Eclipse.

## Purpose

- Package P2 update site as web-deployable WAR file
- Enable HTTP/HTTPS-based plugin installation
- Provide hosted update site for Eclipse users
- Include all feature plugins in single archive

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

## References

- [P2 Update Sites](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.isv/guide/p2_publisher.html)
- [Maven WAR Plugin](https://maven.apache.org/plugins/maven-war-plugin/)

## Summary

The web module is a build packaging component that:
- Creates web-deployable update site
- Enables HTTP-based plugin installation
- Simplifies distribution and updates
