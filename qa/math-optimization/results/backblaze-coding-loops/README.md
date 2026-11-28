# Backblaze Reed–Solomon coding loops

This qualification exercises ordinary Java mathematics analysis and Apply on
all implementations in the upstream `CodingLoop.ALL_CODING_LOOPS` inventory.
It uses Backblaze/JavaReedSolomon at
[`d3c481dc`](https://github.com/Backblaze/JavaReedSolomon/tree/d3c481dc69471e0c47ff6f67f33d53bde941675e).
Its twelve alternatives implement six loop orders with two different
finite-field multiplication methods.

## Protocol

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
