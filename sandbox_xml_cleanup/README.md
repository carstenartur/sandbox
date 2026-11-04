# XML Cleanup Plugin

> **Navigation**: [Main README](../README.md) | [Architecture](ARCHITECTURE.md) | [TODO](TODO.md)

## Overview

The **XML Cleanup** plugin formats selected Eclipse PDE XML files (plugin.xml, feature.xml, etc.) through explicit PDE commands. Compact formatting removes layout whitespace in known element-only containers. Documentation, mixed content, and unknown extension payloads retain their whitespace.

## Key Features

- 📉 **Size Reduction** - Remove unnecessary whitespace from XML files
- 🎯 **PDE-Specific** - Focuses on Eclipse plugin descriptor files
- 📏 **Conservative Formatting** - Preserve text outside known structural containers
- ♻️ **Idempotent** - Running multiple times produces same result
- ✅ **Semantic Preservation** - XML structure and content unchanged

## Quick Start

### Run the PDE command

1. Select supported XML files or a project in **Package Explorer** or **Project Explorer**.
2. Choose **Clean Up PDE XML** from the context menu, or **XML Cleanup** → **Clean Up PDE XML Files** from the main menu.
3. Review the resulting changes in version control. The command writes the selected resources directly and preserves Eclipse resource history.

To inspect opportunities first, choose **Find PDE XML Cleanup Problems**. Analysis creates Problems markers without rewriting files; the **Normalize PDE XML formatting** marker resolution applies formatting explicitly.

XML formatting is no longer registered in Java **Source** → **Clean Up...** profiles or Java save actions. Existing XML settings in stored Java profiles are ignored. Use the PDE commands for XML resources, including projects without Java source.

### Modernize metadata for a known target

When the schema's minimum target is Eclipse 3.4 or newer, choose **Modernize
PDE appinfo (Eclipse 3.4+)** in the context menu of a schema/project or in
**XML Cleanup** on the main menu. This explicit standalone command chooses the
new convention and applies compact formatting. The cleanup does not infer the
target from the running IDE or from a project. The ordinary cleanup command
and formatting marker fixes preserve existing case.

The target option is available through this standalone command. JDT cleanup
profiles and Java save actions cannot represent changes to unrelated XML files
with their compilation-unit change API.

This option changes legacy `appInfo` to `appinfo` only for recognized
`meta.schema`, `meta.element`, `meta.attribute`, and `meta.section` containers in
their PDE annotation contexts. Attributes and documentation stay unchanged.
Recognized metadata whitespace receives the ordinary cleanup formatting in the
same pass. Unknown or mixed payloads, `xml:space="preserve"`, and CDATA-containing
files are retained. This is a compatibility choice, not a size optimization.

### Trim plain reference-section boundaries

For standalone PDE use, choose **Trim Plain PDE Reference Sections** in a
schema/project context menu or the **XML Cleanup** main menu. This explicit
command applies compact formatting and removes outer XML whitespace only from
plain, nonblank documentation in the `since`, `apiinfo`,
`implementation`, and `copyright` reference sections.

The ordinary cleanup command and marker fixes preserve all documentation text.
Overview, element, and attribute descriptions retain their original boundaries:
PDE's lazy readers expose those strings verbatim. Examples, markup sections,
unknown section types, HTML/pre/code text, mixed content, `xml:space="preserve"`,
blank documentation, and CDATA-containing files remain unchanged. Interior
spaces, tabs, and line breaks are never normalized by this option.

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

## Supported Files

The cleanup processes specific PDE files:

| File Type | Description |
|-----------|-------------|
| `plugin.xml` | Eclipse plugin manifest |
| `feature.xml` | Feature descriptor |
| `fragment.xml` | Fragment manifest |
| `*.exsd` | Extension point schema |
| `*.xsd` | XML schema definitions |

**Locations**: The project root or within `OSGI-INF/`, `META-INF/`, or `schema/` directories.

## What Gets Optimized

### Whitespace Reduction
- Remove whitespace-only text in known structural PDE descriptor and XSD/EXSD containers
- Compact `appinfo` and legacy `appInfo` only when they contain recognized PDE attribute-only metadata (`meta.schema`, `meta.section`, `meta.element`, or `meta.attribute`) in the matching annotation context
- Remove trailing layout spaces and collapse blank metadata leaves to empty-element syntax; retain names and attribute values
- Preserve complete documentation and unknown application-info or extension payloads, including whitespace-only documentation required by older PDE readers
- Leave files containing CDATA unformatted after secure validation, preserving literal sections across repeated cleanup passes
- Preserve mixed content, blank text values, and inherited `xml:space="preserve"`
- Consolidate spacing between attributes

### Text Preservation
- Retain all text inside unknown elements and text subtrees
- Never rewrite text using a leading-space substitution after serialization

## Default formatting guarantees

✅ **Semantic Equivalence**: The XML structure and content remain identical
- Verified using DOM comparisons, including exact whitespace-sensitive regression tests
- Element hierarchy preserved
- Attribute values unchanged
- Text content intact

✅ **Idempotent**: Second run produces no changes
- Consistent formatting
- Stable output

✅ **Conservative Layout**: Layout changes are limited to known structural containers
- XML attribute values unchanged
- Text content spacing preserved
- Only structural indentation modified

## Benefits

### Size Reduction
- Compact structural layout can reduce file size
- Size reduction alone does not establish a startup or loading-time improvement

### Consistency
- Standardized formatting across team
- Consistent indentation style
- Predictable file structure

### Version Control
- Cleaner diffs
- Easier to review changes
- Less merge conflicts from formatting

## Configuration

The PDE commands and marker resolutions use compact formatting. They do not read Java cleanup profile settings. The transformer retains an indentation option for programmatic callers; it is not an option in the standalone command UI.

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
- Native JDT calculation tests proving Java cleanup cannot write XML resources
- Registry checks retaining the standalone command and removing Java cleanup/save-action contributions

Run tests:
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_xml_cleanup_test
```

## Limitations

1. **PDE Files Only** - Only processes plugin.xml, feature.xml, etc.
2. **Location Restricted** - Files must be in the project root or within OSGI-INF, META-INF, or schema folders
3. **Conservative Formatting** - Unknown content models retain their whitespace, so compact output can still contain line breaks and indentation
4. **No Schema Validation** - Doesn't validate XML against schemas
5. **Explicit Invocation** - XML formatting is unavailable through Java cleanup profiles and Java save actions
6. **No Atomic Multi-file Preview/Undo** - The standalone command writes each eligible file directly; a resource-aware LTK refactoring remains future work

See [TODO.md](TODO.md) for planned improvements.

## Safety

Before writing, the workspace service checks for dirty editor buffers, changed resource stamps or bytes, encoding conflicts, and malformed output. It retains Eclipse resource history. These checks do not provide schema-aware validation or atomic multi-file undo; review the resulting version-control diff.

## License

Eclipse Public License 2.0 (EPL-2.0)

---

> **Related Plugins**: [Common Utilities](../sandbox_common/) for shared XML processing
