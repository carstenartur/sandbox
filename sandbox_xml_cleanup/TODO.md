# XML Cleanup Roadmap

> **Navigation:** [Main README](../README.md) · [Plugin README](README.md) · [Architecture](ARCHITECTURE.md)

## Current status

The PDE XML cleanup is available through both the JDT cleanup registry and the standalone PDE command. It supports compact and indented serialization, secure XSLT processing, deterministic charset/BOM handling, dirty-buffer rejection, optimistic resource-conflict checks, well-formed output validation, and filtering to known PDE metadata.

## Completed

- [x] Filter `plugin.xml`, `fragment.xml`, `feature.xml`, `*.exsd`, and `*.xsd`.
- [x] Restrict automatic processing to the project root, `OSGI-INF`, `META-INF`, and `schema` folders.
- [x] Exclude unrelated XML such as Maven and Ant build files.
- [x] Disable external DTD and stylesheet access.
- [x] Preserve elements, attributes, namespaces, comments, processing instructions, and meaningful text.
- [x] Strip formatting-only whitespace before serialization.
- [x] Let the XML serializer produce empty-element syntax instead of using element-matching regular expressions.
- [x] Convert groups of four leading spaces to tabs only on serialized markup lines.
- [x] Preserve intentional blank lines and leading spaces inside meaningful text nodes.
- [x] Apply `XML_CLEANUP_INDENT` directly to the Transformer.
- [x] Verify compact/indented layouts, default compact behavior, multiline text, and idempotency.
- [x] Avoid writing unchanged files and retain Eclipse resource history.
- [x] Provide an independent PDE command/service for non-Java projects.
- [x] Remove the physical-path and temporary-output-file requirement from workspace transformations.
- [x] Resolve BOM, UTF-16 signature, XML declaration, resource charset, and UTF-8 fallback in a documented order.
- [x] Preserve UTF-8/UTF-16 BOMs and reject BOM/declaration conflicts or unsupported encodings.
- [x] Reject dirty editor buffers before analysis and before write.
- [x] Recheck resource stamp and original bytes immediately before write.
- [x] Reject malformed transformed XML before changing the workspace.
- [x] Apply a deterministic 16 MiB input limit.

## Remaining priority work in #1240

### P1 — atomic integration and conflict fixtures

- [ ] Replace direct resource application in the JDT bridge with one multi-file LTK `Change` that supports preview, atomic apply, and undo.
- [ ] Add workspace integration tests that apply and undo a PDE XML change through both execution paths.
- [ ] Add dirty connected-buffer and modified-after-analysis integration fixtures.
- [ ] Add linked-resource and non-local-storage fixtures.
- [ ] Add unavailable-stylesheet and unsupported-charset user-visible status assertions.

### P1 — schema-aware validation

- [ ] Run PDE/XML schema validation after transformation and reject newly introduced errors.
- [ ] Define and test validation-error baseline comparison for documents that are already invalid.
- [ ] Add realistic `plugin.xml`, `feature.xml`, EXSD, and XSD fixtures with namespaces, mixed content, `xml:space`, CDATA, and processing instructions.
- [ ] Prove formatting-only whitespace stripping is safe for each supported document type.

### P2 — scale and ergonomics

- [ ] Benchmark documents above 100 KiB and project-wide batches.
- [ ] Measure memory and latency near the 16 MiB boundary and decide whether streaming is required.
- [ ] Add cancellation checkpoints during project traversal and before expensive validation.
- [ ] Add a dry-run preview with file count and byte/diff summary for the standalone PDE command.
- [ ] Make eligible locations configurable only if negative tests continue to protect unrelated XML.
- [ ] Expose line-ending normalization as an explicit policy if users need a mode other than preserving the source convention.

## Known limitations

- The JDT cleanup adapter requires a Java compilation-unit context; the PDE command is the independent path.
- The transformer materializes complete documents in memory and rejects files larger than 16 MiB.
- The current JDT resource application is not yet represented as one atomic multi-file LTK change.
- Well-formedness is enforced, but PDE/schema-aware validation is not yet automatic.
- Eligible directories and filenames are fixed.

## Acceptance gate for production-ready status

Before describing the cleanup as production-ready, the project must have:

1. atomic preview/apply/undo integration tests;
2. dirty-editor, external-change, linked-resource, and charset fixtures;
3. malformed-input and unavailable-stylesheet status tests;
4. schema/PDE validation with an explicit pre-existing-error policy; and
5. performance and cancellation evidence for representative project batches.
