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

Parameterized constructor migration, external method-source providers, shared assertion-helper signature changes and runners with custom runtime semantics also require their own immutable plan components. They must not be enabled merely by broadening the suite scope.
