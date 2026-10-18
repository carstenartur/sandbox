# From a Java change to a project rule

TriggerPattern can turn an ordinary Java edit into an explicit rule draft and
try that draft on a Java project through Eclipse's refactoring preview.

## Create a draft

1. Open a saved Java file, improve the code and leave the change unsaved.
2. Choose **Sandbox TriggerPattern → Generalize this change…** in the editor.
3. Review the actual changed lines from the saved file and current editor.
   Both source and replacement fields accept multiple lines.
4. Use **Choose variables to generalize…** to explicitly select shared
   variable-shaped names. Nothing is selected initially; the suggestions are
   syntactic and do not prove binding identity or scope. You can also enter a
   placeholder such as `$bytes` on both sides. Method names and string literals
   remain concrete until you explicitly change them.
5. Inspect the complete DSL document. Metadata, conditions and additional
   alternatives survive clipboard import and saving. The full document is the
   authoritative rule; the helper fields are conveniences for a new draft.

For code without an existing edit, select the entire snippet and use
**New hint file from selection…**. The complete selection is retained.
New drafts require resolved semantic information by default through
`<!binding-policy: required>`.

## Try the rule on real code

Choose **Try on my code…** in the wizard or the hint editor's TriggerPattern
menu. The mining view also offers **Try selected rule on my code…** after
explicitly selecting one language-checked draft and choosing a Java project.

The trial reads the current draft and the project's source, compiler options
and classpath. It does not install the rule. It uses the ordinary hint cleanup
backend, including import changes and supported Charset exception cleanup.

Candidate details show original placeholder text, available method/type
bindings, guard outcomes and the planned replacement. A failed guard and an
unknown binding are different outcomes. Details are limited to structural
candidates, with bounded output; the actual diff contains all prepared changes.

Use **Preview** to inspect the real Eclipse source diff. A file is selected as
one complete change so that imports and follow-up edits remain together.
The enabled selection is checked again before applying; deselecting a required
companion change blocks application without writing either file.
Applying the preview uses normal Eclipse refactoring undo. A changed source
snapshot invalidates the trial. Overlapping rules and replacements introducing
compiler errors block application. Files already containing compiler errors
are skipped with a warning. Cancel stops project traversal and AST parsing.

Self-contained text rules are supported by this trial. Includes, embedded Java
and coordinated semantic-plan actions require their existing specialized paths;
the trial reports these explicitly rather than silently treating them as
ordinary text replacements.

## Share the reviewed rule

Finish the wizard to save the complete `.sandbox-hint` file in the project.
The existing project-rule discovery makes it available to the normal hint
Quick Assist and hint-file cleanup. Commit the file with the project so the
team uses the same rule. Mining export preserves one complete candidate
document at a time; independent file metadata is not silently merged.

**Language checked** confirms DSL and source-pattern parsing. A project trial
also checks the bindings used by the rules and compiler diagnostics of its
replacements. Neither result proves behavioral equivalence. Positive and
negative examples, project tests and review remain necessary. A green AI
recommendation is displayed separately from these checks.

## Semantic guard contracts

Every executable rule declines an unknown necessary semantic requirement;
`optional` permits compatibility fallbacks only for diagnostic-only rules.
`hasNoSideEffect` is a recursive, conservative effects check. Missing or
recovered semantic information produces an unknown result, which a required
binding rule cannot use to authorize a rewrite. Calls, writes, allocation and
volatile reads are not accepted. This guard alone does not permit evaluating
an expression twice, skipping it or changing argument evaluation order.

`isStableValue` additionally requires a non-throwing stable read, currently
literals and resolved final local or parameter reads. It does not prove that
an object is immutable or that a newly introduced conversion is harmless.
Unsafe eager Optional and unrestricted null-equality migrations are therefore
informative suggestions until their stronger contracts can be established.

The [JDT reference cleanups](triggerpattern-jdt-reference-cleanups.md) document
the exact substring and isBlank guards and the deliberately narrower cases.
The test suite includes Unicode counterexamples for `trim()` versus `strip()`.

## Current limits

Generalization is explicit, rather than inferred from a single example.
Java text blocks are reported as unsupported during authoring validation until
the DSL parser can preserve their indentation; imported documents stay intact.
API patterns with qualified field receivers currently require their canonical
fully qualified spelling or an explicit receiver placeholder.
Generic statement replacements containing comments are withheld when the
backend cannot preserve them. The complete draft still retains those comments.
Automatic refinement from additional examples/counterexamples, persisted
example certifications, build-mode parity, measured performance comparisons
and an extracted upstream core remain separate follow-up work. No performance
or upstream-equivalence result is implied by the interactive workflow.
