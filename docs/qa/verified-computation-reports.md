# Verified computation plans in mathematics reports

Each newly accepted mathematics edit now carries an `explanation` in its
analysis evidence. The workspace/CLI JSON report serializes this data using
the existing report writer; no additional mathematical optimizer is added.

`inputNames` and `outputNames` map SDK symbols to the actual Java source
names. `guardedReceivers` records adapter receiver checks separately from
numeric obligations. `verified` contains the independently reverified proof,
assumptions, runtime obligations, original and replacement computation DAGs,
shared intermediate nodes and separately labeled operation counts.

These are checked before/after computation plans, **not** a fabricated
step-by-step rewrite history. Counts are not timing measurements. Existing
source comments, source code, compact preview labels and Undo stay unchanged.
Optional generated Java comments and a human-readable derivation viewer are
future work. No constructor/halving recognizer or named example selector is
involved. Constant-only edits remain excluded.

Explanation generation replaces the previous standalone re-verification;
its SDK verification and rendering share one allowance and deadline. An
unverified, canceled or incomplete explanation cannot accompany an accepted
new edit. The original numeric/exception checks and emission verifier remain
authoritative. Historical test/report records may have no explanation; they
are not backfilled with invented proof data.

End-to-end tests serialize actual analysis evidence, compare exact typed and
JSON proof values, preserve checked-arithmetic obligations, exercise helper
calls, reject constant-only targets, and test cancellation and exact Undo.
The JSON proof comparison uses identical serialization/parsing on both sides
so Jackson's in-memory numeric-node width does not produce false failures.
