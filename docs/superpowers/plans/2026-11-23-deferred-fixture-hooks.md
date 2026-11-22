# Deferred fixture hooks implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Safely reuse constructor-assigned or mutable JUnit 4 fixtures through explicit Jupiter registrations on supported consumer runtimes.

**Architecture:** Extend the existing scoped hook store with a deferred action that captures one receiver per invocation; retain all existing public APIs. Set Java 21 compilation only in the independent support module and verify on real consumer JVMs.

**Spec:** `docs/superpowers/specs/2026-11-23-deferred-fixture-hooks-design.md`

**Global constraints:** Java 25 Maven/build runtime and repository baseline; no global downgrade, source generator, dependency provisioning, suppression, ignored failure, Python test authority or screenshot changes. Keep production scope to the support artifact and accurate adjacent documentation. Reuse shared dependency caches, never make duplicate upstream fixture checkouts. Root handles remote publication and qualified merge; implementation is local to this worktree.

### Task 1: Add deferred, receiver-stable hooks and Java 21 consumer support

**Files:**
- Modify `sandbox-junit-migration-support/src/main/java/org/sandbox/junit/jupiter/ExternalResourceExtension.java`.
- Extend `sandbox-junit-migration-support/src/test/java/org/sandbox/junit/jupiter/ExternalResourceExtensionTest.java`; use a focused companion test class in the same package if the new concurrency/receiver cases would obscure existing fixtures.
- Modify `sandbox-junit-migration-support/pom.xml` for a module-local compiler release only; inspect generated `maven-bundle.bnd` metadata and change the bnd instructions only if the generated EE would be wrong.
- Update `sandbox-junit-migration-support/README.md`, `docs/junit-coordinated-migration.md`, `sandbox_junit_cleanup_help/html/usage.html` and the consumer-artifact exception in `docs/JAVA_BUILD_RUNTIME.md`.

**Interfaces and fixed requirements:**
- Preserve `perTest(Executable before, Runnable after)` and `perClass(Executable before, Runnable after)`.
- Add `public static <T> Extension perTest(Supplier<? extends T> fixture, ThrowingConsumer<? super T> before, Consumer<? super T> after)` and the equivalent `perClass` overload. Use `java.util.function.Supplier`, `java.util.function.Consumer` and `org.junit.jupiter.api.function.ThrowingConsumer`.
- Supplier/callback arguments are non-null; a supplied null fixture is allowed. Read the fixture once at lifecycle entry, bind teardown to that receiver, store only after successful setup, remove before teardown. Keep state per registration and invocation; preserve arbitrary Throwable identity through existing callback handling.
- The parent still requires Maven to run on Java 25; only this support module compiles with release 21. Verify all packaged production class majors are 65 and generated OSGi EE is 21. Existing PDE module stays Java 25.

- [x] **Step 1: Add behavioral regressions and run RED.**
  Add a real Vintage/Jupiter pair where the rule is assigned in a constructor, its field changes during the body, and teardown must close the original object. Include a static field whose registration precedes assignment and changes in the body. Demonstrate why existing eager binding or two independent field reads fails; missing API compilation is acceptable as the initial overload RED but retain concrete event/receiver assertions for GREEN.

- [x] **Step 2: Implement the minimal overloads.**
  Reuse the existing lifecycle wrappers and store-scoping discipline. Store a cleanup action rather than the fixture itself to avoid Platform AutoCloseable disposal. Do not add registration-global receiver state, reflection, source rewriting, or hook-access widening. Keep old overload behavior compatible.

- [x] **Step 3: Prove lifecycle boundaries.**
  Test null values, supplier exceptions, setup exceptions (including the original Throwable identity), successful setup followed by failed body/teardown, and both scopes. Cover repeat invocation and genuine overlap using real Jupiter execution with bounded synchronization. Assert supplier calls, matching setup/teardown receivers, event order/test counts and no unintended skips. Keep existing two-argument tests passing.

- [x] **Step 4: Qualify real consumer runtimes.**
  Set release 21 in this module's compiler plugin configuration without changing `java-version` or skipping the enforcer. Build using Java 25. Run the support-module suite using a Java 21 Surefire JVM and the default Java 25 JVM. Qualify both JUnit BOM 6.1.3 and 5.14.4 using existing Maven dependency management, and inspect the actual packaged class-file/OSGi metadata. If an existing test-only JDT loader blocks an older runtime, identify its exact class version and report before making any skip or fixture substitution. Do not weaken an existing gate.

- [x] **Step 5: Update precise documentation.**
  Show `perTest(() -> fixture, Fixture::before, Fixture::after)` for a constructor-assigned, legally accessible fixture. Explain one selected receiver, null behavior and teardown after successful setup. State the support jar's Java 21 consumer baseline while preserving the Java 25 build/product/PDE baseline. Preserve limitations for `apply` overrides, field hiding, peer-rule collective snapshots, accessibility, PER_CLASS construction order, provisioning and automatic conversion.

- [x] **Step 6: Verify, self-review and commit.**
  Run the focused tests while iterating and the complete support-module suite once per necessary final JVM/BOM combination. Standard entry: `./mvnw -f sandbox-junit-migration-support/pom.xml verify` with Java 25; select the consumer JVM through Surefire's supported JVM property rather than changing Maven's runtime. Record exact commands and actual XML counts. Run `git diff --check`, inspect artifact metadata/source identity, and measure changed lines against `49e12331b056bdf8ae9c7a07d9dce9e0245ba1b1`. Commit local code/tests/docs and append the full report at the supplied task report path; root reviews and publishes.
