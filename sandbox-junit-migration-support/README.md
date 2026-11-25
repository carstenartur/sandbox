# Staged JUnit migration support

Add `io.github.carstenartur:sandbox-junit-migration-support` at the matching Sandbox version to the **test** classpath. When working from a checkout, install it with `./mvnw -f sandbox-junit-migration-support/pom.xml install` on Java 25. Keep the project's JUnit BOM in dependency management so the launcher, Jupiter and Vintage engines use compatible versions. Include Vintage while running JUnit 4 tests. The jar supplies a discovery filter and lifecycle extensions.

The support jar targets Java 21 for consumer test runtimes. Sandbox builds,
including the build of this jar, require Java 25. The Sandbox product and PDE
test baseline also use Java 25. The Sandbox JUnit cleanup feature installs
`org.sandbox.junit.migration.support` in the host IDE. Select it in the consumer's
PDE target or add the Maven artifact to its test classpath. The feature also installs
a manifest-only fragment that requires the JDT JUnit 5 provider bundles by name and
version range. It selects Jupiter API 5.14 to below 6, and Platform Launcher
and Engine 1.14 to below 2. Both the PDE development launch and the installed product use
these providers in an IDE containing both JUnit generations. The standalone Maven JAR keeps its broad imports and
supports the ordinary JUnit 6 classpath. Native JDT 6 consumers in this
fragment-bound product need a separately verified binding. Configure PDE
service discovery as described below. Mixed-lifecycle fixture adoption is manual.

## Migrating a test subclass

Apply `org.sandbox.junit.jupiter.IgnoreInheritedTests` to a migrated class that should run only the test methods it declares. The marker is inherited by subclasses, matching JDT UI's legacy marker. An unmarked sibling follows the normal discovery rules. A marked descendant can declare an annotated override to run that method. An independently annotated nested class is handled using its own concrete type.

```java
@org.sandbox.junit.jupiter.IgnoreInheritedTests
public class Java21RefactoringTests extends SharedRefactoringTests {
    @org.junit.jupiter.api.Test
    void extractRecordComponent() { /* ... */ }
}
```

The jar registers a JUnit Platform `PostDiscoveryFilter` through `META-INF/services`. It removes inherited method descriptors from Jupiter and Vintage discovery, including inherited Jupiter test templates. This matters when a Jupiter child extends a class that still has JUnit 4 methods and both engines are enabled. A method removed during discovery contributes zero tests to the runner's tree and zero skipped tests. Other engines and descriptors without a test method retain their original behavior. Keep the original JUnit 4 marker and custom runner on the JUnit 4 classes that use them.

This approach follows [JUnit Framework discussion 4570](https://github.com/junit-team/junit-framework/discussions/4570), where Marc Philipp suggested comparing the declaring method to the concrete test class in a Platform post-discovery filter.

### Eclipse plug-in tests

PDE's SPI class loader looks up services in the selected test bundle and engine/runtime bundles. Give the consuming **test plug-in** its own service file named `META-INF/services/org.junit.platform.launcher.PostDiscoveryFilter` with this single line:

```text
org.sandbox.junit.jupiter.InheritedTestsFilter
```

Include `META-INF/` in the test plug-in's `bin.includes`. Make the support bundle available in the target and import both `org.sandbox.junit.jupiter` and `org.junit.platform.launcher` into the test bundle. Use the launcher version belonging to the selected JDT test runner. For JDT's JUnit 5 runtime on the project target, the package range is `[1.14,2)`. The paired native test bundle [shows the complete setup](../sandbox_junit_migration_support_test/).

## Reusing JUnit 4 rule fixtures from Jupiter

A resource fixture may expose setup and teardown through public methods, as in JDT UI's `AbstractRefactoringTestSetup`. Keep the fixture instance so test methods can use its existing helpers. Register its hooks with `perTest` for a former `@Rule`, or with `perClass` on a static field for a former `@ClassRule`.

```java
private final RefactoringTestSetup project = new RefactoringTestSetup();

@org.junit.jupiter.api.extension.RegisterExtension
final org.junit.jupiter.api.extension.Extension extension =
        ExternalResourceExtension.perTest(project::before, project::after);
```

When a constructor assigns the fixture, register a supplier and unbound method
references to its accessible hooks:

```java
class MigratedTest {
    private Fixture fixture;

    @RegisterExtension
    final Extension extension =
            ExternalResourceExtension.perTest(() -> fixture, Fixture::before, Fixture::after);

    MigratedTest() {
        fixture = new Fixture();
    }
}
```

The supplier runs once at the start of each test lifecycle. A null result skips
both hooks. After successful setup, teardown uses the selected receiver even if
the field is reassigned. The supplier form of `perClass` provides the same behavior
at class setup and teardown for a static fixture field.

For protected hooks, such as `ProjectTestSetup.before()`, put the factory in the fixture's package or add a Jupiter companion subclass that can access the hooks:

```java
class JupiterProjectSetup extends ProjectTestSetup {
    Extension perClass() {
        return ExternalResourceExtension.perClass(this::before, this::after);
    }
}

// In the migrated test:
static final JupiterProjectSetup project = new JupiterProjectSetup();
@RegisterExtension static final Extension extension = project.perClass();
```

When migrating a leaf, include any setup that it previously inherited from a JUnit 4 base class. Register shared rule hooks at their original scope and expose inherited setup/teardown through corresponding Jupiter lifecycle methods where needed. Test counts and fixture initialization are separate checks.

If the same fixture class serves both rule scopes, keep its implementation and
the fixture instances used by each consumer. Use separate `perTest` and `perClass`
registrations at the corresponding instance and static fields. Review field
hiding and ordering with other rules, extensions and inherited lifecycle methods,
then compare the same tests before and after. Each supplier reads independently
at its own setup boundary. JUnit 4 can snapshot peer rules together before any
peer setup, so rules that reassign peer fields need explicit review. With
Jupiter's `PER_CLASS` lifecycle, the test instance is constructed before
`perClass` setup.

Unmigrated tests can continue to register `ProjectTestSetup` as a JUnit 4 rule. The Jupiter extension calls teardown after successful setup, including when the test fails. A failed setup leaves teardown uncalled at both scopes. If a test body and its teardown both fail, Vintage reports both causes in a multiple-failure error. Jupiter reports the body failure with the teardown failure suppressed. This adapter covers the `ExternalResource.before/after` lifecycle. A rule that overrides `apply` to wrap the test body needs a migration for that additional behavior.

### Grouping peers that JUnit 4 captured together

Supply the effective outer-to-inner order for **one scope**. The following example
uses two public fixture hooks. The test keeps access to the live fields, and
the group holds the original selected receivers through teardown.

```java
import static org.sandbox.junit.jupiter.ExternalResourceExtension.perTestGroup;
import static org.sandbox.junit.jupiter.ExternalResourceExtension.resourceHook;

import java.util.List;
import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.RegisterExtension;

class SearchTest {
    static class Workspace {
        public void open() { /* prepare the workspace */ }
        public void close() { /* release the workspace */ }
    }

    private Workspace project = new Workspace();
    private Workspace index;

    SearchTest() {
        index = new Workspace();
    }

    @RegisterExtension
    static final Extension resources = perTestGroup(SearchTest.class, context -> {
        SearchTest test = (SearchTest) context.getRequiredTestInstance();
        return List.of(
                resourceHook(() -> test.project, Workspace::open, Workspace::close),
                resourceHook(() -> test.index, Workspace::open, Workspace::close));
    });
}
```

For static class fixtures, use a separate static `perClassGroup(SearchTest.class,
context -> ...)` registration with selectors that read static fields. Entry and
exit run only where the required test class exactly matches the supplied class.
The factory runs once per matching entry. Defer field reads to the selectors.
The extension validates a snapshot of the entire list and selects **every**
receiver before setup starts, so an outer setup can replace a peer's field without
redirecting its hooks. A null list or descriptor fails before selection. Empty
groups are valid. Null receivers skip both hooks. Aliases and repeated descriptors
retain their own positions. A factory or selector failure acquires nothing.

Only successful setups acquire cleanup. The group removes its pending actions
before draining them in reverse order. One ordinary cleanup failure escapes
intact. Several produce a fresh OpenTest4J `MultipleFailuresError` with every
original failure in cleanup order, including duplicates. An ordinary setup
failure keeps its identity. Jupiter controls how it combines cleanup with a
setup, body or user-lifecycle failure. A suppression-disabled primary can hide
cleanup failures. An assumption can abort a test after cleanup, while a failing
cleanup can turn the abort into a failure.

If group setup throws `OutOfMemoryError`, the group immediately drains the
successful outer setups and rethrows the original error. An `OutOfMemoryError`
during cleanup stops the drain. Already collected failures, or a distinct
cleanup failure during fatal setup, are attached where suppression permits.
A fatal body or user hook can escape Jupiter before the group gets its exit
callback. Under `PER_CLASS`, Jupiter constructs the instance before class setup.

The caller must establish the effective order, resolve field hiding and provide
legally accessible hooks. The group supplies no automatic mixed-rule conversion
or translation of custom `apply` wrappers. Provision the support artifact,
OpenTest4J 1.3 and the matching Jupiter/Platform dependencies in the test target.
The four outstanding reference-matrix failures remain separate migration work.
