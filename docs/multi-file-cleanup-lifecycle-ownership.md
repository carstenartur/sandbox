# Multi-file cleanup lifecycle ownership

## Contract

`AbstractPlannedMultiFileCleanUp` is stateful for the duration of one explicit cleanup run. It retains one immutable semantic plan per `IJavaProject` between `checkPreConditions(...)`, the per-file `createFix(...)` calls, and `checkPostConditions(...)`.

The supported ownership model is:

- one cleanup instance may serve more than one Java project in the same run;
- lifecycle calls on one instance are serialized;
- after `checkPostConditions(...)` completes, the instance contains no retained plan and may be reused;
- a fatal planning result, cancellation, fix-resolution failure, or postcondition failure removes the affected retained state as documented by the lifecycle tests.

This matches the current Eclipse JDT cleanup orchestrator, which invokes one registered cleanup instance sequentially while it builds a common preview and change tree.

## Defensive concurrency boundary

Alternative callers, including future headless batching code, must not assume that project plans can be created or resolved concurrently on the same instance. The base class protects every entry point that can access retained plan state with a private lifecycle lock:

- `checkPreConditions(...)`;
- `createFix(...)`;
- `checkPostConditions(...)`;
- `expandCleanUpScope(...)`;
- protected plan access used by tests and specialised subclasses.

The lock is deliberately private. The implementation does not synchronize on the publicly reachable cleanup instance or on the cleanup class, so external code cannot accidentally participate in the monitor protocol.

Overridable planning, scope-discovery, fix-resolution, and postcondition hooks execute while the lifecycle lock is held. Implementations must remain synchronous and must not wait for another thread to invoke a lifecycle method on the same cleanup instance.

## Parallel execution

Parallel project processing requires one cleanup instance per independently executing run. Sharing one instance across worker threads is safe only in the limited sense that the base class serializes access; it does not provide parallel planning throughput.

This distinction is intentional:

- mutable run state remains simple and auditable;
- plans for different projects cannot race or leak into each other;
- existing JDT UI behavior is preserved;
- a future parallel orchestrator has an explicit factory/ownership requirement instead of relying on incidental thread safety.

## Verification

`AbstractPlannedMultiFileCleanUpTest` starts planning for two projects from separate executor threads, blocks the first plan inside the overridable hook, and verifies that the second hook cannot enter until the first lifecycle call is released. The test also verifies that both project plans remain independently available afterward.

Related issue: #1220.
