# Staged JDT Core JUnit 3 harness migration

The Eclipse JDT Core test harness is not a normal `junit.framework.TestCase`
hierarchy. Its custom `org.eclipse.jdt.core.tests.junit.extension.TestCase`
combines named construction, configurable method filtering and ordering,
performance callbacks, indexer state, memory diagnostics and rerun hooks.
`SuiteOfTestCases` additionally provides suite-scoped setup and teardown and
copies instance state between separately constructed JUnit 3 test objects.

A safe migration therefore proceeds in independently verified slices.

## Slice 1: detachable single-test leaves

The first supported custom-harness shape is deliberately narrow:

- the concrete class directly extends the JDT Core custom `TestCase`;
- it has no source subtype and no reference outside its own compilation unit;
- it declares exactly one public, non-static, parameterless `void test*()` method;
- its only constructor is `public Type(String name) { super(name); }`;
- its only `suite()` method is `return buildTestSuite(Type.class);`;
- it has no lifecycle override, name access, performance call, inherited field
  access or other custom-harness API use;
- assertion calls must resolve to the ordinary JUnit 3 assertion API, not to a
  JDT-specific assertion overload.

For this shape the constructor and suite method carry discovery mechanics only.
The cleanup removes them and the custom superclass, materializes the Jupiter
`@Test` method and rewrites the ordinary assertion call. An active regression
runs the same type through JDT's JUnit 5/Vintage launch before and after and
requires an identical, non-empty, successful runtime tree.

Classes with more than one test remain rejected because the JDT harness can
change their order through the `ordering` system property. Preserving only the
default order would silently remove a supported debugging mode.

## Remaining slices

The following concepts still require explicit models and remain unchanged:

1. configurable multi-test filtering and ordering (`TESTS_PREFIX`,
   `TESTS_NAMES`, `TESTS_NUMBERS`, `TESTS_RANGE`, `RUN_ONLY_ID`, `ORDERING`);
2. inherited-depth suite construction;
3. `SuiteOfTestCases` suite-scoped setup, teardown and instance-field transfer;
4. compliance-level multiplication and custom suite aggregators;
5. performance meters, memory logging, indexer control and rerun callbacks.

Each later slice must use the existing JDT launch as its before/after oracle and
must fail closed when the complete source and runtime contract is unavailable.
