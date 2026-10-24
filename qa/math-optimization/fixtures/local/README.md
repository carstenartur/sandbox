# Default mathematics benchmark inputs

These sources were exported on 2026-10-24 by the real
`MathCorpusIntegrationTest` analysis/proof/edit pipeline at source commit
`17b735e1c52f830682fa92b6c7c1c7d22eb5d09c`, using SDK JAR SHA-256
`40707f43a79b3c9aea6edbed4f5c2fc1be506665fd7cfa864f5a507f6ae7129e`.
All seven corpus integration tests passed, including original/generated
Java-17 compilation, edge/random execution and second-pass stability.

They are local affine BigInteger examples, not Bouncy Castle code or a claim
about cryptographic performance. `PRESERVE_JAVA` and the estimated-runtime goal
produce the recorded generated bodies. Timing is measured separately by JMH.

Use `MATH_QA_WRITE_FIXTURES=/absolute/fresh-directory` with the existing
`qa/math-optimization/run.sh test` adapter to regenerate for review; never
hand-edit generated bodies. Run JMH from the repository root. The ordinary
Maven benchmark contract test compiles and executes both registered fixtures.
