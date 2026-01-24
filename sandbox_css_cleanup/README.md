# CSS Cleanup Plugin

Eclipse plugin for CSS validation and formatting using Prettier and Stylelint.

## Overview

The CSS Cleanup plugin integrates industry-standard CSS tools (Prettier and Stylelint) into Eclipse, providing:

- **Automatic formatting** via Prettier
- **Linting and validation** via Stylelint
- **Right-click menu integration** for .css, .scss, and .less files
- **Preferences page** for configuration
- **Graceful fallback** when npm tools are not installed

## Features

### CSS Formatting with Prettier

- Format CSS files with consistent style
- Supports CSS, SCSS, and LESS files
- Uses stdin/stdout for safe processing
- Configurable via Eclipse preferences

### CSS Validation with Stylelint

- Detect CSS issues and anti-patterns
- Configurable linting rules
- JSON output for detailed error messages
- Auto-fix support (future enhancement)

### Eclipse Integration

- Right-click menu action on CSS files
- Preferences page showing tool status
- Error dialogs with helpful messages
- Works with Eclipse's file history

## Requirements

### External Dependencies

- **Node.js**: Required for running npm tools
- **npm/npx**: Package manager (comes with Node.js)
- **Prettier**: Optional, installed on-demand via npx
- **Stylelint**: Optional, installed on-demand via npx

### Installation

1. Install Node.js from https://nodejs.org/
2. Ensure `node` and `npx` are in your PATH
3. Install the plugin in Eclipse

The plugin will automatically download Prettier and Stylelint via npx when first used.

## Usage

### Formatting CSS Files

1. Right-click on a .css, .scss, or .less file in Project Explorer
2. Select "Format CSS with Prettier" (or "Format SCSS/LESS with Prettier")
3. The file will be formatted in-place with history preserved

### Configuration

1. Go to Window > Preferences > CSS Cleanup
2. Configure options:
   - Enable/disable Prettier formatting
   - Enable/disable Stylelint validation
   - Specify Prettier options (JSON format)
   - Specify Stylelint config file path

### Troubleshooting

**Error: "Node.js is not installed or not in PATH"**
- Install Node.js from https://nodejs.org/
- Add Node.js to your system PATH
- Restart Eclipse

**Error: "Process timed out after 30 seconds"**
- The file may be too large or complex
- Check your internet connection (npx downloads tools on first use)
- Try formatting a smaller file first

**Prettier/Stylelint not working**
- Run `npx prettier --version` in a terminal to verify installation
- Run `npx stylelint --version` in a terminal to verify installation
- Check the Eclipse Error Log for detailed messages

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md) for detailed design documentation.

## Contributing

See [TODO.md](TODO.md) for planned features and known issues.

## License

Eclipse Public License 2.0 - see LICENSE.txt
