# XML Cleanup Architecture

> **Navigation:** [Main README](../README.md) · [Plugin README](README.md) · [TODO](TODO.md)

## Purpose

The XML cleanup normalizes Eclipse PDE metadata while preserving XML semantics. It deliberately limits automatic processing to files that belong to an Eclipse plug-in or feature model:

- `plugin.xml`, `fragment.xml`, and `feature.xml`;
- `*.exsd` and `*.xsd`;
- project root, `OSGI-INF`, and `META-INF` locations.

Files such as `pom.xml`, `build.xml`, and arbitrary application XML are excluded.

## Execution paths

### Eclipse Java cleanup integration

`XMLCleanUpCore` participates in the JDT cleanup registry. It reads:

- `MYCleanUpConstants.XML_CLEANUP`;
- `MYCleanUpConstants.XML_CLEANUP_INDENT`.

`XMLPlugin` scans the owning project for eligible PDE files and creates file-local cleanup operations. This path is useful for Java-based plug-in projects but still requires a Java compilation-unit cleanup context.

### PDE command integration

`XMLCleanupHandler` and `XMLCleanupService` provide an independent command/action path. They can process selected PDE XML files or complete projects without requiring a Java source file.

Both paths use `SchemaTransformationUtils`, so serialization and security behavior remain identical.

## Transformation pipeline

```text
eligible IFile / Path
        │
        ├─ secure TransformerFactory configuration
        ├─ formatter.xsl identity-style transformation
        │      └─ strip formatting-only whitespace nodes
        ├─ serialize in compact or indented mode
        └─ convert leading groups of four spaces to tabs
               only on lines whose next character is XML markup (<)
```

No global regex removes line breaks or trailing whitespace from arbitrary text. Meaningful multiline content, intentional blank lines, and leading spaces inside text nodes remain unchanged.

### 1. Secure XML transformation

`SchemaTransformationUtils` enables `XMLConstants.FEATURE_SECURE_PROCESSING` and disables external DTD and stylesheet access. The built-in stylesheet is loaded through the bundle class loader.

The stylesheet copies elements, attributes, comments, processing instructions, namespaces, and meaningful text. `xsl:strip-space` removes text nodes that contain only formatting whitespace before serialization.

### 2. Serialization modes

The cleanup exposes two deterministic layouts:

| Preference | Transformer output | Intended use |
|---|---|---|
| `XML_CLEANUP_INDENT=false` | `OutputKeys.INDENT=no` | Default compact representation and smaller diffs/files |
| `XML_CLEANUP_INDENT=true` | `OutputKeys.INDENT=yes` | Human-readable nested layout |

The preference is applied directly to the `Transformer`; it is not a post-processing approximation. The no-argument `transform(Path)` method is equivalent to `transform(path, false)`.

The XML serializer owns empty-element syntax. The cleanup does not use regular expressions to infer element boundaries.

### 3. Markup-only indentation conversion

After serialization, groups of four leading spaces are converted to tabs only when the line immediately continues with `<`. This safely covers serialized elements, comments, and processing instructions while excluding text lines such as:

```xml
<description>first line

    second line</description>
```

The blank line and four leading spaces before `second line` are preserved. A dedicated regression test protects this behavior.

## Main components

### `SchemaTransformationUtils`

Pure transformation utility for a filesystem `Path`. It owns secure transformer creation, temporary-output cleanup, layout selection, and markup-only indentation conversion.

### `XMLPlugin`

Adapter for the JDT cleanup lifecycle. It:

- discovers PDE-relevant workspace files;
- avoids duplicate processing during one cleanup run;
- passes the configured indentation mode to the transformation utility;
- creates Eclipse workspace changes only when content differs.

### `XMLCleanupService`

Standalone workspace service used by the PDE command. It performs the same eligibility and transformation rules without the Java cleanup lifecycle.

### `XMLCandidateHit` / `XMLCleanUpFixCore`

Bridge objects used by the existing Sandbox cleanup infrastructure to represent an eligible file and its original/transformed content.

## Change and resource safety

- Source files are currently read as UTF-8 and written through Eclipse resource APIs.
- Workspace history is retained when files are updated.
- Temporary transformation files are deleted in `finally` blocks.
- Unchanged output does not create a workspace modification.
- External DTD and stylesheet resolution is disabled.
- Meaningful text is not passed through global whitespace regexes.
- Errors are reported through Eclipse logging/status APIs rather than standard output.

Resource charset, dirty-editor conflict handling, post-transform validation, and large-document behavior are tracked together in issue #1240.

## Tests

The XML test module verifies:

- semantic equality after transformation;
- compact versus indented serialization;
- default compact behavior;
- idempotency;
- comment and meaningful multiline text preservation;
- serializer-produced empty elements;
- markup-only indentation conversion;
- eligible and ignored file selection.

Tests for the indentation preference assert a visible layout difference and semantic equality; they do not accept identical output for both settings.

## Current limitations

- The JDT cleanup adapter still requires a Java cleanup context; use the PDE command for non-Java projects.
- Eligible directory names are fixed rather than configurable.
- The complete file is materialized in memory and transformed through a temporary file; very large XML documents are not streamed.
- XML schema/PDE model validation is not run automatically after transformation.
- Workspace conflict behavior for concurrently edited external files needs a dedicated integration test.
- Resource charset metadata and non-UTF-8 XML are not yet preserved explicitly.

## Extension rules

New XML types or locations must not be added implicitly. Any expansion requires:

1. an explicit eligibility rule;
2. semantic-equivalence fixtures;
3. a negative test proving unrelated XML remains untouched;
4. mixed-content and meaningful-whitespace tests;
5. documentation of whether whitespace-only text is semantically relevant for that format.
