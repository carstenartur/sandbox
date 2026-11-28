# Backblaze Reed–Solomon coding loops

This qualification exercises ordinary Java mathematics analysis and Apply on
all implementations in the upstream `CodingLoop.ALL_CODING_LOOPS` inventory.
It uses Backblaze/JavaReedSolomon at
[`d3c481dc`](https://github.com/Backblaze/JavaReedSolomon/tree/d3c481dc69471e0c47ff6f67f33d53bde941675e).
Its twelve alternatives implement six loop orders with two different
finite-field multiplication methods.

The [complete-region follow-up](complete-region/README.md) adds source-bound recognition of two table reductions and tests their mutable Java behavior. The [four-input prefix experiment](complete-region/prefix/README.md) records the exact Apply patch and full-call timing against the original encoders.

## Initial scan protocol

Compile the complete original Java library for Java 17. Use all upstream
production sources as binding context. Select only the twelve registered
coding loop classes. Run the existing strict and mathematical-opt-in profiles
with their unchanged budgets (one million work units, 20,000 states and five
seconds per file), in that order. Export typed task diagnostics alongside
the usual reviewed source regions. For each profile compile the original
sources plus the exact generated overlay, check upstream parity encoding and
verification on a partial buffer using every loop and compare the complete
shards with the original default loop. Report all twelve inputs even when
there are no proposed changes. The earlier Bouncy Castle work and this
announced corpus are part of the development record. A transfer-to-unseen-
families claim requires separately reserved data.

Use the [qualified SDK build](../../../../sandbox_math_cleanup/lib/README.md)
and Temurin 25.0.5+8. From the Sandbox checkout:

```sh
./mvnw -f qa/math-optimization/pom.xml test -Dtest=ReedSolomonCodingLoopsTest \
  -Dsandbox.math.backblaze=true \
  -Dsandbox.math.backblaze.output="$(pwd)/qa/math-optimization/target/backblaze-run"
```

Supply a fresh output path. Each profile's `review.json` contains source,
runtime and profile hashes, warnings, extracted regions, decisions and task
diagnostics. An existing local upstream clone may be used with
`-Dsandbox.math.backblaze.mirror=/absolute/path`. The fixture checks the
mirror's upstream URL and the pinned commit. Local differences are excluded.

## Observed on 28 November 2026

Sandbox production code at `145cd6bd8087da9145ff18937bb0304b3a7744ab` used
the six SDK jars and seven POMs qualified at Regelsuche
`e29218175e11af2d7f16fcc42bb42e995c8d9567`. The source archive SHA-256 was
`6673c22525244e510e2637e15786ced3879afa4d0ff38a00e8bf16dc6d7ac7e9`
and the compiled archive SHA-256 was
`e34ebb6993241cbdc2d1dbecc44aa9b30d8fea6f9421199a6bb00cf8fc38180f`.
The [complete inventory](scan.tsv) binds each implementation to its source
hash and records the outcome under both profiles.

| Profile | Bound source files | Selected/parsed | Parse errors | Extracted runtime regions | Accepted edits |
| --- | ---: | ---: | ---: | ---: | ---: |
| Strict Java | 20 | 12/12 | 0 | 2 | 0 |
| Mathematical opt-in | 20 | 12/12 | 0 | 2 | 0 |

The two regions occurred in the output–byte–input permutations. Both reported
`NOIMPROVEMENT`. The [29 typed task spans](task-spans.tsv) were identical
across profiles: fourteen zero initializations, twelve single array reads,
two stores of an existing value and one store of a table lookup. All three
compiled runs (original, strict and mathematical) produced the same complete
shard buffers using each of the twelve implementations. Verification accepted
the valid parity and rejected a single corrupt byte in every run.

No task spans a complete accumulation across input shards. For example,
`InputOutputByteTableCodingLoop` looks up a GF(256) product through two
indexed tables and XORs the value into a mutable output buffer inside three
nested loops. The typed fragments stop before that operation sequence and its
read/write ordering. Both profiles report 72 side-effect boundaries, 12 array
store boundaries and seven array read dependency boundaries across candidate
locations. These diagnostics may overlap. No verified mapping from this corpus
to a scalar algebraic schema was exercised, and no transformed encoder was
available to benchmark.

The separate [upstream throughput baseline](baseline/README.md) measures
all twelve unmodified encoders on three shard geometries and includes the
raw windows from 108 JVM forks. To repeat the measurements alongside the
source scan, add `-Dsandbox.math.backblaze.timing=true` to the command above.
