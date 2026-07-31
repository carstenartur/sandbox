# Generic harness-contract DSL

## Purpose

Legacy test frameworks often encode discovery, construction, ordering, selection,
lifecycle and matrix execution implicitly in Java inheritance and suite builders.
The migration engine must not contain project names such as JDT Core. Instead,
project-specific recipes describe those contracts using generic semantic facts,
composable predicates and structured rewrite actions.

The existing binding-resolved planner and JDT runtime-tree recorder remain the
safety boundaries. The DSL describes reusable policy; it does not replace scope
closure, binding resolution, atomic LTK changes or before/after execution.

## Composable predicates

Repeated guard expressions can be named and parameterized:

```text
<!predicate exactTest($method):
    plannedRole($method, "TEST")
    && isPublic($method)
    && !isStatic($method)
    && paramCount($method, 0)
    && hasReturnType($method, "void")>

public void $name() :: exactTest($name)
=> @org.junit.jupiter.api.Test public void $name()
;;
```

Predicates may compose other predicates. Expansion uses the parsed guard AST,
not global textual replacement. Therefore a same-named Java call in a source or
replacement pattern is not changed.

Predicate contracts are deliberately closed:

- parameters are explicit placeholders;
- every parameter must be used;
- undeclared placeholder captures are rejected;
- duplicate names, wrong arity and direct or indirect recursion are rejected;
- definitions retain source lines for editor diagnostics and trace output.

These rules keep predicates locally understandable and safe to reuse or rename.

## One language vocabulary

`HintLanguageVocabulary` is the canonical source for:

- metadata and declaration directives;
- DSL operators;
- built-in guard documentation used by content assist.

Syntax highlighting no longer contains a manually maintained guard list.
Identifier-shaped calls are highlighted structurally, so built-in guards,
extension-contributed guards and local predicates behave consistently. Bare
registered guards such as `otherwise` are obtained from the live registry.
Content assist combines the same registry with local predicate declarations and
de-duplicates by name.

A unit test compares the canonical guard documentation with the complete
built-in registration map. Adding a built-in without editor documentation is a
test failure rather than silent language drift.

## Parsing layers

The large compatibility-oriented `HintFileParser` remains the stable rule
parser. `HintProgramParser` is a small composition layer that:

1. extracts high-level declarations;
2. validates their contracts;
3. expands them into ordinary guard ASTs;
4. delegates the resulting program to the existing rule parser.

This avoids mixing comment handling, NetBeans compatibility, map/foreach
expansion, predicate composition and future harness-contract declarations in
one parser class.

## Next generic language slices

Predicates reduce duplication but do not yet express the complete harness
migration. The next extensions must stay generic and typed:

```text
contract legacy-tests/v1 {
    discover methods where exactTest($method)
    construct by namedConstructor(String methodName)
    order by planValue("ordering")
    select by planValue("selection")
    lifecycle perSuite("setUpSuite", "tearDownSuite")
    matrix dimension("compliance", planValue("levels"))
}
```

Required engine concepts are:

- typed semantic-plan values rather than untyped strings;
- binding-based graph predicates such as source-subtype and external-reference
  closure;
- structured AST actions such as replacing a supertype, removing a validated
  constructor, adding annotations and generating a nested adapter type;
- reusable contract fragments for discovery, ordering, selection, suite state
  and execution matrices;
- a generic runtime oracle requiring an identical non-empty successful test
  tree with configurable identity, nesting, order and multiplicity policies.

Project-specific recipes then supply only type names, method patterns and the
composition of these generic contracts.

## Acceptance criteria

Every language extension must include:

- parser and model tests for valid composition;
- negative tests for ambiguity, hidden capture, recursion and stale contracts;
- syntax-highlighting tests for every new lexical form;
- content-assist tests for declarations and references;
- a vocabulary-drift test where applicable;
- plan-aware end-to-end execution proving that the expanded program reaches the
  existing rewrite backend;
- at least one runtime-tree regression when execution semantics are affected.

A feature is not complete when only the parser accepts it. Editor support,
diagnostics, execution and tests are part of the language contract.
