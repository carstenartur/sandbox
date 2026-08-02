# TriggerPattern safety contracts

TriggerPattern supports ordinary local hints and planner-authorized coordinated migrations. These modes have different safety requirements and must not be treated as interchangeable.

## Semantic-plan contract

A plan-aware program declares one contract:

```text
<!requires-plan: junit3-hierarchy>
```

That declaration is complete. It identifies the required `SemanticRewritePlan` and also implies fail-closed semantic bindings. A second binding-policy directive would repeat information already determined by the plan dependency and is therefore not part of the language.

The semantic planner supplies an immutable authorization graph containing:

- stable binding-based node keys;
- authorization roles;
- typed node facts;
- ordered directed relations and relation attributes.

The plan-aware execution boundary rejects an empty or mismatched plan. Every produced rewrite must resolve to a current AST node with a stable semantic key authorized by that plan. Execution also fails when planned targets can no longer be identified, the source has changed so that coverage is incomplete, or a rule requests an analysis-dependent replacement that cannot be reproduced safely.

Ordinary hints without `requires-plan` retain the existing compatibility behavior. The current guard API is boolean; a general `MATCH` / `NO_MATCH` / `UNKNOWN` model and actionable unresolved-binding diagnostics remain a separate follow-up. Extra syntax for non-plan binding requirements should only be introduced together with that executable semantics.

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
2. Express the desired target code once wherever the DSL can derive the typed AST operations.
3. Put only non-derivable scope, role, relationship, and strategy information in the semantic plan.
4. Let `requires-plan` be the sole plan contract.
5. Avoid repeating a target, name, type, or position that the pattern or target representation already determines.
6. Prefer stable bindings and planned ordered relations to positional indices.
7. Add positive, negative, ambiguity, and stale-plan tests.
8. For test migrations, compare the relevant JDT JUnit test tree and results where identity or multiplicity can change.

The broader implementation plan is tracked in #1367.
