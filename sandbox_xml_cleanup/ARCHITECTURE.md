# XML Cleanup Architecture

> **Navigation:** [Main README](../README.md) · [Plugin README](README.md) · [TODO](TODO.md)

## Purpose

The XML cleanup normalizes Eclipse PDE metadata while preserving XML semantics. Automatic processing is limited to `plugin.xml`, `fragment.xml`, `feature.xml`, `*.exsd`, and `*.xsd` in the project root, `OSGI-INF`, `META-INF`, or a `schema` folder. Unrelated XML such as Maven and Ant build files is excluded.

## Execution paths

### Eclipse Java cleanup integration

`XMLCleanUpCore` participates in the JDT cleanup registry. `XMLPlugin` discovers eligible files in the owning project and prepares file-local operations. This adapter still requires a Java compilation-unit cleanup context.

### PDE command integration

`XMLCleanupHandler` and `XMLCleanupService` process selected PDE XML files or complete projects without a Java source context.

Both paths use the same resource-safety and transformation pipeline.

## Resource-safety boundary

`XMLResourceSupport` owns all workspace reads and writes.

```text
eligible IFile
    │
    ├─ reject dirty connected text buffer
    ├─ read original bytes and modification stamp
    ├─ resolve charset and BOM
    ├─ transform decoded content in memory
    ├─ validate well-formed output with external access disabled
    ├─ encode with the original charset and BOM
    └─ before write: recheck dirty state, stamp, and original bytes
```

### Charset precedence

The effective charset is resolved in this order:

1. UTF-8, UTF-16LE, or UTF-16BE byte-order mark;
2. unambiguous UTF-16 byte signature;
3. XML declaration;
4. Eclipse resource charset metadata;
5. UTF-8 fallback.

A BOM/byte signature and XML declaration must agree. Unsupported encodings, malformed byte sequences, or ambiguous declarations fail closed. UTF-8 and UTF-16 BOMs are preserved exactly. The Eclipse resource charset is synchronized with the effective charset after a successful write.

### Conflict policy

A candidate retains the original byte array and resource modification stamp. Immediately before writing, the cleanup rejects the operation when:

- the file has unsaved editor changes;
- the resource no longer exists;
- the modification stamp changed; or
- the current bytes no longer match the analysed bytes.

This prevents both execution paths from silently overwriting editor or external changes. Workspace history is retained.

### Size boundary

The implementation materializes the complete document in memory and therefore rejects inputs larger than 16 MiB. The limit is deterministic and shared by both execution paths. Representative 100 KiB and project-batch performance measurements remain required before broader production claims.

## Transformation pipeline

`SchemaTransformationUtils` transforms decoded XML content through the bundled `formatter.xsl`; it no longer requires a physical resource location or temporary output file.

- `XMLConstants.FEATURE_SECURE_PROCESSING` is enabled.
- External DTD and stylesheet access are disabled.
- Output encoding is set to the resolved source charset.
- Compact mode uses `indent=no`; readable mode uses `indent=yes`.
- Groups of four leading spaces are converted to tabs only on serialized markup lines.
- Elements, attributes, namespaces, comments, processing instructions, CDATA-derived text, mixed content, and meaningful whitespace are preserved by the identity-style stylesheet.

After transformation, `XMLResourceSupport` parses the output with external DTD/schema/entity access disabled. Malformed output is rejected before any workspace write.

## Main components

### `XMLResourceSupport`

Shared workspace boundary for charset/BOM handling, dirty-buffer checks, optimistic conflict detection, size limits, well-formed validation, and byte-preserving writes.

### `SchemaTransformationUtils`

Content-based secure XSLT transformation and deterministic layout rendering. The legacy `Path` overload remains for compatibility and assumes UTF-8; workspace callers use the content/charset overload.

### `XMLPlugin`

Adapter for the JDT cleanup lifecycle. It discovers relevant resources, prepares validated transformations, avoids duplicate project resources, and applies only candidates whose original resource state is still current.

### `XMLCleanupService`

Standalone PDE command service. It uses the same prepared transformation and write checks as the JDT adapter.

### `XMLCandidateHit` / `XMLCleanUpFixCore`

Bridge objects used by the existing Sandbox cleanup infrastructure. A candidate contains the complete prepared `XMLResourceSupport.Transformation`, not only two unqualified UTF-8 strings.

## Validation boundary

The current automatic post-transform gate proves XML well-formedness and blocks external entity/schema access. It does **not** yet run PDE model validation, EXSD/XSD schema validation, or compare validation-error baselines. Those requirements remain open in #1240 and must be implemented before describing the cleanup as production-ready for arbitrary PDE metadata.

## Tests

The XML test module verifies:

- compact versus indented serialization and idempotency;
- comment, mixed-content, namespace, CDATA, and meaningful-whitespace preservation;
- eligible and ignored file selection;
- UTF-8 metadata decoding;
- UTF-8 BOM preservation;
- ISO-8859-1 declaration precedence;
- UTF-16LE BOM decoding;
- BOM/declaration conflict rejection; and
- malformed transformation-output rejection.

Workspace integration tests for dirty buffers, external modification conflicts, linked resources, multi-file preview/apply/undo, and validation-error baselines remain tracked by #1240.

## Current limitations

- The JDT cleanup adapter requires a Java compilation-unit context; the PDE command is the independent path.
- The complete document is materialized in memory, bounded at 16 MiB.
- The current JDT bridge still performs resource application through the existing cleanup operation rather than a dedicated multi-file LTK `Change`; atomic preview/apply/undo remains open.
- PDE/schema-aware validation is not yet performed.
- Eligible directories and filenames are fixed.

## Extension rules

New XML types or locations require:

1. an explicit eligibility rule;
2. semantic-equivalence fixtures;
3. a negative test proving unrelated XML remains untouched;
4. mixed-content and meaningful-whitespace tests;
5. charset/BOM fixtures; and
6. documentation of the applicable validation model.
