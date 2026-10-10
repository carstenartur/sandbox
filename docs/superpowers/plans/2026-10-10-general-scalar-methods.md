# Source-level scalar helper expansion

Goal: feed original Java computations spanning statically bound helper calls into the existing Regelsuche optimizer. No mathematical identity, target expression, class name or example recognizer belongs to this adapter.

Architecture: resolve private static methods in the same type by JDT binding; translate their integral straight-line bodies using the existing typed expression decoder and original operation trace. Parameter/local scopes are distinct per call, arguments are evaluated left-to-right before body evaluation, and return conversions are explicit. Search and independent mathematical checking are unchanged.

Constraints: no reflective execution of consumer code; no unchecked math assumptions; no loss of unused throwing computations; no new generated runtime dependency; no altered helper declarations. Unsupported dispatch, state, control flow and recursive cycles are diagnosed. Hard expansion limits bound the source adapter separately from search.

Tasks:
- [x] Observe failing end-to-end and source-decoding tests for general helper sequences.
- [x] Implement only Java binding/body translation; retain the existing optimizer and independent verifier.
- [x] Exercise unrelated formula shapes, nested/repeated/overloaded calls, promotions, exceptions, rejected effects and source boundaries.
- [x] Run the full mathematics module (192 tests, 0 failures/errors/skips; both SpotBugs checks clean).
- [ ] Publish regular sources with exact test evidence (tracked in the PR).

Ruling: this slice expands integral straight-line private static helpers, not arbitrary loop invariants. It must not reintroduce the removed halving/constructor recognizer. The current SDK's mathematics is reused unchanged. More general control-flow and object semantics remain separately diagnosed.
