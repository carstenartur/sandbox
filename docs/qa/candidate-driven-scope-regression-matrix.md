# Candidate-driven cleanup scope regression matrix

This matrix records the safety contract exercised by the common search tests, provider-scope tests, and coordinated cleanup lifecycle tests.

| Scenario | Int-to-Enum | JUnit ExternalResource | Expected result |
|---|---|---|---|
| No structural candidate in the explicit selection | Covered by candidate detector and selected-only cleanup tests | Covered by candidate detector and selected-only cleanup tests | No scope expansion |
| Owner selected, one required source user | `IntToEnumScopeExpansionTest` and `IntToEnumCandidateGateFixedPointTest` | `JUnitScopeExpansionTest` and `JUnitCandidateGateFixedPointTest` | Provider returns the exact owner/user closure once |
| Unrelated editable source in the same project | Scope tests include an unrelated unit | Scope tests include an unrelated unit | Unit is not admitted to the exact closure |
| Candidate declaration and all references in permitted source roots | `RelatedCompilationUnitSearchTest` accurate-match case | Same common search contract | Closed deterministic source subset |
| Accurate reference in another Java project | Common search rejection test | Same common search contract | Coordinated migration refused |
| Binary or otherwise non-source reference | Common search rejection test | Same common search contract | Coordinated migration refused |
| Reference in generated, derived, output, metadata-less, or excluded root | Common search allow-list rejection test plus source-root policy tests | Same common search contract | Coordinated migration refused |
| Inaccurate JDT search match | Common search rejection test | Same common search contract | Coordinated migration refused |
| Candidate syntax found but seed bindings unavailable | Candidate seed completeness fallback | Candidate seed completeness fallback | Conservative complete policy scope requested; planner still validates bindings |
| Supporting host invokes scope expansion | Provider fixed-point tests | Provider fixed-point tests | Returned units are added until no further expansion is requested |
| Ordinary Eclipse host does not invoke scope expansion | Complete-scope lifecycle test | Complete-scope lifecycle test | Caller supplies the complete coordinated scope explicitly |
| Apply and undo | `MultiFileIntToEnumCleanUpTest` | `MultiFileExternalResourceLifecycleTest` | Sources and Java error baseline restored; unrelated selected source remains unchanged |
| Cancellation during search or planning | Shared progress monitor and cancellation checks | Shared progress monitor and cancellation checks | `OperationCanceledException`; no retained plan |

## Determinism

Compilation units are deduplicated by primary Java-element handle and sorted by handle before planning. Search targets are derived from resolved Java elements rather than names. A closed scope is consumed once by the serialized cleanup lifecycle and cleared after planning or failure.

## Host boundary

`IMultiFileCleanUpScopeProvider` does not modify the ordinary Eclipse cleanup host. A supporting host must call the provider explicitly and iterate to its fixed point. The unpatched `CleanUpRefactoring` path remains valid when the complete coordinated compilation-unit scope is supplied up front.

## Fail-closed boundary

A search result that cannot be represented as an accurate, editable source compilation unit in the owning project is not ignored. It rejects the coordinated migration. Missing seed bindings are the only fallback to the broader policy scope because the search cannot be constructed safely; that fallback never admits generated, derived, output, binary, or metadata-less roots.

Related issues: #1212, #1214, #1221, #1224.
