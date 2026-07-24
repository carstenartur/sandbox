# Coordinated cleanup diagnostics

## Purpose

A coordinated cleanup can add source files beyond the user's explicit selection and can reject a syntactic candidate after binding, API, reference, or resource-policy checks. A preview that only shows the final text edits is therefore insufficient: users and headless callers need to know what the cleanup found, why files were added, why a candidate was refused, and which source units a successful plan will transform.

The common multi-file cleanup layer exposes this information as immutable structured diagnostics. The same model is used by preview status, headless execution, tests, and CI artifacts.

## Schema

`MultiFileCleanUpDiagnostics` contains:

- `cleanupId`: stable cleanup identifier;
- `scope.selectedCompilationUnits`: primary Java-element handles from the original selection;
- `scope.addedCompilationUnits`: handles added by coordinated scope expansion;
- `scope.reasonCode` and `scope.explanation`;
- `scope.complete`: whether the expansion reached a fixed point;
- ordered candidate diagnostic entries.

Each candidate entry contains:

- stable candidate ID derived from a binding key or source field location;
- owning compilation-unit handle;
- lifecycle outcome;
- stable reason code;
- concise human-readable explanation;
- all related source compilation-unit handles known to the planner.

The deterministic JSON representation uses `schemaVersion: 1`. Arrays and candidate entries are sorted, duplicates are removed, and strings are JSON-escaped without relying on UI services. The object contains no AST nodes, working-copy buffers, mutable planner maps, or repository secrets.

## Candidate outcomes

| Outcome | Meaning |
|---|---|
| `FOUND` | A structural candidate was found in the selected or closed source scope. |
| `APPLICABLE` | Binding and reference validation proved that the candidate can be migrated atomically. |
| `REJECTED` | The complete source-compatible migration could not be proved safe. |
| `TRANSFORMED` | The immutable plan contains the candidate's coordinated semantic edits. |

A successful candidate can therefore produce `FOUND`, `APPLICABLE`, and `TRANSFORMED` entries. A refused candidate produces `FOUND` followed by `REJECTED`, or a direct `REJECTED` entry when validation fails before a complete builder can be created.

## Scope diagnostics

`AbstractPlannedMultiFileCleanUp` records the first explicit scope seen by the host, every newly added primary compilation-unit handle, and whether a later expansion pass returned no additional units. The trace is merged into planner diagnostics during preconditions and is consumed exactly once.

When no expansion provider is invoked, the explicit precondition scope is recorded with `NO_EXPANSION`. Alternative hosts can therefore distinguish an intentionally local cleanup from a coordinated closure without inferring behavior from edit count.

The default expansion reason is `RELATED_SOURCE_CLOSURE`. Cleanup implementations may override the code and explanation when a different policy drives expansion.

## Preview summary

When candidates or added source units exist, the cleanup adds one nonfatal `RefactoringStatus` information entry containing:

- total candidate diagnostic entries;
- transformed count;
- rejected count;
- number of automatically added compilation units.

This summary is intentionally short. Detailed candidate IDs, related handles, reason codes, planning metrics, and explanations remain available through structured diagnostics rather than being compressed into one UI message.

## Int-to-Enum reason codes

The planner currently reports:

- `CANDIDATE_FOUND`;
- `NESTED_ENUM_NAME_CONFLICT`;
- `UNSUPPORTED_CONDITIONAL_CHAIN`;
- `OVERLAPPING_CONSTANT_GROUP`;
- `PUBLIC_API_BOUNDARY`;
- `UNSUPPORTED_ARGUMENT_EXPRESSION`;
- `UNSUPPORTED_CONSTANT_USAGE`;
- `NO_REFERENCE_REQUIRES_MIGRATION`;
- `SEMANTICALLY_APPLICABLE`;
- `TRANSFORMED`.

Rejected candidates are not partially transformed. Related handles cover the owner, method declaration and every recognised source reference in the closed migration scope.

## JUnit ExternalResource reason codes

The planner currently reports:

- `EXTERNAL_RESOURCE_TYPE_FOUND`;
- `RULE_FIELD_FOUND`;
- `MULTI_FRAGMENT_RULE_FIELD`;
- `UNRESOLVED_RULE_BINDING`;
- `UNSUPPORTED_RESOURCE_TYPE`;
- `UNSUPPORTED_RULE_FIELD_SHAPE`;
- `RULE_STATICNESS_MISMATCH`;
- `MIXED_RULE_MODES`;
- `SEMANTICALLY_APPLICABLE`;
- `TRANSFORMED`.

Mixed instance/class use remains a fatal atomicity failure. The candidate diagnostic identifies the Rule field and the source resource type involved.

## Lifecycle and failure behavior

Diagnostics and planning metrics are retained only while the serialized cleanup lifecycle is active. They are cleared:

- before a new plan for the same project;
- after postconditions;
- when planning or fix creation throws;
- together with a rejected or invalid retained plan.

Fatal planning status may still carry diagnostics so the refusal remains explainable. A fatal result never carries a plan.

## Headless and CI use

Specialized cleanup subclasses can obtain the current structured model or deterministic JSON through protected lifecycle accessors. A headless runner can persist the JSON beside its refactoring report without re-parsing sources. The schema deliberately uses Java-element handles rather than absolute filesystem paths, making reports stable inside the same workspace model while avoiding accidental disclosure of machine-local paths.

## Verification

The common tests verify:

- deterministic sorting and deduplication;
- exact JSON representation and escaping;
- transformed/rejected/added summary counts;
- scope trace retention and cleanup with the normal lifecycle;
- compatibility constructors for consumers that do not yet emit structured candidate diagnostics.

The cleanup integration tests continue to verify apply, compile, and undo for the successful coordinated cases. Planner-focused tests exercise each rejection code with no generated change.

Related issues: #1212, #1214, #1221, #1224.
