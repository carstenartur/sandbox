# ADR 0001: Integer-domain migration compatibility and cleanup impact

- **Status:** Accepted
- **Date:** 2026-07-27
- **Issues:** #1216, #1223, #1226

## Context

A local syntax cleanup and a coordinated signature/type migration do not make the same safety claim. The integer-to-enum cleanup currently supports only package-private source domains whose declarations, comparisons and callers can be proven closed in the selected Java source scope. Future requests include public APIs, persisted values, command-line values and wire protocols; those representations cannot be treated as ordinary source-only cleanups.

## Decision

### Impact levels

Every cleanup is classified as one of:

- `LOCAL_SAFE`: one compilation unit and no externally visible contract change;
- `PROJECT_CLOSED`: multiple Java source units changed atomically after proving reference closure;
- `COMPATIBILITY_MANAGED`: an external contract changes under an explicit adapter/versioning policy;
- `MANUAL_REFACTORING`: interactive decisions or non-Java resources are required.

Only `LOCAL_SAFE` is eligible for an ordinary save action. All other levels require an explicit preview. Structured multi-file diagnostics report the impact, affected-unit count, save-action eligibility and compatibility statement in both preview status and deterministic JSON.

### Integer-to-enum modes

#### `CLOSED_FLOW_AUTOMATIC`

This is the only implemented automatic mode. It requires:

- package-private constants and candidate method;
- distinct constant values;
- a complete source declaration/caller scope;
- direct calls passing only modelled constants;
- no unsupported constant/state/method references;
- no generated-name collision;
- callers in the same package as the package-private nested enum.

The migration is `PROJECT_CLOSED`: it preserves the behaviour of the proven source flow but makes no source- or binary-compatibility promise to external clients.

#### `PUBLIC_VALUE_ADAPTER_OPT_IN`

An explicit cleanup profile may add a public nested enum and a distinctly named public method to a final class. Each enum constant stores the existing public compile-time int or String constant. Its `legacyValue()` returns that exact value. For example, `processValue(Status)` calls the unchanged `process(int)` or `process(String)`. Existing binaries keep the original method descriptor, switch labels remain compile-time constants, and calls with null or unknown values follow the original method body.

This single-unit `COMPATIBILITY_MANAGED` edit is off by default and absent from save-action profiles. Its first implementation accepts a one-parameter, void, non-static public method with a complete directly compared constant group. Final owners avoid introducing an override in an unknown subclass. Serializable owners, locally ambiguous names and aliases are excluded. External consumers combining wildcard static imports must check for conflicts with the proposed nested type name. The edit introduces no deprecation annotations. The wider numeric adapter and external representation policy below remains a separate mode.

#### `NUMERIC_ADAPTER_OPT_IN`

This future `COMPATIBILITY_MANAGED` mode is not executable yet. Before it may be enabled it must provide:

- an explicit stable numeric field on every enum constant;
- `fromValue(int)` or an equivalent adapter with a documented unknown-value policy;
- temporary overloads/adapters where source or binary callers require them;
- deprecation and removal policy for public integer constants;
- tests covering persistence, serialization, database values, preferences, CLI flags and network payloads.

#### `MANUAL_DOMAIN_REFACTORING`

Aliases, sparse/ranged domains and bit masks are not ordinary enum domains. Bit flags normally require `EnumSet`; aliases and ranges require an explicit domain design. These cases remain manual until a dedicated policy is implemented.

### External identity

`Enum.ordinal()` is never a persisted, serialized or wire identity. Reordering enum constants must not alter external meaning.

### Reference generation

A generated package-private nested enum may be referenced only by callers in the same package. References are generated through AST/`ImportRewrite` name selection so same-package callers receive idiomatic `Owner.Enum.CONSTANT` names while conflicts may retain an unambiguous qualified owner name. Cross-package callers are rejected during planning instead of producing inaccessible source.

## Consequences

- Existing closed-flow detection remains conservative and automatic.
- Public or externally represented integer domains cannot silently enter the automatic cleanup.
- IDE, headless and CI consumers share one impact vocabulary.
- Compatibility-managed migration remains a separately reviewable feature rather than an accidental extension of the current detector.
