# XML Cleanup Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **XML Cleanup** plugin optimizes Eclipse PDE XML files (plugin.xml, feature.xml, etc.) by reducing whitespace in known element-only containers. Optional indentation uses tabs in those containers. Documentation, mixed content, and unknown extension payloads retain their whitespace.

## Key Features

- 📉 **Size Reduction** - Remove unnecessary whitespace from XML files
- 🎯 **PDE-Specific** - Focuses on Eclipse plugin descriptor files
- 📏 **Indentation** - Generate tab indentation in known structural containers (optional)
- ♻️ **Idempotent** - Running multiple times produces same result
- ✅ **Semantic Preservation** - XML structure and content unchanged

## Quick Start

### Enable in Eclipse

1. Open **Source** → **Clean Up...**
2. Navigate to the **XML** category
3. Enable **Optimize PDE XML files**
4. Optionally enable **Enable XML indentation (increases file size)**

### Example Transformations

**Before:**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin>
    <extension point="org.eclipse.ui.views">
        <view
            class="com.example.MyView"
            id="com.example.myview"
            name="My View" />
    </extension>
</plugin>
```

**After (Size Optimized):**
```xml
<?xml version="1.0" encoding="UTF-8"?><plugin><extension point="org.eclipse.ui.views"><view class="com.example.MyView" id="com.example.myview" name="My View"/></extension></plugin>
```

**After (Indentation Enabled):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<plugin>
→ <extension point="org.eclipse.ui.views">
→ → <view class="com.example.MyView" id="com.example.myview" name="My View"/>
→ </extension>
</plugin>
```
(where `→` represents a tab character)

## Supported Files

The cleanup processes specific PDE files:

| File Type | Description |
|-----------|-------------|
| `plugin.xml` | Eclipse plugin manifest |
| `feature.xml` | Feature descriptor |
| `fragment.xml` | Fragment manifest |
| `*.exsd` | Extension point schema |
| `*.xsd` | XML schema definitions |

**Locations**: Only in project root, `OSGI-INF/`, or `META-INF/` directories.

## What Gets Optimized

### Whitespace Reduction
- Remove whitespace-only text in known structural PDE descriptor and XSD/EXSD containers
- Preserve complete documentation, application-info, and unknown extension payload subtrees
- Preserve mixed content, blank text values, and inherited `xml:space="preserve"`
- Consolidate spacing between attributes

### Indentation (Optional)
- Generate one tab per level in known structural containers
- Retain all text inside unknown elements and text subtrees
- Never rewrite text using a leading-space substitution after serialization

## Guarantees

✅ **Semantic Equivalence**: The XML structure and content remain identical
- Verified using DOM comparisons, including exact whitespace-sensitive regression tests
- Element hierarchy preserved
- Attribute values unchanged
- Text content intact

✅ **Idempotent**: Second run produces no changes
- Consistent formatting
- Stable output

✅ **Safe Indentation**: Layout changes are limited to known structural containers
- XML attribute values unchanged
- Text content spacing preserved
- Only structural indentation modified

## Benefits

### Size Reduction
- Smaller files load faster
- Reduced storage requirements
- Faster git operations

### Consistency
- Standardized formatting across team
- Consistent indentation style
- Predictable file structure

### Version Control
- Cleaner diffs
- Easier to review changes
- Less merge conflicts from formatting

## Configuration

Configure cleanup options:
1. **Window** → **Preferences** → **Sandbox** → **XML Cleanup**
2. Enable size optimization
3. Configure XML indentation (ON/OFF)
4. Set indentation preferences

## Example Results

Typical size reductions:
- Small plugin.xml: 10-15% smaller
- Medium plugin.xml: 15-25% smaller
- Large feature.xml: 20-30% smaller

## Documentation

- **[Architecture](ARCHITECTURE.md)** - Implementation details
- **[TODO](TODO.md)** - Future enhancements
- **[Main README](../README.md#xml-cleanup)** - Detailed examples

## Testing

Comprehensive tests in `sandbox_xml_cleanup_test`:
- Size reduction verification
- Semantic equality and whitespace-sensitive DOM tests
- Idempotency tests
- Tab conversion tests
- PDE file filtering tests

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_xml_cleanup_test
```

## Limitations

1. **PDE Files Only** - Only processes plugin.xml, feature.xml, etc.
2. **Location Restricted** - Files must be in project root, OSGI-INF, or META-INF
3. **Conservative Formatting** - Unknown content models retain their whitespace, so compact output can still contain line breaks and indentation
4. **No Schema Validation** - Doesn't validate XML against schemas

See [TODO.md](TODO.md) for planned improvements.

## Safety

The cleanup is safe to use:
- Preserves XML semantics
- Verified by comprehensive tests
- Only modifies whitespace
- No data loss

Always use version control when running automated cleanups!

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Common Utilities](../sandbox_common/) for shared XML processing
