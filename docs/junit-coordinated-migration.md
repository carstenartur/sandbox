# Coordinated JUnit migration policy

Sandbox separates local annotation/API rewrites from transformations that need a closed source scope.

## Implemented coordinated components

### ExternalResource rules

Named `ExternalResource` implementations and proven `@Rule`/`@ClassRule` consumers are planned together. The immutable plan owns the generated Jupiter extension, field rewrite and lifecycle changes. An incomplete source scope is rejected rather than partially rewritten.

For incremental manual adoption, the separate
[staged support jar](../sandbox-junit-migration-support/README.md) retains fixture
instances through `perTest` and `perClass` hook registrations. Use an instance
extension field for a former `@Rule` and a static field for a former `@ClassRule`.
The two scopes require separate registrations; inherited setup and ordering
still need review. This Java 25 support jar and its discovery filter must be
wired explicitly into the consumer, including PDE service registration where
needed. It does not enable automatic mixed-lifecycle conversion or preserve an
arbitrary override of `ExternalResource.apply`.

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

Parameterized migration has its own immutable `junit4-parameterized` contract.
It records runner class, provider/delegate, constructor, injected fields and test
methods, with ordered parameter relations and exact source fingerprints. Missing
source, ambiguous injection, unproven row conversions and custom execution hooks
produce stable rejection reasons. With Parameterized migration enabled and
Jupiter's `ParameterizedClass` available in the selected project, the closed
constant-row strategy can produce executable coordinated edits and reports
`PARAMETERIZED_CLASS_READY`. The executor revalidates source fingerprints and
binding targets. Its native runtime regression tests compare test identities,
multiplicity, display names and results.

A `FOUND` plan with `PARAMETERIZED_RUNTIME_VERIFICATION_REQUIRED` remains discovery
evidence only and does not authorize Apply. Shared assertion-helper signature
changes and runners with unsupported runtime semantics still need their own
verified execution components. They must not be enabled merely by broadening the
suite scope. Run the actual migrated project tests as well. See the
[executable capability matrix](../sandbox_junit_cleanup/MIGRATION_CAPABILITIES.md)
for the production classes and tests behind each current boundary.

## Review a large migration in batches

The headless cleanup application can split independent dependency components into
separately reviewable batches. Add `--batch-max-files 20` to a project check, then
inspect the reported batch, required descriptor changes and review receipt.
Apply one reviewed batch with the same project, configuration and file limit;
run the project's build and tests before planning the next batch. A dependency
component can exceed the requested limit when splitting it would break its
contract. The Workbench cleanup selection is unchanged.

See the [batch commands and review steps](../sandbox_cleanup_application/README.md#reviewing-a-junit-migration-in-batches)
for receipt validation, intermediate-state checks and native Undo.
