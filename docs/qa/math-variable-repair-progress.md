# Mathematics variable-region repair

Tracking #1657. This is a work ledger, not a completion or performance claim.

## Scope

The seven previously committed `MathVariableRegionsTest` cases are the red baseline for direct returns, multiline factoring, return conversions, externally used locals, checked overflow, guarded fallback and dead throwing operations.

This repair adds:

- numeric return expressions, with the method return conversion represented after the original expression;
- output liveness projection separate from the full original execution trace;
- exact-range removal of internal local statements, preserving adjacent comments and retaining declarations with embedded comments;
- compact pure emission: reuse input names, inline single-use nodes with explicit grouping, retain temporaries for shared operations;
- return-aware guarded branches without changing the original fallback;
- the qualified SDK at source `655809a500a18eda8dca6ad4e89b82751de0e521`.

The temporary `.github/math-region-fix.patch` transports the three edits to large existing source files into the Java-25 CI workspace. CI applies it with exact-source checks, runs the regressions and the entire mathematics test module, and only then commits the resulting source and vendored SDK to this isolated branch. On failure it retains the working diff instead of claiming completion. No patch is applied on main and no merge is automated.

## Boundaries retained

Ordinary BigInteger object-return identity, unproved parameter magnitude/dispatch, raw NaN-payload observations and unmodelled side effects remain conservative rejections. The repair does not switch off those checks. Checked source occurrences, including operations whose value is dead, remain in the request and exception checks.

## Still to qualify

Native preview and screenshots with runtime variables, final p2/product installation and complete-repository CI are separate gates. Do not close #1657 on the strength of the targeted tests alone.
