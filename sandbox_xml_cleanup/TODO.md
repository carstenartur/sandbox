# XML Cleanup Roadmap

> **Navigation:** [Main README](../README.md) · [Plugin README](README.md) · [Architecture](ARCHITECTURE.md)

## Current status

The PDE XML cleanup is functional through both the JDT cleanup registry and the standalone PDE command. It supports compact and indented serialization, secure XSLT processing, semantic-preservation tests, workspace-safe updates, and filtering to known PDE metadata.

## Completed

- [x] Filter `plugin.xml`, `fragment.xml`, `feature.xml`, `*.exsd`, and `*.xsd`.
- [x] Restrict automatic processing to the project root, `OSGI-INF`, and `META-INF`.
- [x] Exclude unrelated XML such as Maven and Ant build files.
- [x] Disable external DTD and stylesheet access.
- [x] Preserve elements, attributes, namespaces, comments, processing instructions, and meaningful text.
- [x] Strip formatting-only whitespace before serialization.
- [x] Let the XML serializer produce empty-element syntax instead of using element-matching regular expressions.
- [x] Convert groups of four leading spaces to tabs only on serialized markup lines.
- [x] Preserve intentional blank lines and leading spaces inside meaningful text nodes.
- [x] Apply `XML_CLEANUP_INDENT` to the Transformer (`indent=no` by default, `indent=yes` when enabled).
- [x] Verify that compact and indented modes produce different layouts while remaining semantically equal.
- [x] Verify default compact behavior, multiline-text preservation, and idempotency.
- [x] Avoid writing unchanged files and retain Eclipse resource history.
- [x] Provide an independent PDE command/service for non-Java projects.

## Priority work

The production-hardening work below is tracked as one coherent resource-safety effort in #1240.

### P1 — integration safety

- [ ] Add an Eclipse workspace integration test that applies and undoes a PDE XML change through each execution path.
- [ ] Test concurrently dirty editors and external file modifications before applying a change.
- [ ] Verify file charset handling when the Eclipse resource charset is not UTF-8; either preserve the declared/resource charset or reject unsupported input explicitly.
- [ ] Add malformed XML and unavailable stylesheet error-path tests with user-visible status assertions.

### P1 — validation

- [ ] Run PDE/XML schema validation after transformation and reject output that introduces new validation errors.
- [ ] Add realistic `plugin.xml`, `feature.xml`, EXSD, and XSD fixtures containing namespaces, mixed content, `xml:space`, CDATA, and processing instructions.
- [ ] Prove that formatting-only whitespace stripping is safe for every supported document type.

### P2 — scale and ergonomics

- [ ] Benchmark documents above 100 KiB and project-wide batches.
- [ ] Remove the temporary-file round trip or document why it remains preferable to in-memory streams.
- [ ] Add a dry-run preview with file count and byte/diff summary for the standalone PDE command.
- [ ] Make eligible locations configurable only if negative tests continue to protect unrelated XML.
- [ ] Add optional line-ending preservation/normalization policy.

## Known limitations

- The JDT cleanup adapter requires a Java compilation-unit context; the PDE command is the supported independent path.
- Files are currently decoded and written as UTF-8 rather than consulting the Eclipse resource charset.
- The transformer loads each complete document into memory.
- No automatic PDE/schema validation is performed after rewriting.
- Eligible directories and filenames are fixed.

## Acceptance gate for broader use

Before describing the cleanup as production-ready, the project must have:

1. apply/undo integration tests;
2. resource-charset behavior;
3. malformed-input and dirty-editor conflict tests;
4. schema/PDE validation or a documented validation boundary;
5. performance measurements for representative project batches.
