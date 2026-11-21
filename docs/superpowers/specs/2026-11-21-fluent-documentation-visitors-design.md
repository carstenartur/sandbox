# Fluent visitor support for Java documentation

Eclipse JDT represents both classic `/** ... */` comments and Java 23+ `///` documentation comments as `Javadoc`. On JDT 3.47.0, both formats already reach fluent callbacks for documentation comments, tags, text, and Java references when documentation parsing is enabled. The scoped and independent builders reject callbacks for three JDT snippet node types because `VisitorEnum` has no entries for them.

## Approaches

Add `JavaDocRegion`, `JavaDocTextElement`, and `TagProperty` to the existing visitor dispatch. This gives callers typed access to JDT's existing DOM nodes through the current scoped, independent, and legacy APIs. A documentation-specific visitor facade would introduce a second callback model for the same nodes. Changing the traversal default would alter existing behavior without addressing registration.

## Behavior and verification

Use JDT's start and end callbacks and preserve its existing child traversal, scope, exclusion, and pruning behavior. Preserve both values accepted by the existing `HelperVisitor.build(ASTNode, boolean)` overload. The parser remains responsible for documentation support and the source language level.

Test both comment formats and real parsed snippet markup on Java 25 with the pinned JDT 3.47.0. Create a JDT DOM node to test external snippet text that contains a block comment delimiter. That fixture exercises the callback for the DOM type independently of external snippet file lookup.
