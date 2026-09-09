# Coordinated JUnit migration policy

Sandbox separates local annotation/API rewrites from transformations that need a closed source scope.

## Implemented coordinated components

### ExternalResource rules

Named `ExternalResource` implementations and proven `@Rule`/`@ClassRule` consumers are planned together. The immutable plan owns the generated Jupiter extension, field rewrite and lifecycle changes. An incomplete source scope is rejected rather than partially rewritten.

### JUnit 4 suites

When the RunWith migration is enabled and a selected source unit contains `@Suite.SuiteClasses`, the source test classes referenced by the annotation are added to the same fixed-point cleanup scope.

Supported forms:

```java
@Suite.SuiteClasses({ FirstTest.class, SecondTest.class })
```

```java
@Suite.SuiteClasses(OnlyTest.class)
```

Only source compilation units are added directly. Ordinary `@RunWith` annotations without `SuiteClasses` do not broaden the scope. If a syntactically recognized suite target cannot be resolved, the existing conservative source-root policy is used instead of assuming a partial suite closure.

## Deliberate boundaries

The Java cleanup does not silently remove JUnit 4 dependencies from Maven, OSGi manifests or other build resources. Dependency cleanup is a separate compatibility-managed/manual stage because mixed engines and non-Java resources require explicit project policy.

Parameterized discovery now has its own immutable `junit4-parameterized` contract.
It records runner class, provider/delegate, constructor, injected fields and test
methods, with ordered parameter relations and exact source fingerprints. Missing
source, ambiguous injection, unproven row conversions and custom execution hooks
produce stable rejection reasons. A successful discovery is explicitly reported
as requiring runtime verification; it does not broaden the local rewrite.

Coordinated Parameterized execution, shared assertion-helper signature changes
and runners with custom runtime semantics still need their own verified execution
components. They must not be enabled merely by broadening the suite scope. See the
[executable capability matrix](../sandbox_junit_cleanup/MIGRATION_CAPABILITIES.md)
for the production classes and tests behind each current boundary.
