# Grouped fixture runtime design

## Goal and base

Preserve a caller-specified group of legally accessible fixture hooks during an explicit staged migration from JUnit 4 to Jupiter. The group must capture every receiver before any setup hook can replace a peer field, then unwind only successful setups in reverse order. This is a prerequisite for future automatic mixed-rule conversion, not that conversion itself.

Start from main `8c5ec81095c6a268b0498a8516be5b4bc7e9f6a5`, which contains #1981. This document reconstructs the previously reviewed grouped-runtime decisions after the local executor was replaced; it does not claim that lost draft bytes or test receipts were recovered.

## Public API

Add these members to `org.sandbox.junit.jupiter.ExternalResourceExtension`:

```java
public static final class ResourceHook { /* opaque; no public constructor */ }
public static <T> ResourceHook resourceHook(Supplier<? extends T> fixture,
        ThrowingConsumer<? super T> before, Consumer<? super T> after);
public static Extension perTestGroup(Class<?> exactTestClass,
        Function<? super ExtensionContext, ? extends List<ResourceHook>> factory);
public static Extension perClassGroup(Class<?> exactTestClass,
        Function<? super ExtensionContext, ? extends List<ResourceHook>> factory);
```

Use the existing Java/Jupiter functional interfaces. Preserve all four existing registration APIs and their behavior. A hook describes selection/setup/teardown only; it contains no registration-global selected receiver or invocation state. Validate hook arguments, exact class and factory at registration. Empty groups are allowed.

## Entry, snapshot and ordering

1. Compare the actual required test class by identity with `exactTestClass` before invoking the factory or touching any test instance or selector. A mismatch performs no work. Apply the same guard on exit; inherited registrations must not affect a different concrete class.
2. Invoke the factory once per matching lifecycle entry. It may obtain the appropriate test instance and construct hook metadata, but callers must defer fixture field reads to each hook's selector. The runtime cannot make an eager or side-effecting factory safe.
3. Take and fully validate a `List.copyOf` snapshot before calling any selector. A null list or null element fails before selection/setup; later mutation of the caller's list has no effect.
4. Invoke every selector in list order, exactly once, before the first setup. Capture references, including nulls, without deduplicating aliases or repeated descriptors. A factory/selector failure runs no setup or teardown.
5. The supplied order is already the effective outer-to-inner order. Skip both hooks for a null receiver. Invoke setup in order and record a receiver-bound cleanup action only after that setup succeeds. A partially failed setup does not acquire its own cleanup.
6. Keep successful cleanup records in the existing registration-identity and invocation-identity scoped ExtensionContext Store discipline. Store actions/collections rather than AutoCloseable receivers. Remove the complete pending state before cleanup to prevent reentry or duplicate exit from closing it twice. Finish successful entries in reverse order.

Neither this API nor the runtime discovers JUnit 4 field hiding, rule sorting, reflective access or custom `apply` wrappers. Future source conversion must supply a proven order and legally callable hooks. Equal field names and aliases must remain distinct when the supplied effective list says so.

## Ordinary failures and engine boundaries

For an ordinary setup failure, rethrow the original Throwable object and let Jupiter invoke the normal matching after callback to unwind previously successful setups. No wrapper may replace a Throwable merely because it is not an Exception or Error.

Cleanup drains every successful action for ordinary failures. With zero failures it returns normally; with one it throws that original object; with two or more it throws a fresh `org.opentest4j.MultipleFailuresError` containing the original objects in actual cleanup order, including repeated identical objects. Do not mutate ordinary cleanup-failure objects or reuse a previous aggregate.

Jupiter owns the relationship between a pre-existing setup/body/user-lifecycle primary failure and this cleanup result. Its suppression behavior, abort classification, container/test counts and engine-version boundaries must be measured with real execution. A suppression-disabled primary can prevent ordinary cleanup failures from being attached. Preserve setup identity and document that reporting limit; do not replace the primary to invent a universal lossless-reporting guarantee. Body and user-lifecycle failures remain engine-owned.

## Fatal failures

Use synthetic `OutOfMemoryError` objects in tests without consuming memory. Do not import internal Platform exception utilities or infer that every Error is fatal.

- A factory/selector OutOfMemoryError occurs before any setup, so rethrow that same object with no cleanup.
- A group-owned setup OutOfMemoryError can bypass Jupiter's normal after callback. Remove and immediately drain the successful outer setups, then rethrow the identical setup error. Attach a distinct ordinary cleanup result or cleanup OutOfMemoryError as context when suppression allows it; never attempt self-suppression.
- During cleanup, OutOfMemoryError stops the remaining drain immediately and leaves no pending state. It is not wrapped in MultipleFailuresError. Attach any already collected ordinary cleanup result as context when possible. If an outer setup OutOfMemoryError exists, that original setup error remains the outgoing object and the cleanup error is its context.
- A body or user-lifecycle OutOfMemoryError may bypass matching after callbacks before the extension regains control. Retain a real native counterexample and document this boundary. This slice offers no universal cleanup guarantee for engine-owned fatal exits.

## Runtime and packaged artifact

The build/product/PDE baseline remains Java 25, Tycho 5.0.4 and Eclipse 4.41. Only the independent support artifact retains compiler release 21, including its tests. Add a direct `org.opentest4j:opentest4j:1.3.0` dependency: the supported JUnit BOMs do not manage it. Add OSGi import range `[1.3,2)` while preserving the existing Jupiter `[5.14,7)` and Platform `[1.14,7)` ranges and SPI registration.

Build one canonical support JAR. Run packaged verification against those same unmodified JAR bytes on Java 21 and Java 25, each with JUnit BOM 5.14.4 and 6.1.3. The normal Maven unit-test execution stays intact. A second Surefire execution named `packaged-runtime`, bound to integration-test, uses an unused classesDirectory and the canonical JAR as an explicit additional classpath entry. It includes the support tests and a `SupportArtifactIT`; its artifact probe must prove the production class CodeSource and loaded class bytes come from that JAR, all packaged production classes have major 65, the OSGi EE is JavaSE 21, imports and SPI are present, and record actual JVM, engine/library versions and SHA-256. Never allow loose target/classes to mask a broken package.

Build once with Java 25. For subsequent matrix cells invoke `surefire:test@packaged-runtime` directly with the supported JVM/BOM properties; do not rebuild the package between cells. The real existing JDT JUnit 5 loader/filter cases must pass in each cell. A broad OSGi import range alone is not proof of a provisioned product or target; those are later slices.

## Behavioral evidence

Use actual Vintage and Jupiter engines, event/receiver identity and exact execution counts. Share bounded fixtures when that improves clarity; no mirrored implementation tests or replacement engine mocks.

- Compare outer-to-inner setup/reverse teardown, class/test scopes and user Before/After phases with real Vintage rules.
- Prove collective snapshot against a setup that replaces a peer, constructor/static assignment, body-time replacement, aliases, duplicate descriptors, null receivers, empty lists and caller-list mutation.
- Prove exact-class guarding including inherited/nested registrations before factory access; distinct inherited fields and same-name hiding are represented by explicit effective lists. Include a legally accessible TestName-style hook whose position matters to user setup.
- Cover ordinary setup failure at each relevant depth, one/multiple/repeated cleanup failures, body and user-lifecycle failures, assumption aborts, original Throwable identity, fresh aggregates and suppression-disabled primaries.
- Prove the fatal rules above, including group-owned setup/cleanup versus engine-owned body boundaries, through native execution; callback-specific checks may complement but not replace the engine oracle.
- Repeat launches in one JVM; prove no leftover registration state, reuse of aggregates or extension mutation of prior ordinary cleanup-failure objects. Jupiter may mutate its primary via suppression, as described above. Exercise duplicate/reentrant exit and genuinely concurrent invocations with bounded synchronization. AutoCloseable receivers must not be auto-closed by the Store.
- Retain a concrete Jupiter PER_CLASS construction-order counterexample. This API does not force PER_METHOD or promise JUnit 4 construction equivalence under PER_CLASS.

## Scope and delivery

Update the support README and adjacent public capability/help documentation with a usable grouped example, exact ordering/snapshot/failure contract and boundaries. Keep automatic mixed-rule migration, field-order/hiding analysis, dependency provisioning, custom-apply translation and four remaining reference-matrix cases explicitly open. Do not claim #1931 or the reference matrix becomes green from this API alone.

Aim for 1,350–1,450 changed text lines, including this spec, the plan, implementation, tests and docs. Preserve meaningful coverage and readable code; do not compress statements or weaken assertions to fit. Reassess the slice before 1,500 lines and stop before 2,000 unless a substantive repository-policy exception is already recorded. No Python/workflow test authority, no gate weakening, no screenshot changes and no extra upstream checkout. Root owns remote publication and ordinary qualified merge.
