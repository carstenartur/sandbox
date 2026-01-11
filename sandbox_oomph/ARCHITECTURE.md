# Oomph Setup - Architecture

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

## References

- [Eclipse Oomph](https://projects.eclipse.org/projects/tools.oomph)
- [Oomph Authoring Guide](https://wiki.eclipse.org/Eclipse_Oomph_Authoring)

## Summary

The Oomph module provides automated development environment setup:
- Simplifies onboarding process
- Ensures consistent developer configuration
- Reduces manual setup errors
- Improves contributor experience
