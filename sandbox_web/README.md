# Web Module

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Web** module packages the sandbox P2 update site as a WAR (Web Application Archive) file for deployment to a web server. This allows hosting the P2 repository so users can install sandbox plugins directly into their Eclipse installations via the Update Manager.

## Key Features

- 🌐 **P2 Update Site** - Distributable update site for Eclipse
- 📦 **WAR Packaging** - Deploy to any servlet container
- 🚀 **Easy Installation** - Users install via Eclipse Update Manager
- 🔄 **Automatic Updates** - Eclipse can check for updates
- 🏗️ **Maven Build** - Built with `-Dinclude=web` flag

## Quick Start

### Building the WAR

```bash
# Build with web module
mvn -Dinclude=web -Pjacoco verify

# WAR file location:
# sandbox_web/target/sandbox_web-1.2.1-SNAPSHOT.war
```

### Deploying to Tomcat

```bash
# Copy WAR to Tomcat
cp sandbox_web/target/sandbox_web-*.war /path/to/tomcat/webapps/sandbox.war

# Start Tomcat
/path/to/tomcat/bin/startup.sh

# Update site available at:
# http://localhost:8080/sandbox/
```

### Installing in Eclipse

1. **Open Eclipse**
   - Help → Install New Software

2. **Add Repository**
   - Click "Add..."
   - Name: Sandbox Plugins
   - Location: `http://your-server:8080/sandbox/`

3. **Select Features**
   - Check desired sandbox features
   - Click "Next" and follow wizard

4. **Restart Eclipse**
   - Restart when prompted
   - Plugins are now available

## P2 Update Site Structure

### Repository Contents

The P2 repository includes:

```
sandbox_web/
├── features/           # Eclipse features
│   ├── sandbox_encoding_quickfix_feature_*.jar
│   ├── sandbox_junit_cleanup_feature_*.jar
│   └── ...
├── plugins/            # Eclipse plugins
│   ├── sandbox_encoding_quickfix_*.jar
│   ├── sandbox_junit_cleanup_*.jar
│   └── ...
├── artifacts.jar       # P2 artifact metadata
├── content.jar         # P2 content metadata
└── p2.index           # P2 repository index
```

### Metadata Files

- **artifacts.jar** - Contains artifact descriptors (JAR checksums, etc.)
- **content.jar** - Contains installable unit metadata (dependencies, etc.)
- **p2.index** - Index file for P2 repository discovery

## Update Site Categories

Features are organized into categories:

```xml
<category-def name="sandbox.cleanups" label="Sandbox Cleanups">
  <description>Code cleanup plugins for Java development</description>
</category-def>

<category-def name="sandbox.tools" label="Sandbox Tools">
  <description>Development tools and utilities</description>
</category-def>
```

### Available Categories

- **Sandbox Cleanups** - All cleanup plugins
- **Sandbox Tools** - Extra Search, Usage View
- **Sandbox Infrastructure** - Common utilities

## Hosting Options

### Option 1: GitHub Pages

Host update site on GitHub Pages:

```bash
# Copy P2 repository to docs/ or gh-pages branch
cp -r sandbox_web/target/repository/* docs/

# Commit and push
git add docs/
git commit -m "Update P2 repository"
git push

# Enable GitHub Pages in repository settings
# Update site URL: https://username.github.io/sandbox/
```

### Option 2: Web Server

Deploy to Apache/Nginx:

```bash
# Extract WAR contents
mkdir /var/www/sandbox
unzip sandbox_web/target/sandbox_web-*.war -d /var/www/sandbox

# Configure web server
# Point document root to /var/www/sandbox
```

### Option 3: Servlet Container

Deploy WAR to Tomcat/Jetty:

```bash
# Copy WAR to webapps
cp sandbox_web-*.war $TOMCAT_HOME/webapps/

# Access at http://server:8080/sandbox_web-1.2.1-SNAPSHOT/
```

### Option 4: Composite Repository

Create composite update site combining multiple versions:

```xml
<repository name="Sandbox Composite" type="composite">
  <children>
    <child location="releases/1.2.0/"/>
    <child location="releases/1.2.1/"/>
    <child location="snapshots/"/>
  </children>
</repository>
```

## Version Management

### Release vs Snapshot

- **Release**: `1.2.1` - Stable, tested version
- **Snapshot**: `1.2.1-SNAPSHOT` - Development version

### Multiple Versions

Maintain multiple update site versions:

```
/var/www/sandbox/
├── 1.2.0/          # Stable release
├── 1.2.1/          # Latest release
├── snapshots/      # Development snapshots
└── index.html      # Landing page
```

## Update Site Usage

### Installation

Users install via:
```
Help → Install New Software → Add...
Location: http://your-server/sandbox/
```

### Updates

Eclipse checks for updates:
```
Help → Check for Updates
```

### Marketplace

Optionally publish to Eclipse Marketplace:
- More discoverable
- Better exposure
- Integrated installation

## Web Interface

### Landing Page

Create `index.html` for update site:

```html
<!DOCTYPE html>
<html>
<head>
  <title>Sandbox Plugins - Eclipse Update Site</title>
</head>
<body>
  <h1>Sandbox Plugins for Eclipse</h1>
  
  <h2>Installation</h2>
  <ol>
    <li>Open Eclipse</li>
    <li>Help → Install New Software</li>
    <li>Add: http://your-server/sandbox/</li>
    <li>Select features and install</li>
  </ol>
  
  <h2>Available Features</h2>
  <ul>
    <li>Encoding Quickfix</li>
    <li>JUnit Cleanup</li>
    <li>Functional Converter</li>
    <!-- ... -->
  </ul>
</body>
</html>
```

### Documentation Links

Include links to:
- Plugin documentation
- User guides
- GitHub repository
- Issue tracker

## Build Configuration

### Maven POM

```xml
<plugin>
  <groupId>org.eclipse.tycho</groupId>
  <artifactId>tycho-p2-repository-plugin</artifactId>
  <executions>
    <execution>
      <phase>package</phase>
      <goals>
        <goal>archive-repository</goal>
      </goals>
    </execution>
  </executions>
</plugin>

<plugin>
  <artifactId>maven-war-plugin</artifactId>
  <configuration>
    <webResources>
      <resource>
        <directory>${project.build.directory}/repository</directory>
      </resource>
    </webResources>
  </configuration>
</plugin>
```

### Include Web in Build

The web module is optional:

```bash
# Without web
mvn verify

# With web
mvn verify -Dinclude=web
```

## Documentation

- **[Architecture](ARCHITECTURE.md)** - WAR packaging details
- **[TODO](TODO.md)** - Update site improvements
- **[P2 Documentation](https://wiki.eclipse.org/Equinox/p2)** - Eclipse P2 reference

## Troubleshooting

### Update Site Not Found

**Symptom**: Eclipse can't find update site

**Solutions**:
- Verify URL is accessible in browser
- Check web server is running
- Verify artifacts.jar and content.jar exist
- Check for CORS issues

### Installation Fails

**Symptom**: Features won't install

**Solutions**:
- Check Eclipse version compatibility
- Verify dependencies are satisfied
- Review Eclipse error log
- Try with fresh Eclipse installation

### Updates Not Available

**Symptom**: Eclipse doesn't find updates

**Solutions**:
- Rebuild update site with new version
- Clear Eclipse update cache
- Force update check
- Verify version numbers increased

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **See Also**: [Installation](../README.md#installation) - User installation guide
