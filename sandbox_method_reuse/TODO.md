# Method Reuse roadmap

> **Navigation**: [Main README](../README.md) | [Plugin README](README.md) | [Architecture](ARCHITECTURE.md)

## Implemented

- [x] Replace an inline sequence with a call to an already existing method.
- [x] Map local variables and compatible expressions for the existing-method path.
- [x] Discover repeated contiguous statement sequences above a configurable minimum length.
- [x] Delegate semantic validation, method extraction, parameter/output inference, and duplicate replacement to Eclipse JDT Extract Method.
- [x] Replace all JDT-valid duplicates of the selected sequence.
- [x] Keep the structural extraction mode out of save actions.
- [x] Test the minimum-length boundary and non-repeated negative cases.
- [x] Prove byte-exact Undo for the extracted-method change through the real Cleanup/LTK workflow.
- [x] Add an SWTBot Cleanup preview scenario that shows the extracted method and every replaced occurrence.
- [x] Regenerate the canonical Method Reuse configuration screenshot with the threshold control enabled.

## Near-term hardening

- [ ] Add active tests for return-value extraction, checked exceptions, static contexts, and legal branch handling.
- [ ] Add negative tests for candidates rejected by JDT because of multiple outputs or invalid control flow.
- [ ] Add deterministic performance fixtures for large methods and many coarse candidates.
- [ ] Measure source duplication before and after on a checked-in representative corpus.

## Candidate selection

- [ ] Evaluate whether statement count × duplicate count is the best default value function.
- [ ] Consider an optional estimated removed-line threshold in addition to statement count.
- [ ] Record bounded-analysis diagnostics in the UI when candidate or validation limits are reached.
- [ ] Avoid selecting low-value boilerplate even when it technically meets the minimum length.

## Scope expansion

The current extraction deliberately follows JDT's same-type, same-compilation-unit duplicate-replacement contract.

- [ ] Investigate common-method placement across sibling types only through a dedicated, previewable LTK refactoring.
- [ ] Define visibility, destination type, API compatibility, dependency direction, and naming policy before any cross-type automatic rewrite.
- [ ] Reuse shared planned multi-file infrastructure only when a real cross-file extraction contract exists; do not reintroduce a custom Extract Method implementation.
- [ ] Keep cross-project extraction report-only until editable-scope closure and build dependency rules are proven.

## Existing-method path

- [ ] Strengthen side-effect and overload-resolution tests.
- [ ] Reject overlapping matches deterministically.
- [ ] Improve diagnostics explaining why an apparent match was not replaceable.
- [ ] Share more safety checks with JDT refactoring primitives where possible.

## Documentation and upstream readiness

- [ ] Keep README, installed Help, UI labels, and preview text aligned with executable behavior.
- [ ] Document known JDT Extract Method limitations encountered in real corpora.
- [ ] Prepare an upstream-oriented design only after the behavior and UI are proven on representative Eclipse sources.
