# Deferred fixture hooks for staged JUnit migration

## Purpose and evidence

The merged staged support jar (#1971) accepts bound setup/teardown callbacks. The pinned JDT UI refactoring corpus also has rule fields assigned in constructors and mutable static class-rule fields. Binding `fixture::before` during field initialization can fail before the constructor assigns the fixture. Reading the field independently in setup and teardown can close a replacement object instead of the fixture that was opened. The pinned consumer project declares JavaSE-21; a Java 25 support jar cannot load in that consumer runtime.

This bounded follow-up supplies deferred, receiver-stable lifecycle registrations and qualifies the support artifact on Java 21. It is a prerequisite for later automatic mixed-lifecycle adoption. It does not itself implement source rewriting or claim the four remaining reference-matrix cases pass.

## Public contract

Keep the existing two-argument `perTest(Executable, Runnable)` and `perClass(Executable, Runnable)` APIs and their behavior. Add these overloads to `ExternalResourceExtension`:

```java
public static <T> Extension perTest(Supplier<? extends T> fixture,
        ThrowingConsumer<? super T> before, Consumer<? super T> after)
public static <T> Extension perClass(Supplier<? extends T> fixture,
        ThrowingConsumer<? super T> before, Consumer<? super T> after)
```

Use the existing Jupiter `ThrowingConsumer` contract; add no runtime dependency. Reject null callback/supplier arguments at registration. A non-null supplier may return null, which is a no-op fixture for that invocation, matching a null JUnit 4 rule.

At the matching lifecycle entry, evaluate the supplier exactly once. Call setup on that selected receiver and retain a cleanup action bound to the same receiver only after setup succeeds. At lifecycle exit remove the stored cleanup action before invoking it, so an exception cannot cause repeated teardown. A supplier or setup failure does not call teardown. Preserve original Throwable identity and the established Jupiter aggregation behavior. Store state per registration and actual invocation context, including repeated/concurrent tests; avoid shared mutable receiver fields. Wrapping the receiver in a cleanup action also avoids accidentally registering an AutoCloseable fixture for Platform store disposal.

## Runtime and build

The Sandbox build, Eclipse product, cleanup bundles, and PDE test bundle remain Java 25. Only the independent Maven support artifact and its unit-test classes target Java 21 through a module-local compiler override. Build with the supported Java 25 Maven runtime. Prove the packaged classes have class-file major version 65 and the generated OSGi execution-environment requirement is JavaSE 21. Run the actual support-module JUnit suite on a Java 21 JVM as well as Java 25, with the existing JUnit 6.1.3 baseline and the supported JUnit 5.14.4 consumer family. Do not bypass the Java 25 build-runtime enforcer or change global compiler properties.

## Validation and boundaries

Use real Vintage/Jupiter execution and event/test-count assertions. Cover constructor assignment, late static initialization, changing the field during the body, null values, supplier failure, setup failure, teardown failure and body failure, both lifecycle scopes, and repeated/concurrent invocations. Keep the old overload regressions passing. Compare the receiver opened and closed, supplier invocation counts, original failure identity, and zero unintended skips. Include a concrete overlapping-execution test that would expose registration-global receiver state.

Document the supplier overload with a constructor-assigned fixture example and the Java 21 consumer requirement. Keep existing scope/accessibility/setup guidance. Explicitly retain these limitations: hooks do not reproduce arbitrary `ExternalResource.apply` overrides; hook access must be legal; automatic source conversion and dependency provisioning remain separate; field hiding and ordering among multiple rules need review; separate suppliers do not promise JUnit 4's collective snapshot before any peer setup; `perClass` follows Jupiter lifecycle ordering and does not move PER_CLASS test-instance construction after class setup. Do not invent screenshots for this API-only change.

## Delivery

One ordinary PR against current main, under 1,500 changed text lines where practical. Use native Maven/JUnit evidence, no new Python test authority, suppressions, ignored failures or unrelated formatting. Keep exact build commands, runtime versions, test XML and artifact identity in the task report. Root owns independent review, publication and merge under the user's existing authorization.
