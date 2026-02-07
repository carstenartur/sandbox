# Oomph Setup Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **Oomph Setup** plugin provides Eclipse Oomph setup configurations for the Sandbox project. Oomph is Eclipse's automated workspace configuration tool, allowing developers to set up their development environment with a single click.

## Key Features

- 🚀 **One-Click Setup** - Automated workspace configuration
- 🎯 **Project Preferences** - Pre-configured Eclipse settings
- 📦 **Required Plugins** - Automatic installation of dependencies
- 🔧 **Git Configuration** - Clones repository and sets up branches
- 🔌 **Eclipse Integration** - Works with Eclipse Installer

## Quick Start

### Using Oomph Setup

1. **Download Eclipse Installer**
   - Get from [eclipse.org/downloads](https://www.eclipse.org/downloads/)

2. **Import Setup File**
   - Open Eclipse Installer
   - Switch to Advanced Mode
   - Add sandbox.setup file from this plugin

3. **Configure**
   - Select sandbox project setup
   - Configure installation location
   - Review settings

4. **Install**
   - Click Next to start installation
   - Eclipse will download, configure, and launch
   - Workspace is ready to use

## What Gets Configured

### Eclipse Preferences
- Code formatting rules
- Save actions
- Compiler settings
- Editor preferences
- Clean up profiles

### Project Setup
- Import sandbox modules
- Configure build path
- Set up working sets
- Configure launchers

### Git Setup
- Clone sandbox repository
- Configure remotes
- Set up user credentials
- Initialize branches

### Required Plugins
- Eclipse JDT (Java Development Tools)
- Eclipse PDE (Plugin Development Environment)
- Maven/Tycho integration
- Git integration (EGit)

## Setup Files

The plugin contains:

| File | Purpose |
|------|---------|
| `sandbox.setup` | Main Oomph setup model |
| `preferences.epf` | Eclipse preferences export |
| `launchers/` | Launch configurations |

## Benefits

### For New Contributors
- Get started quickly without manual configuration
- Consistent development environment
- All required tools pre-installed

### For Team
- Standardized workspace settings
- Consistent code formatting
- Reduced setup documentation

### For CI/CD
- Reproducible build environment
- Known working configuration
- Version-controlled setup

## Customization

To customize the setup:

1. **Edit Setup Model**
   - Open `sandbox.setup` in Oomph Setup Editor
   - Modify tasks and preferences
   - Save changes

2. **Export Preferences**
   - Configure Eclipse as desired
   - File → Export → Preferences
   - Save as `preferences.epf`

3. **Add Launch Configs**
   - Create launch configurations
   - Export to `launchers/` directory

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Setup model structure
- **[TODO](TODO.md)** - Planned enhancements
- **[Oomph Documentation](https://wiki.eclipse.org/Eclipse_Oomph_Authoring)** - Official Oomph guide

## Oomph Resources

- **Eclipse Oomph**: [eclipse.org/oomph](https://www.eclipse.org/oomph/)
- **Setup Authoring Guide**: [Oomph Wiki](https://wiki.eclipse.org/Eclipse_Oomph_Authoring)
- **Setup Examples**: [Eclipse Projects](https://git.eclipse.org/c/)

## Maintenance

### Updating Eclipse Target Version

The Eclipse version can be changed even after initial installation:

**Method 1: Using Oomph Preferences (Recommended)**
1. In Eclipse: `Help` → `Perform Setup Tasks...`
2. In the dialog, find the "Eclipse Release Version" variable
3. Change the value (e.g., from "2025-12" to "2025-09" or "2024-12")
4. Click `OK` to re-trigger setup with the new version
5. Restart Eclipse when prompted
6. The target platform will automatically update to the new version

**Method 2: Edit Setup File**
1. Open `sandbox.setup` in a text editor
2. Locate the `eclipse.target.version` variable
3. Change the default value
4. Save and re-import the setup in Eclipse Installer

### Updating Eclipse Heap Size

The Eclipse heap size can be changed even after initial installation:

**Using Oomph Preferences**
1. In Eclipse: `Help` → `Perform Setup Tasks...`
2. In the dialog, find the "Eclipse Heap Size" variable
3. Change the value (e.g., from "2048m" to "4096m" or "8192m")
4. Click `OK` to re-trigger setup
5. Restart Eclipse when prompted
6. Eclipse will now use the new heap size

**Common Heap Size Values**:
- `2048m` (2 GB) - Default, suitable for most projects
- `4096m` (4 GB) - Recommended for large projects
- `8192m` (8 GB) - For very large projects or workspaces

### Updating Setup

When project structure changes:
1. Update `sandbox.setup` model
2. Export new preferences if needed
3. Test with fresh Eclipse installation
4. Commit changes

### Testing Setup

Test the setup:
1. Delete test workspace
2. Run Eclipse Installer
3. Import updated setup
4. Verify all configuration works
5. Test build and run

## Common Issues

### Setup Fails to Clone Repository

**Cause**: Git credentials not configured

**Solution**: 
- Configure Git credentials in Eclipse Installer
- Use SSH instead of HTTPS
- Set up personal access token

### Missing Plugins

**Cause**: Update site unavailable

**Solution**:
- Check Eclipse version compatibility
- Verify update site URLs
- Update setup with alternative sites

### Preferences Not Applied

**Cause**: Preferences file outdated

**Solution**:
- Export fresh preferences
- Update `preferences.epf`
- Test with clean workspace

## Advanced Usage

### Creating Custom Setups

Create setups for specific use cases:
- Minimal setup (just source code)
- Full setup (with all test modules)
- CI/CD setup (headless build)
- Documentation-only setup

### Sharing Setups

Share your setup:
1. Export setup model
2. Include in repository
3. Document custom tasks
4. Test with team members

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **See Also**: [Eclipse Oomph](https://www.eclipse.org/oomph/) - Official Oomph project
