# JDT Core JUnit harness migration

## Goal

Migrate Eclipse JDT Core test families from their custom JUnit 3 harness to Jupiter without flattening or silently losing the harness semantics that determine test identity, ordering, filtering, shared suite state, compiler compliance, indexer control, memory logging and performance measurements.

This migration builds on the ordinary closed-hierarchy path from #1336, but it is a distinct framework migration. The JDT Core harness must be made explicit before its inheritance can be removed.

## Source contract being migrated

The implementation is based on the current upstream sources:

- `org.eclipse.jdt.core.tests.junit.extension.TestCase` extends `org.eclipse.test.performance.PerformanceTestCase`, requires a `String` test-name constructor, filters `test*` methods through `TESTS_PREFIX`, `TESTS_NAMES`, `TESTS_NUMBERS`, `TESTS_RANGE` and `RUN_ONLY_ID`, supports configurable ordering, controls the JDT indexer, clears interrupt state and exposes performance/memory hooks.
- `SuiteOfTestCases` uses a custom `TestSuite` to call `setUpSuite()` once, copy mutable instance fields from one freshly constructed test object to the next, and call `tearDownSuite()` once.
- `AbstractJavaModelTests.buildModelTestSuite(...)` uses that suite-state contract with zero inherited test depth.
- `AbstractCompilerTest` multiplies tests across compiler compliance levels, reads an optional `INHERITED_DEPTH` field and wraps classes in compliance-specific setup suites.
- Eclipse Platform already provides `PerformanceTestCaseJunit5`, backed by the same `AbstractPerformanceTestCase` measurement API. The migration must use that existing class rather than create another performance framework.

Upstream references:

- https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/org.eclipse.jdt.core.tests.compiler/src/org/eclipse/jdt/core/tests/junit/extension/TestCase.java
- https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/org.eclipse.jdt.core.tests.model/src/org/eclipse/jdt/core/tests/model/SuiteOfTestCases.java
- https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/org.eclipse.jdt.core.tests.model/src/org/eclipse/jdt/core/tests/model/AbstractJavaModelTests.java
- https://github.com/eclipse-jdt/eclipse.jdt.core/blob/master/org.eclipse.jdt.core.tests.compiler/src/org/eclipse/jdt/core/tests/util/AbstractCompilerTest.java
- https://github.com/eclipse-platform/eclipse.platform.releng.aggregator/blob/master/eclipse.platform.releng/bundles/org.eclipse.test.performance/src/org/eclipse/test/performance/PerformanceTestCaseJunit5.java

## Staged target architecture

### Slice A: direct custom-TestCase families

A source-compatible nested Jupiter bridge is inserted into the existing JDT Core `TestCase` source. The original JUnit 3 class remains unchanged for families not migrated yet.

Eligible direct families are changed from:

```java
class ExampleTests extends TestCase {
    ExampleTests(String name) {
        super(name);
    }
}
```

to the nested Jupiter bridge:

```java
class ExampleTests extends TestCase.Jupiter {
}
```

The bridge uses the existing `PerformanceTestCaseJunit5`, preserves the current test name through `TestInfo`, applies the JDT filter and ordering contract, controls the indexer, clears interrupt state and retains the supported memory/performance hooks. Test methods and assertions are materialized through the existing plan-aware hint backend. Only simple name constructors and exact per-class suite factories are removed.

The slice rejects lifecycle overrides, suite-state classes, compliance multiplication, decorators, custom `runTest` logic, unsupported TestCase helper calls, out-of-scope suite owners and any stale or unresolved binding.

### Slice B: SuiteOfTestCases state transfer

A Jupiter extension nested in the existing `SuiteOfTestCases` source must reproduce the current semantics rather than approximate them with `@TestInstance(PER_CLASS)`:

- a fresh instance per test;
- `setUpSuite()` before the first test;
- mutable non-static, non-final fields copied from the previously executed instance;
- `tearDownSuite()` after the final test;
- same-thread execution and deterministic order;
- standalone rerun support.

### Slice C: compiler compliance templates

Compliance multiplication becomes an explicit Jupiter test-template or suite descriptor. It must retain:

- the exact set and order of supported compliance levels;
- the `INHERITED_DEPTH` contract;
- setup-suite construction and cleanup;
- preview-test exclusion on future JREs;
- test class and method multiplicity in the JDT runtime tree.

### Slice D: aggregate suites and remaining callbacks

Global `Run*`/`TestAll` owners, decorators, filters, performance callbacks and rerun setup hooks are migrated only after their complete JDT launch tree is captured. Mixed Jupiter/Vintage aggregate suites may be used during the transition.

## Safety rules

- The semantic planner identifies the exact upstream harness shape by bindings and source structure; matching only class names is insufficient.
- The old JUnit 3 harness remains available until every dependent family in the selected slice has migrated.
- No migration is applied when a constructor, suite owner, helper call, filter assignment, binary subtype or setup wrapper is outside the closed source scope.
- Every supported slice runs the same JDT launch before and after and compares a non-empty successful tree including nesting, display name, class/method identity, order and multiplicity.
- Test execution is part of acceptance; a matching discovery tree alone is insufficient.
- Compliance and suite-state semantics are never replaced by `@Test` annotations alone.
