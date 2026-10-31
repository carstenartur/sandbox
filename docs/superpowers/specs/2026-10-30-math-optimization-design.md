# Useful verified mathematics optimization

The user authorized implementation of all five recommendations from the code review:
Java-aware candidate selection and magnitude-sensitive costs; realizable whole
computation tasks; stronger generic word arithmetic; source method summaries across
files; and complete explanation work accounting.

Keep the current split: Sandbox owns bindings, source/effect order, emission and
staleness; Regelsuche owns numeric graphs, proposals, independent proof and costs.
Use the existing search and typed graph, not a second solver or formula catalogue.
A configurable CAS remains a later provider behind Regelsuche; no new Python
process or mandatory CAS dependency belongs in the Eclipse plugin.

Implement the bounded straight-line integral fragment end to end. Preserve each
read, local commit, store and retained source call in its original order. Never
infer array equality from source spelling. Computed constants and comments retain
existing source protections. Helper summaries are source-bound and cache only
within one analysis; cross-class calls remain executed to preserve initialization
and exceptions. Unsupported loops, joins and dynamic dispatch remain diagnostic.

Candidate ranking uses typed operand magnitudes and the emitted Java footprint,
including captures and guards. It is an estimate, never a measured speedup claim.
All candidates must pass the existing independent numeric and emission proofs.
Keep source semantics, selected numeric types, cancellation and a shared bounded
work allowance. Explanation verification and presentation both debit that budget.

Qualification compares exact original/generated Java, exception-visible state,
aliasing, overflow, compilation and Undo. Run the full headless suites and matched
BC corpus scans, then native integration CI. Record genuine losses and uncertain
measurements; do not weaken tests or silently increase budgets to retain counts.

Alternatives considered: adding isolated formula rules would not connect source
effects; a general compiler/CAS replacement is unnecessarily broad. Extend the
existing typed task and search interfaces with bounded, independently checked
capabilities. Work in ordinary branches directly from main in each repository.
