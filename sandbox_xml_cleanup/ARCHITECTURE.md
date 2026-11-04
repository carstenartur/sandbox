# XML Cleanup Architecture

> **Navigation:** [Main README](../README.md) · [Plugin README](README.md) · [TODO](TODO.md)

## Purpose

The XML cleanup normalizes Eclipse PDE metadata while preserving XML semantics. Automatic processing is limited to `plugin.xml`, `fragment.xml`, `feature.xml`, `*.exsd`, and `*.xsd` in the project root, `OSGI-INF`, `META-INF`, or a `schema` folder. Unrelated XML such as Maven and Ant build files is excluded.

## Execution paths

### Former Eclipse Java cleanup integration

XML cleanup is no longer contributed to the JDT cleanup registry, profile UI, or Java save-action UI. The retained `XMLCleanUpCore` compatibility entry point returns no fix, requests no AST, and performs no XML discovery or preparation, even if old profile options remain enabled.

JDT's `ICleanUpFix` returns a Java `CompilationUnitChange`. `CleanUpRefactoring.calculateChange` takes its text edit and constructs a new change for the current Java compilation unit; it does not retain a custom change's resource application or undo behavior. The former XML bridge wrote files during `rewriteAST`, before preview acceptance, then failed because no Java edit existed. An empty or dummy Java edit cannot provide an XML preview/apply/undo contract.

### PDE command integration

`XMLCleanupHandler` and `XMLCleanupService` process selected PDE XML files or complete projects without a Java source context.

The explicit commands and formatting marker resolutions use the same resource-safety and transformation pipeline. They do not depend on Java cleanup settings.

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

This prevents commands and marker resolutions from silently overwriting editor or external changes. Workspace history is retained.

### Size boundary

The implementation materializes the complete document in memory and therefore rejects inputs larger than 16 MiB. The limit is deterministic and shared by both execution paths. Representative 100 KiB and project-batch performance measurements remain required before broader production claims.

## Transformation pipeline

`SchemaTransformationUtils` transforms decoded XML content through the bundled `formatter.xsl`; it no longer requires a physical resource location or temporary output file.

- `XMLConstants.FEATURE_SECURE_PROCESSING` is enabled.
- External DTD and stylesheet access are disabled.
- Output encoding is set to the resolved source charset.
- Serializer indentation stays disabled. The stylesheet removes whitespace-only
  text and optionally generates tab indentation only in known element-only
  containers: root `plugin`, `fragment`, and `feature` descriptors, their direct
  `extension` containers, and common XSD/EXSD schema structures.
- PDE `appinfo`/`appInfo` containers are structural only when every element child
  is recognized attribute-only metadata in its corresponding schema, element, or
  attribute annotation. Metadata with element children, mixed text, or a foreign
  namespace keeps the complete application-info subtree unchanged.
- Unknown elements, extension payloads, and `documentation` retain all text,
  including whitespace-only text nodes. Blank documentation retains a text child
  for released PDE readers. Element names in an unrelated namespace do not
  establish a formatting contract.
- Mixed-content containers and `xml:space="preserve"` subtrees are copied without
  layout changes. This conservative subtree policy also retains descendants that
  specify `xml:space="default"`; that attribute does not prove whitespace is disposable.
- Inputs containing CDATA are validated, then retained without formatting. XSLT
  1.0 otherwise erases CDATA boundaries and a subsequent pass could remove a
  whitespace-only literal. This conservative fallback also preserves lexical
  CDATA sections.
- Elements, attributes, namespaces, comments, and processing instructions are preserved. Generic inter-element whitespace is not
  assumed to be indentation, and no regular-expression rewrite touches text after serialization.

After transformation, `XMLResourceSupport` parses the output with external DTD/schema/entity access disabled. Malformed output is rejected before any workspace write.

### Explicit appinfo target convention

`SchemaTransformationUtils.PdeSchemaTarget.PRESERVE` is the default for existing
callers. `ECLIPSE_3_4_OR_NEWER` enables the stylesheet's `modernize-appinfo`
parameter. This follows the PDE schema writer's 3.4 threshold and deliberately
does not guess a project's target. Only legacy `appInfo` with recognized,
attribute-only PDE metadata in its reader context is renamed. The same pass
formats recognized metadata whitespace using the ordinary cleanup rules.
Attributes and namespaces remain intact; unknown payloads are copied as before.

The dedicated standalone command uses `PdeAppinfoModernizationHandler` to
construct a fresh `XMLCleanupService` with an immutable target. That value
passes through the resource preparation call for this invocation; no singleton
or shared mutable profile state stores it. Ordinary commands and marker fixes
keep `PRESERVE`. Interleaved and concurrent workspace tests verify independent
command choices through actual file writes.

The new option has no JDT cleanup/profile/save-action registration. JDT's
`ICleanUpFix` produces a compilation-unit change, and its cleanup refactoring
rebuilds that change for the Java compilation unit. The standalone command uses
the guarded XML workspace prepare/write boundary. CDATA sources are validated
but retained without serialization because XSLT loses their lexical boundaries.

Tests compare full and abbreviated PDE models, the lightweight
`SchemaAnnotationHandler` result, and generated HTML, as well as exact preserved
subtrees, explicit/default target behavior, namespace handling, and idempotence.

### Optional reference-section boundaries

`SchemaTransformationUtils.DocumentationPolicy.PRESERVE` is the default for
existing callers. `TRIM_PLAIN_SECTION_BOUNDARIES` removes only outer XML
whitespace in plain, nonblank documentation for the known `since`,
`apiinfo`, `implementation`, and `copyright` sections. The stylesheet
requires the unqualified PDE vocabulary, a root schema annotation containing
`meta.schema`, and an ordinary two-child reference annotation: one recognized
`appInfo`/`appinfo` metadata container followed by its documentation.
Unknown payloads or ambiguous ordering are excluded.

The full PDE loader already trims those section boundaries before exposing
`DocumentSection` text or generating HTML. Abbreviated loading omits section
descriptions, and `SchemaAnnotationHandler` excludes `meta.section` text.
By contrast, overview, element, and attribute descriptions have raw lightweight
or lazy readers. They remain untouched. Tests use different real source URLs
for original and transformed schemas and compare the lazy getters directly;
they never normalize observed strings or reuse the original URL for both sides.

Literal HTML/pre/code, examples, markup sections, unknown sections, non-text
children, effective `xml:space="preserve"`, and blank documentation are
protected. Blank documentation retains a text node for released PDE readers.
CDATA sources are securely validated but retained without serialization.
The trim routine uses binary subdivision to bound recursion depth for long
whitespace runs; `normalize-space` is only a predicate and never supplies
replacement text, so interior spacing is preserved.

The explicit standalone command configures a new `XMLCleanupService` whose
policy is immutable. Ordinary commands and marker fixes retain `PRESERVE`.
Both paths share charset/BOM handling, well-formedness checks, dirty-buffer
protection, and optimistic resource-conflict detection.

Both options are captured independently in each service instance. Combined API
callers can select both; each single-option command and overload uses `PRESERVE`
for its other option.

## Main components

### `XMLResourceSupport`

Shared workspace boundary for charset/BOM handling, dirty-buffer checks, optimistic conflict detection, size limits, well-formed validation, and byte-preserving writes.

### `SchemaTransformationUtils`

Content-based secure XSLT transformation and deterministic layout rendering. The legacy `Path` overload remains for compatibility and assumes UTF-8; workspace callers use the content/charset overload.

### `XMLPlugin`

Legacy operation helper retained with the former JDT adapter. The inert `XMLCleanUpCore` no longer invokes it, and there is no JDT extension registration for XML cleanup.

### `XMLCleanupService`

Standalone PDE command and marker-resolution service. It prepares a transformation in memory, then applies it through the shared guarded workspace boundary after explicit invocation.

### `XMLCandidateHit` / `XMLCleanUpFixCore`

Retained legacy bridge objects. They are not used by the retired Java cleanup entry point. The standalone service uses `XMLResourceSupport.Transformation` directly.

## Validation boundary

The current automatic post-transform gate proves XML well-formedness and blocks external entity/schema access. It does **not** yet run PDE model validation, EXSD/XSD schema validation, or compare validation-error baselines. Those requirements remain open in #1240 and must be implemented before describing the cleanup as production-ready for arbitrary PDE metadata.

## Tests

The XML test module verifies:

- compact versus indented serialization and idempotency;
- comment, mixed-content, namespace, CDATA, and meaningful-whitespace preservation,
  with exact DOM comparisons retaining whitespace-only nodes for the safety boundary;
- eligible and ignored file selection;
- UTF-8 metadata decoding;
- UTF-8 BOM preservation;
- ISO-8859-1 declaration precedence;
- UTF-16LE BOM decoding;
- BOM/declaration conflict rejection; and
- malformed transformation-output rejection.

Native regressions exercise the retained legacy core, actual JDT `CleanUpRefactoring.calculateChange`, the installed cleanup/command registry contributions, and the standalone service. Java calculation must return no XML-related fix/change and leave XML bytes, resource stamps, and Java source unchanged. The standalone control must still format the eligible file and be idempotent.

Workspace integration tests for dirty buffers, external modification conflicts, linked resources, multi-file preview/apply/undo, and validation-error baselines remain tracked by #1240.

## Current limitations

- XML formatting is available through explicit PDE commands and marker resolutions, not Java cleanup profiles or Java save actions.
- The complete document is materialized in memory, bounded at 16 MiB.
- The standalone service applies files individually. Atomic preview/apply/undo needs a separate resource-aware LTK refactoring and remains open.
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
