# TriggerPattern: two conservative JDT String cleanup comparisons

The bundled `string-modernization` and `string-isblank` libraries demonstrate
two existing JDT cleanup families through the same TriggerPattern batch path.
The source patterns and replacement variants remain declarative; their
binding-dependent conditions live in `StringCleanupGuards` and operate on the
original matched AST nodes. These are conservative reference implementations,
not replacements for JDT's implementations or a claim of complete parity.

## Examined upstream sources

The comparison was made against the actual upstream sources retrieved on
2026-10-18:

* [SubstringCleanUpCore](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/c11bc906124cb07c1e5abbf29b4805164754d37c/org.eclipse.jdt.core.manipulation/common/org/eclipse/jdt/internal/ui/fix/SubstringCleanUpCore.java)
  (retrieved file blob `1cc96456b05ae7c7088b1495e26c6b634fc3cfba`).
* [UseStringIsBlankCleanUp](https://github.com/eclipse-jdt/eclipse.jdt.ui/blob/c11bc906124cb07c1e5abbf29b4805164754d37c/org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UseStringIsBlankCleanUp.java)
  (retrieved file blob `51e976cffc48cc5b732cb26d3c3dd3f3d71a2790`).

The commit above is the upstream search snapshot. The retrieved file blob IDs
identify the exact files reviewed independently of later branch changes.

## Substring

```java
text.substring(start, text.length())
// becomes
text.substring(start)
```

The existing rule now requires `stringSubstringSafe()` rather than the generic
`hasNoSideEffect($str)` guard.

| Upstream requirement | TriggerPattern condition | Executable evidence |
| --- | --- | --- |
| `String.substring(int, int)` | Exact resolved declaring type, name and parameter types | Positive String example; same-named custom API is rejected |
| `String.length()` on the end-index receiver | Exact resolved no-argument String method | Lookalike and unresolved API negatives |
| Both receiver expressions match | Same local variable binding, or matching String literals | Different receiver negative |
| Passive receiver | Stable local/parameter or String literal only | Method-call receiver and volatile field negatives |
| Preserve the remaining code | Replace only the matched expression | Full-source comparison and second-pass no-op |

There are two additional restrictions. All field receivers are declined, and
the start index must consist of passive local expressions, primitive literals,
operators or primitive casts. Calls, arrays, fields, allocations and assignments
are declined. This prevents an intervening computation from changing the value
of the receiver whose second evaluation is removed. It is deliberately stricter
than the upstream receiver-passivity check.

## String.isBlank

```java
text.strip().isEmpty()
text.strip().length() == 0
"".equals(text.strip())
text.strip().equals("")
// each becomes
text.isBlank()
```

The resource contains 27 explicit variants: the three Unicode `strip` methods,
three empty checks, and six length comparisons for each method.

| Upstream requirement | TriggerPattern condition | Executable evidence |
| --- | --- | --- |
| Java 11 or higher | `sourceVersionGE(11)` on every variant | Java 8 negative |
| `String.strip`, `stripLeading` or `stripTrailing` | Exact resolved declaring type and zero parameters | All three methods exercised; lookalike rejected |
| String `isEmpty`, `length` or `equals(Object)` | Exact resolved method signatures through `stringIsBlankSafe()` | All variants exercised; unresolved/recovered bindings rejected |
| Empty-string constant | Explicit empty String literal in equals variants | Both equals directions exercised |
| Length comparisons with 0 or 1 | Explicit `== 0`, `<= 0`, `< 1`, `!= 0`, `> 0`, `>= 1` variants | All six comparisons for all three strip methods |
| Negated result for nonempty checks | `!$str.isBlank()` in the negative variants | Full-source comparisons for negative variants |

`trim()` is deliberately excluded. Its definition of whitespace differs from
`isBlank()`: `"\u0000".trim().isEmpty()` is true while `"\u0000".isBlank()`
is false; the result is reversed for `"\u2003"`. JDT's implementation also uses
only the Unicode `strip` family. The previous bundled `trim` rules were unsafe
and have been removed.

Compared with upstream, the reference variants accept literal `""`, `0` and `1`
in the written operand order. They do not claim coverage for every constant
expression, alternate numeric spelling, or reversed length comparison accepted
by JDT's normalization helpers.

## Comments, imports and bindings

JDT uses AST rewrite operations, including comment-preserving operations in its
isBlank cleanup. The core TriggerPattern batch result currently uses text
substitution. Both guards therefore decline expressions containing internal
comments. This preserves the source until a comment-preserving rewrite path is
available; it does not silently drop or relocate comments. Comments around the
expression and existing imports survive the rewrite unchanged. Neither cleanup
requires a new import.

Missing or recovered method bindings produce an unknown semantic prerequisite
and never authorize replacement. The isBlank library explicitly declares
`binding-policy: required`, allowing the existing diagnostics path to explain
the missing prerequisite. The substring guard also fails closed within the
larger modernization library.

`JdtStringCleanupContractTest` uses binding-resolving JDT AST parsers and the
real `BatchTransformationProcessor`. It checks positive full-source results,
compilation of transformed fixtures, negatives, source levels, surrounding and
internal comments, and idempotence. It is a headless JUnit test; it does not
constitute an Eclipse workbench test, an execution of JDT's original test suite,
a performance comparison, or proof that every existing JDT fixture is covered.
