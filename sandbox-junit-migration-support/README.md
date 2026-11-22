# Staged JUnit migration support

Add `io.github.carstenartur:sandbox-junit-migration-support` at the matching Sandbox version to the **test** classpath. When working from a checkout, install it with `./mvnw -f sandbox-junit-migration-support/pom.xml install` on Java 25. Keep the project's JUnit BOM in dependency management so the launcher, Jupiter and Vintage engines use compatible versions. Include Vintage while running JUnit 4 tests. The jar supplies a discovery filter and lifecycle extensions.

The support jar currently targets Java 25, so the test runtime that loads it must
support Java 25 as well. Building it on Java 25 does not make it loadable by a
Java 21 test runtime. The cleanup does not automatically add this jar, its PDE
service registration, or mixed-lifecycle fixture adapters to a consuming project.

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
registrations at the corresponding instance and static fields; one registration
does not run at both scopes. Review ordering with other rules, extensions and
inherited lifecycle methods, then compare the same tests before and after.

Unmigrated tests can continue to register `ProjectTestSetup` as a JUnit 4 rule. The Jupiter extension calls teardown after successful setup, including when the test fails. A failed setup leaves teardown uncalled at both scopes. If a test body and its teardown both fail, Vintage reports both causes in a multiple-failure error. Jupiter reports the body failure with the teardown failure suppressed. This adapter covers the `ExternalResource.before/after` lifecycle. A rule that overrides `apply` to wrap the test body needs a migration for that additional behavior.
