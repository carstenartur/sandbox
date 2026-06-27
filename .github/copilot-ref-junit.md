# JUnit Cleanup Plugin Reference

> **Read this when**: Working on `sandbox_junit_cleanup` or `sandbox_junit_cleanup_test`.

## Purpose

Migrates JUnit 3/4 tests to JUnit 5:
- `extends TestCase` → remove, add annotations
- `setUp()`/`tearDown()` → `@BeforeEach`/`@AfterEach`
- `setUpBeforeClass()`/`tearDownAfterClass()` → `@BeforeAll`/`@AfterAll`
- `@Test(expected=...)` → `assertThrows()`
- `@RunWith(Suite.class)` → `@Suite`
- `ThrowingRunnable` → `Executable`
- `@Rule ExpectedException` → `assertThrows()

## Plugin Architecture

- `JUnitCleanUpFixCore` enum controls which migrations are active
- `JUNIT_CLEANUP_4_RUNWITH` → controls `RunWithJUnitPlugin` (handles Suite, Enclosed, Theories, Categories)
- `JUNIT_CLEANUP_4_SUITE` exists in `MYCleanUpConstants` but is **NOT mapped** — enabling it has no effect

## Key Helper Classes

Located in `sandbox_junit_cleanup/src/.../helper/`:
- `ThrowingRunnableJUnitPlugin` — migrates `ThrowingRunnable` → `Executable`
- `RunWithJUnitPlugin` — migrates `@RunWith` annotations
- `AssertionRefactorer` — refactors JUnit 3/4 assertions
- `ImportHelper` — manages import changes

## TriggerPattern DSL (.sandbox-hint files)

JUnit migration also uses `.sandbox-hint` DSL rules:

### Method Annotation Rewrite (Natural Syntax)
```
void $name($params$) :: methodNameMatches($name, "test.*") && !isStatic($name) && enclosingClassExtends("junit.framework.TestCase")
=> @org.junit.jupiter.api.Test void $name($params$)
;;
```

### Guards
- `methodNameMatches($name, "regex")` — regex method name filter
- `isStatic($name)` / `!isStatic($name)` — static vs instance
- `enclosingClassExtends("fqn")` — type hierarchy check (REQUIRED for JUnit 3 rules)

### Hint File Placement
- JUnit-specific hints go in `sandbox_junit_cleanup` via extension point in `plugin.xml`
- Generic hints go in `sandbox_common_core` bundled resources
- **NEVER duplicate** hint files between modules

## Recovered Bindings Warning

When using standalone `ASTParser`, types not on classpath produce **recovered** bindings:
- `isRecovered() == true` but `getQualifiedName()` returns "" or unreliable name
- **ALWAYS check** `binding != null && !binding.isRecovered()` before trusting
- Fall back to source-level names: `annotation.getTypeName().getFullyQualifiedName()`

Affects: `LambdaASTVisitor.java` — all annotation visit/endVisit methods and field type matching.

## ThrowingRunnable → Executable Migration

Handles generic type parameters too (e.g., `AtomicReference<ThrowingRunnable>` → `AtomicReference<Executable>`).

For `.run()` → `.execute()` migration through generics, uses 4 strategies:
1. Check `methodBinding.getDeclaringClass()`
2. Check `getMethodDeclaration().getDeclaringClass()` (unparameterized)
3. Check receiver method's return type
4. Check receiver's type arguments

## Adding New BuiltInGuards — Checklist

1. Register in `BuiltInGuards.registerAll()`
2. Update `testAllBuiltInGuardsRegistered` test in `BuiltInGuardsTest.java`
3. Add to `dsl-explanation.md` in BOTH `sandbox_common_core` AND `sandbox_mining_core`

## AstProcessorBuilder: Chaining is SCOPED

Chained `onXxx()` calls create **scoped** visitors — each runs inside previous match.
Use **separate** `AstProcessorBuilder` instances for independent visitors.

## ASTRewrite Queues Changes

`ASTRewrite.remove()` does NOT modify AST immediately. Track removal counts explicitly when inspecting the tree after queueing removals.