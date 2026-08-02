# TriggerPattern safety contracts

TriggerPattern supports ordinary local hints and planner-authorized coordinated migrations. These modes have different safety requirements and must not be treated as interchangeable.

## Semantic-plan contract

A plan-aware program declares one contract:

```text
<!requires-plan: junit3-hierarchy>
```

That declaration is complete. It identifies the required `SemanticRewritePlan` and also implies fail-closed semantic bindings. A second binding-policy directive would repeat information already determined by the plan dependency and is therefore unnecessary.

The semantic planner supplies an immutable authorization graph containing:

- stable binding-based node keys;
- authorization roles;
- typed node facts;
- ordered directed relations and relation attributes.

The plan-aware execution boundary rejects an empty or mismatched plan. Every produced rewrite must resolve to a current AST node with a stable semantic key authorized by that plan. Execution also fails when planned targets can no longer be identified, the source has changed so that coverage is incomplete, or a rule requests an analysis-dependent replacement that cannot be reproduced safely.

## Binding policy for ordinary hints

Ordinary hints without `requires-plan` retain compatibility behaviour by default. A migration whose correctness depends on resolved overload, owner, type, generic or hierarchy information can opt into the same fail-closed guard semantics explicitly:

```text
<!binding-policy: required>
```

`optional` preserves the historical boolean fallback. `required` evaluates guards as `MATCH`, `NO_MATCH` or `UNKNOWN`; an unresolved semantic requirement produces a diagnostic transformation result and no rewrite. An unknown ordered alternative does not fall through to `otherwise`. A proven branch can still decide the expression, for example `UNKNOWN || MATCH` is `MATCH`.

A plan-aware program may omit `binding-policy` because `requires-plan` is already strict. A contradictory `binding-policy: optional` declaration is rejected.

## Text rewrites versus structured actions

Use ordinary `=>` replacement when the desired result can be expressed directly as target code for a local expression, statement, or supported declaration shape.

Use `=>!` structured actions when a rewrite requires typed AST operations, import management, or a semantic-plan value that cannot be represented safely as a plain replacement.

Structured actions are a normalized execution form rather than a reason to duplicate source information. New action syntax should prefer implicit context, stable bindings, and target-code derivation wherever these remain unambiguous. A plan-aware rule may not mix text and structured alternatives; split the behavior into separate rule IDs so coverage and execution order remain reviewable.

## Where semantic planning is required

A local rule is insufficient when correctness depends on any of the following:

- a closed inheritance hierarchy;
- callers or providers in other compilation units;
- test identity, multiplicity, or execution order;
- a runner, suite, or framework callback contract;
- edits across projects or bundles;
- coordinated Java and resource/dependency changes.

The existing wizard should describe such a transformation as requiring a semantic planner instead of generating an unsafe broad pattern.

## Authoring checklist

Before shipping a migration rule:

1. Decide whether the transformation is local or plan-aware.
2. For an ordinary binding-dependent migration, declare `binding-policy: required`.
3. Express the desired target code once wherever the DSL can derive the typed AST operations.
4. Put only non-derivable scope, role, relationship, and strategy information in the semantic plan.
5. Let `requires-plan` be the sole plan contract.
6. Avoid repeating a target, name, type, or position that the pattern or target representation already determines.
7. Prefer stable bindings and planned ordered relations to positional indices.
8. Add positive, negative, ambiguity, unresolved-binding and stale-plan tests.
9. For test migrations, compare the relevant JDT JUnit test tree and results where identity or multiplicity can change.

The broader implementation plan is tracked in #1367.
