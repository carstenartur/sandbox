# Fluent Documentation Visitors Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to follow the steps in order.

**Goal** — Dispatch JDT's three snippet documentation node types through the existing fluent AST visitors.

**Architecture** — Extend `VisitorEnum` and `LambdaASTVisitor`, then expose the new types through the existing typed legacy builder and factory conventions. Scoped and independent builders reuse the existing shared dispatch.

**Tech stack** — Java 25, Eclipse JDT 3.47.0, Maven Wrapper, and JUnit Jupiter.

**Spec** — [Fluent visitor support for Java documentation](../specs/2026-11-21-fluent-documentation-visitors-design.md)

## Global constraints

Use the existing JDT DOM and preserve current traversal options. Keep the change in `sandbox_common_core` and run its Maven suite with SpotBugs.

## Review focus

Check that both comment formats reach Java references, scoped matches stay inside the selected comment, exclusions preserve descendants, and a rejected tag prunes only that stage. Check that the legacy boolean overload still behaves consistently for both values.

## Task 1 — Complete documentation dispatch

- [x] Capture the existing comment behavior and the three unavailable registrations in `DocumentationAstProcessingTest`. Use real parsed `@highlight` markup and a named region, plus a JDT-created `JavaDocTextElement`.
- [x] Run `./mvnw -pl sandbox_common_core -Dtest=DocumentationAstProcessingTest test`. Expect failures for the unavailable registrations and passing checks for the existing behavior.
- [x] Add the three enum values and matching start/end overrides. Add the typed entry points in `HelperVisitor`, `HelperVisitorFactory`, `ASTProcessor`, and `AstProcessorBuilder` and verify legacy callback execution.
- [x] Document parser configuration in `AstProcessing`.
- [x] Rerun the focused test and `./mvnw -pl sandbox_common_core verify` with Java 25. Expect passing JUnit tests and no SpotBugs findings.
