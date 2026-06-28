# Mining candidate integration tests

This note outlines a safe way to add optional integration tests for the staged mining pipeline.

## Goal

The normal test suite should remain deterministic and offline. Gemini-backed checks should be opt-in and only run when explicitly requested with credentials.

A useful integration test should verify the complete path for a small, known commit:

1. check out or clone the configured source repository,
2. extract the diff for a pinned commit,
3. build the mining prompt,
4. call the configured LLM provider,
5. validate the returned DSL rule,
6. stage a `MiningCandidate`,
7. assert that `known-rules.json` is only updated for the persisted candidate.

## FQN-aware DSL style

Candidate rules should normally express concrete APIs with fully qualified names. The matcher should accept target code that uses imports or simple names when imports or bindings prove that the code refers to the same API.

For example, a rule written for `java.util.Collections.emptyList()` should also match source code that imports `java.util.Collections` and calls `Collections.emptyList()`.

The same principle should apply to constructors, type references, method receivers, argument types, return types and overload-sensitive API migrations wherever the equivalence is knowable. This keeps mined rules readable and avoids duplicating rules for different source-code presentation forms.

## Suggested execution model

Use a JUnit 5 tag such as `@Tag("gemini-integration")` and gate the test with environment variables:

- `GEMINI_API_KEY`
- `RUN_GEMINI_INTEGRATION=true`
- optionally `GEMINI_MODEL`, defaulting to the model used by the workflow

The Maven invocation should be explicit, for example:

```bash
RUN_GEMINI_INTEGRATION=true \
GEMINI_API_KEY=... \
mvn -pl sandbox_mining_core \
  -Dgroups=gemini-integration \
  -DskipTests=false \
  test
```

The CI default must not run this tag.

## Recommended fixtures

Use a small set of pinned commits that are known to contain generalizable rules and keep each fixture narrow. Good candidates are rules already represented in `docs/mining-report/known-rules.json`, for example:

- Vector constructor modernization to ArrayList, guarded by local assignment context.
- `String.replaceAll("\\n", replacement)` to `String.replace("\\n", replacement)` for literal non-regex replacement.
- Deprecated wrapper constructors such as `new Float(x)` or `new Double(x)` to `valueOf(x)`.

Each fixture should define expected properties rather than exact prose:

- expected traffic light: `GREEN`
- expected `dslValidationResult`: `VALID`
- expected target hint file or category
- required DSL fragments, not the entire exact LLM response
- before/after/negative examples when available

## Reproducibility notes

LLM responses are probabilistic, so the assertions should not depend on exact wording. The useful measurement is whether the model still finds a valid, general rule under the current prompt and budget.

For cognitive-load/regression tracking, store a small JSON summary as a build artifact:

- model name
- prompt size
- diff size
- latency
- token usage if available
- traffic light
- DSL validation result
- candidate ID

This makes prompt regressions visible without committing live LLM output into the repository.
