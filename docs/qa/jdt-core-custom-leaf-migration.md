# JDT Core custom leaf migration QA

The first JDT Core harness slice is accepted only when all of the following hold:

- the source class directly extends `org.eclipse.jdt.core.tests.junit.extension.TestCase`;
- the class has exactly one executable JUnit 3 test method;
- the named constructor is exact boilerplate;
- the suite is either the exact inherited `buildTestSuite(This.class)` form or
  the real package-named outer `TestSuite` plus inner class `TestSuite` form;
- the class has no source subtype and no external reference;
- no method or field declared by the custom harness is used;
- assertions resolve to the ordinary JUnit 3 assertion API;
- the same JDT JUnit 5/Vintage launch succeeds before and after migration;
- the complete non-empty runtime trees are identical;
- a two-test fixture remains unchanged because configurable harness ordering would otherwise be lost.

The QA intentionally does not claim support for `SuiteOfTestCases`, compliance
multiplication, inherited-depth discovery, filters, performance callbacks,
indexer control or state transfer between JUnit 3 instances.
