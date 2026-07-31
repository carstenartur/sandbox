# Generic harness-contract DSL

## Purpose

Legacy test frameworks often encode discovery, construction, ordering, selection,
lifecycle and matrix execution implicitly in Java inheritance and suite builders.
The migration engine must not contain project names such as JDT Core or JDT UI.
Instead, project-specific recipes describe those contracts using generic semantic
facts, composable predicates and structured rewrite actions.

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

## Typed semantic facts

Roles answer only whether a node participates in a transformation. Harness
migration also needs properties such as runner kind, lifecycle scope,
constructor mode, selection policy, ordering and matrix coordinates.
`SemanticRewritePlan` therefore carries immutable typed values:

- exact strings for enum-like contract values;
- booleans;
- signed integers;
- stable references to another plan node;
- homogeneous immutable lists.

Fact names have one value kind across a plan. Conflicting duplicate values or
conflicting kinds fail while the plan is built rather than becoming a silent
non-match later. The DSL reads those values with fail-closed guards:

```text
<!predicate selectedJupiterTest($method):
    plannedValue($method, "test-kind", "JUPITER")
    && plannedValue($method, "selected", true)>
```

`plannedNodeValue` compares a node-reference fact with another bound node,
`plannedListContains` checks a typed list, and `enclosingPlannedValue` searches
only the node and its semantic ancestors. Missing plans, facts, bindings or
wrong value kinds return false.

## Ordered semantic relations

Directed relations model suite membership, providers, wrappers, lifecycle
ownership, inheritance and matrix expansion without hard-coding any test
framework. Relations carry typed attributes and remain in planner order.
Duplicate relation occurrences are intentionally preserved because a runtime
suite may contain the same test more than once.

```text
<!predicate firstSuiteOccurrence($suite, $test):
    plannedRelation($suite, "CONTAINS", $test)
    && plannedRelationValue($suite, "CONTAINS", $test, "index", 0)>
```

The shared guards also support incoming/outgoing existence and exact outgoing
relation counts. Contract predicates can combine those primitives rather than
requiring a new Java guard for every framework concept.

Stable plan keys now cover ordinary, enum, annotation and record types, methods,
fields and exact method/constructor call sites. Field keys are needed for JUnit
4 `@Rule`/`@ClassRule` migration; exact constructor call sites are needed for
named JUnit 3 test construction and custom suite builders.

## Generic execution-tree oracle

`ExecutionTreeSnapshot` is the framework-neutral before/after contract. Adapters
convert a completed JDT JUnit model, another launcher or a dedicated harness
fixture into immutable containers and tests with stable semantic identities,
results, attributes and ordered children. No live framework model object is
retained after capture.

`ExecutionTreeComparator` requires a named policy rather than silently
normalizing differences. Available policy shapes include:

- strict structure, container identity, order, results and attributes;
- identical shape while allowing framework container names to change;
- ordered leaves while ignoring wrappers;
- an unordered leaf multiset.

Every policy preserves duplicate occurrences exactly. There is deliberately no
set-only mode that could hide a lost or duplicated test. Successful execution is
required by default, and result or attribute comparison can be relaxed only by
an explicit policy copy. The existing coordinated JUnit 3 hierarchy regression
now uses this generic model through a small JDT adapter.

For demanding migrations, the planner assigns stable identities to legacy and
replacement containers. That lets the strict policy compare semantic suite
structure even when the physical implementation changes from JUnit 3 suite
objects to Jupiter dynamic containers.

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

Typed facts, relations and execution-tree verification provide the shared data
and safety model, but they do not yet express all required AST changes. The next
extensions remain generic:

```text
contract legacy-tests/v1 {
    discover methods where exactTest($method)
    construct by planValue("construction")
    order by planValue("ordering")
    select by planValue("selection")
    lifecycle through relation("OWNS_LIFECYCLE")
    matrix through relation("EXPANDS_TO")
}
```

Required engine concepts are:

- a separate registrable structured-action channel rather than encoding AST
  operations as syntactically invalid Java replacement text;
- actions such as replacing a supertype, removing a validated constructor,
  replacing an annotation, qualifying an invocation and generating a nested
  adapter type;
- reusable contract fragments for discovery, ordering, selection, suite state,
  providers, wrappers and execution matrices;
- trace output containing rule ID, plan node, relation occurrence, binding key,
  typed values and rejection/skip reason.

Project-specific recipes then supply only type names, method patterns and the
composition of these generic contracts.

## Migration roadmap

### JDT Core

The first demanding fixture is the JDT Core JUnit 3 harness. The planner must
capture named test construction, custom `suite()` builders, inherited test-depth
selection, compliance-level multiplication, setup wrappers, ordering/filtering
and performance callbacks as typed facts and ordered relations. Unsupported or
incomplete concepts remain fail-closed. The migrated implementation is accepted
only when the existing JDT launch infrastructure records an equivalent
successful runtime tree under the declared identity, nesting, order and
multiplicity policy.

### JDT UI

After the JDT Core contract is under control, the same engine is applied to JDT
UI rather than creating a second migration framework. Its dominant input is
JUnit 4, so the planner and recipes will classify and transform different
concepts:

- `@RunWith` runners and suites;
- `@Rule` and `@ClassRule` fields and their extension mappings;
- parameterized constructors, fields and provider methods;
- categories, assumptions and custom runners;
- inherited lifecycle methods and shared assertion helpers;
- mixed JUnit 3, JUnit 4, Vintage and Jupiter execution during staged migration.

The typed field, call-site, fact and relation model is intentionally shared by
both projects. Only contract-specific planners, fact names and recipes differ.

## Acceptance criteria

Every language extension must include:

- parser and model tests for valid composition;
- negative tests for ambiguity, hidden capture, recursion, stale contracts and
  conflicting value kinds;
- syntax-highlighting tests for every new lexical form;
- content-assist tests for declarations and references;
- a vocabulary-drift test where applicable;
- plan-aware end-to-end execution proving that the expanded program reaches the
  existing rewrite backend;
- relation tests preserving explicit order and duplicate multiplicity;
- execution-tree regressions whenever discovery or runtime semantics change.

A feature is not complete when only the parser accepts it. Editor support,
diagnostics, execution and tests are part of the language contract.
