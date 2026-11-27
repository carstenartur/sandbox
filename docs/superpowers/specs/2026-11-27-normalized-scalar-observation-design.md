# Exact observations after scalar root normalization

**Scope, 27 November 2026.** Regelsuche PR1415 supplies a generic proof that a jointly negated canonical root pair has the same exact value after a compatible parity normalization. Sandbox must establish that observation from the actual Java source before requesting a weaker raw-root contract. No Bouncy Castle upstream change is part of this slice.

## Mathematical contract

For an odd prime and canonical coordinates, negation changes every nonzero coordinate's parity. A selector choosing the first nonzero coordinate therefore changes its bit under joint negation. Normalizing either orientation to the same target bit gives the identical ordered pair; the zero pair is fixed. `QuadraticRootSdk.verifyNormalized` checks the original root certificate, bound normalizer contract and all parity cells under one shared work/cancellation budget. Java effects, bindings and target stability remain Sandbox obligations.

Use the immutable SDK closure in `sandbox_math_cleanup/lib/qualification.json`; its README records the merged source and historical receipts. Preserve genuine screenshot identities.

## Admitted source

The root, caller and fresh pair factory are static, nonsynchronized and on the same top-level owner without explicit hierarchy. The factory has exactly two ordered BigInteger formals and only `return new BigInteger[]{first, second};`. Admit an explicitly typed, one-fragment, one-dimensional array declaration and a contiguous suffix ending in a source-proven value sink:

```java
BigInteger[] pair = root(a, b, g);
BigInteger x = pair[0];
BigInteger y = pair[1];
if ((x.signum() != 0 ? x : y).testBit(0) != target) {
    x = x.negate().mod(P);
    y = y.negate().mod(P);
}
return encode(x, y);
```

- Support real-first and imaginary-first selectors; decode their actual operand roles and bit zero. No else, additional statements, one-coordinate negation or unreduced negation.
- Require the exact JDK BigInteger types/signatures and resolved declaration bindings. Both reductions read the exact modulus field consumed by the root, on the same owner. Equal numerical values of different fields are insufficient.
- The target is an explicitly primitive-boolean, effectively-final parameter or preceding local. Reject boxed targets, getters, writes and inferred target declarations. A foreign method signature must not silently change an inferred type after analysis.
- Enumerate every occurrence of the pair and both coordinates across the caller, including nested trees. Their reference sets must equal the consumed source nodes. Reject aliases, escapes, captured values, intermediate effects and raw/identity observers.
- Audit the final static sink with the existing effects auditor before the value-sink auditor. Require exact two-coordinate formals, a source definition and complete transitive source receipts. The lossless fixture encodes both `toByteArray()` outputs with a four-byte first-length prefix; do not truncate large coordinates.
- Keep default exact-source admission unchanged. A named normalized admission path proves the fresh array separately; a raw array return or unsupported normalizer never acquires a raw-sign optimization.
- Reuse source-derived input facts where proved. Otherwise pass three explicit UNKNOWN facts and retain every runtime domain/class/norm/constant guard. Never use the legacy null-facts mode, which assumes a preceding imaginary-coordinate dereference.
- Bind the lookup that selects each static root, factory, modulus and sink member, including transitive source calls, constants and constructor-derived facts. Require a directly bound same-package source owner or a simple type spelling with an explicit single-type import; decline unproved implicit, dotted or inherited lookup. A terminal JDT binding or file hash cannot validate stale lookup facts. No class-name, fixture-hash or prime-specific optimization.

## Emission and Java observations

Preserve the original root and raw callers byte-for-byte. Split the explicitly typed pair declaration from its initializer. Immediately follow it with a block containing the candidate temporary and a single assignment of the pair from the nullable choice/direct original fallback. Close that block before the original x/y declarations, normalization and final return.

The single assignment supports final pairs. Decline `var` pairs and extra declarator dimensions. Use insertion-only edits around the original name, invocation and semicolon to restore comments and Unicode spelling exactly. A method-scope temporary can change later helpful NPE messages; qualify actual Apply output with javac25 default/no-debug/full-debug and pinned ECJ no-debug. Braces alone do not prove slot allocation.

The exact B path's terminal-continuation check remains intact. The scoped normalized route has its own complete source obligation and must not become a generic bypass. Runtime exceptions retain the established type/message and ordered-effect model; diagnostic source/bytecode locations are outside that model.

## Proof replay and freshness

- Search `UP_TO_JOINT_SIGN` only after source admission; require both decoded candidate arms to have character normalization disabled. Keep `EXACT_PAIR` for all other consumers. Never strip character correction from a previously exact certificate.
- Reparse the combined emitted source, verify the outer blank declaration, exactly two inner block statements, assignment to the original pair, protected null choice and direct original call. Count exactly the two temporary reads, one extra pair assignment and one actual helper invocation across the unit.
- Restore the original caller bytes and statement ordinal independently of generated names/offsets. Derive root identity from actual original method binding/text and bind its actual program, modulus and independently acquired prime evidence.
- Derive the normalizer identity from length-delimited source fields: root identity, restored caller key/hash/ordinal, factory/sink keys and body hashes, local/target/modulus bindings and selector. Compare every asserted receipt field with reconstructed facts; supplied identities are untrusted.
- Rebuild the supplied root certificate around the decoded program and invoke `verifyNormalized` with the remaining shared work budget and cancellation. Recheck live source receipts before releasing Apply; unrelated accumulated request receipts do not define a proof's identity.
- Require an existing scoped candidate to pass the same source/guard/program/normalizer verification before reserving its root. Preserve one specialization per root and whole-file fixed point; handwritten lookalikes cannot suppress another eligible site.

## Acceptance evidence

Ordinary Apply must show an opposite raw pair on the full 381-bit field, then identical complete encoded results for both selectors and target bits. Include `(3,4,p-5)` with original `(p-2,p-1)` versus candidate `(2,1)`, large interior coordinates, both candidate branches and equal orientations. The existing prime catalog supplies evidence; no new small-prime cap or prime-specific optimization is permitted.

Cover fresh-factory order/effects, aliases and identity sinks, wrong selector/bit/target/modulus, missing reduction or negation, synchronization, extra helper uses, null/subclass/domain/norm failures, real/zero and partial-initialization fallback, throwing sinks, exact exception messages, source/certificate tampering, external/transitive freshness, combined edits, Apply/Undo and repeated whole-file Apply. Test semantic replay with updated envelopes where necessary to avoid testing only a hash mismatch.

Maven/JUnit is the executable authority. Strict Java25 compilation includes changed production/tests and generated sources. Keep the pinned opaque Montgomery BC corpus as an exact/Jacobi control: the scalar grammar does not prove that consumer. Do not infer speed from eliminating a character loop; the completed BC comparison measures the unchanged exact output separately.
