# TriggerPattern safety contracts

TriggerPattern supports ordinary local hints and planner-authorized coordinated migrations. These modes have different safety requirements and must not be treated as interchangeable.

## Binding policy

A hint program may declare:

```text
<!binding-policy: optional>
```

or:

```text
<!binding-policy: required>
```

`optional` is the compatibility mode for ordinary style hints. It permits rules that can still make a conservative decision when Eclipse cannot resolve every semantic binding.

`required` is the migration mode. It states that type, method, field, owner and hierarchy information are part of the transformation contract. A program that also declares `<!requires-plan: ...>` must use `required`; the plan-aware execution boundary rejects missing, malformed, unknown or conflicting policies before parsing or rewriting source.

Commented directives do not affect the policy. Duplicate identical directives are accepted so composed/generated programs remain stable; conflicting directives are rejected.

## Plan-aware programs

A plan-aware program starts with both directives:

```text
<!binding-policy: required>
<!requires-plan: junit3-hierarchy>
```

The semantic planner supplies an immutable `SemanticRewritePlan` containing:

- stable binding-based node keys;
- authorization roles;
- typed node facts;
- ordered directed relations and relation attributes.

The hint program may query only those facts through plan guards and action values. Structured actions resolve exact authorized targets again against the current AST. The execution fails if the plan contract differs, the plan is empty, a target cannot be re-identified, the current source no longer covers the planned targets, or the hint program produces incomplete coverage.

## Text rewrites versus structured actions

Use ordinary `=>` replacement for a local expression or statement replacement whose target structure remains the same.

Use `=>!` structured actions for declaration changes such as:

- adding or removing annotations;
- adding or removing modifiers;
- removing or replacing supertypes;
- removing a declaration;
- qualifying an exact static invocation.

A plan-aware rule may not mix text and structured alternatives. Split the behavior into separate rule IDs so coverage and execution order remain reviewable.

## Where semantic planning is required

A local rule is insufficient when correctness depends on any of the following:

- a closed inheritance hierarchy;
- callers or providers in other compilation units;
- test multiplicity or execution order;
- a runner, suite or framework callback contract;
- edits across projects or bundles;
- coordinated Java and resource/dependency changes.

The existing wizard should describe such a transformation as requiring a semantic planner instead of generating an unsafe broad pattern.

## Authoring checklist

Before shipping a migration rule:

1. Decide whether it is local or plan-aware.
2. Require bindings for every migration whose overload or ownership affects meaning.
3. Add positive, negative and unresolved-binding tests.
4. Give every rule a stable ID.
5. Use fully qualified replacement types or typed structured actions for reliable imports.
6. Prove idempotency and stale-plan rejection.
7. For test migrations, compare the relevant JDT JUnit test tree and results where identity or multiplicity can change.

The broader implementation plan is tracked in #1367.
