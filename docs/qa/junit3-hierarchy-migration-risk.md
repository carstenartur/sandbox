# JUnit 3 hierarchy migration risk

## Finding

JUnit 3 test identity is implicit and hierarchy-dependent. A method is a test because it is discovered in the context of a `junit.framework.TestCase` hierarchy and follows the JUnit 3 method contract. Removing that hierarchy before every affected method and lifecycle contract has been materialized destroys information that later cleanup passes cannot reconstruct reliably.

The current cleanup does not model this as an atomic hierarchy migration:

- the single JUnit 3 option delegates to one plugin that removes `extends TestCase`, annotates test and lifecycle methods, and rewrites inherited assertions in the same local rewrite;
- additional compilation-unit discovery currently expands scope for selected JUnit 4 suite and `ExternalResource` migrations, but not for JUnit 3 base classes, subclasses, or suite owners;
- the local `isTestMethod` heuristic accepts alternative names and ultimately every public, parameterless `void` method, which is broader than JUnit 3 test discovery and can annotate helpers as tests;
- tests cover direct, single-file `TestCase` subclasses but not abstract base classes, inherited tests, lifecycle override chains, or descendants outside the selected cleanup scope;
- the lost-test cleanup is not a substitute for preserving identity: it runs after explicit annotations already exist somewhere in a hierarchy and only guesses local `test*` methods.

## Required safety model

A destructive JUnit 3 migration must operate on a closed, binding-resolved hierarchy and preserve a baseline inventory of test identities before changing inheritance.

The migration must fail closed when any relevant source type, suite, constructor, lifecycle override, or custom JUnit 3 execution hook cannot be classified.

### Recommended staged path

1. **Inventory the JUnit 3 execution contract**
   - collect direct and transitive `TestCase` subclasses;
   - include abstract bases and all source descendants;
   - identify inherited and overridden `test*` methods using the exact JUnit 3 signature contract;
   - identify `setUp`/`tearDown` override chains and explicit `super` calls;
   - identify `suite()`, `runTest()`, `getName()`/`setName()`, `TestSetup`, `TestDecorator`, custom constructors, and custom runners/adapters.

2. **Materialize implicit identity without removing the safety net**
   - preferably create an explicit JUnit 4 intermediate representation first;
   - annotate every proven test and lifecycle method across the closed hierarchy;
   - qualify or statically import inherited assertion methods;
   - retain `TestCase` inheritance until the explicit representation is complete and verified.

3. **Detach JUnit 3 inheritance atomically**
   - remove `TestCase` only when every affected source unit is in the same migration plan;
   - preserve lifecycle ordering and avoid double execution caused by both annotations and retained `super.setUp()`/`super.tearDown()` calls;
   - reject unsupported suite, constructor, name-based, decorator, or `runTest()` semantics rather than guessing.

4. **Use the existing JUnit 4 to Jupiter migration afterwards**
   - once test identity is explicit, annotation, assertion, lifecycle, rule, and runner migrations may be staged more safely;
   - JUnit Vintage can provide the temporary compatibility boundary while JUnit 3/4 and Jupiter tests coexist.

A direct JUnit 3 to Jupiter mode may remain possible, but only as one atomic hierarchy transformation with the same inventory and verification requirements. It must not expose superclass removal, test annotation, lifecycle conversion, and assertion conversion as independently destructive choices.

## Immediate mitigation

Until the hierarchy-aware planner exists, the current JUnit 3 cleanup should be documented and implemented as fail-closed. At most, it may accept a narrowly proven trivial case:

- one direct concrete `TestCase` subclass;
- no source subclasses;
- no inherited test or lifecycle methods;
- exact public, parameterless `void test*` methods only;
- no custom constructors, `suite()`, `runTest()`, name access, decorators, or adapters;
- no lifecycle override chain or explicit superclass lifecycle call;
- all affected methods and assertions available in the selected compilation unit.

All other shapes should produce structured rejection diagnostics and no source changes.

## Verification requirements

- baseline and post-migration test inventory with stable class/method identities;
- active tests for abstract bases, inherited tests, overridden tests, inherited lifecycle, explicit superclass lifecycle calls, multi-level hierarchies, and selected-scope gaps;
- negative tests for helper methods that are public parameterless `void` but do not start with `test`;
- negative tests for custom constructors, `suite()`, `runTest()`, decorators, and unresolved bindings;
- representative integration fixtures modeled after inheritance-heavy Eclipse test projects;
- no dependency removal until both source transformation and test discovery equivalence have been verified.
