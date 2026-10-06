# Fresh provisional reconstruction check

This is a new execution of the reconstructed code, not recovered historical
output. All 14 selected tests passed with no skipped or aborted tests, using
JDK 25.0.2+10-LTS and a 512 MiB test heap. `test.log` and the JUnit XML contain
the actual run. The initial test-first scanner compile failure is retained
separately in `../reconstruction-checkpoint/`.

The SDK was explicitly provisional: revision
`4fc49eaf9c4ebd700027c1005dcdb69127d4a316`, all-JAR SHA-256
`f8969808aa92ccdc48db0cfac214cd572c16d0d82e90be55f44709b6d61935f4`.
Its final packaging and release gates had not completed. Sandbox sources were
being reconstructed concurrently, so this is not a source freeze or release
qualification. The source receipts honestly record a dirty working tree.

The before/after implementation receipts match. They record 30 math-core
classes, including nested helpers, nine QA classes, two shared JGit-fixture
classes, 13 benchmark/JMH classes, every classpath jar, and the complete SDK
jar. The core closure SHA-256 for this isolated compilation is
`19f70776b164ce03863283e9f55262932a9d03373410fdb71b6e2adaddb17039`.

The tests cover seven local BigInteger proof/emission/compiled-execution and
negative controls, four scanner/environment/fingerprint cases, two actual
benchmark setup/compiler checks, and one opt-in test of both real pinned Bouncy
Castle trees. The opt-in test ran. Both upstream files had resolved calls and
no compilation errors, remained unchanged and supplied no verified candidate.
Missing field, curve-metadata and control-flow assumptions remain unsupported;
an unchanged candidate is not a proof of optimality.

The later targeted `pinned-reference/` check reran only the real-tree test against
the same isolated provisional production classes. It passed after strengthening
the diagnostic assertion and adding report export. Its separate one-test XML,
log, per-file reports, diagnostic counts and exact commits are retained there;
it is not presented as another 14-test run or a full corpus scan.

`fixtures/` contains fresh original and emitted source files from this run.
Their symbolic proof is independently checked, and finite compiled execution
uses boundary values, deterministic random pairs and literal small-modulus
oracles. These samples supplement the proof. The estimated operation work
changes from 2012 to 1030; that is not a measured speedup. The generated plan
retains base normalization through `modPow(ONE, modulus)`, so it is not the
upstream patch's literal one-modPow implementation.

The saved compiler receipt is produced by the actual benchmark setup, on a
runtime classpath containing only the QA/benchmark classes and JMH dependencies,
without an SDK or JDT runtime. It records each of the four source/class hashes
and checks the benchmark's 64 rotating inputs before any measurement.

No full corpus scan and no JMH measurements were run for this provisional
checkpoint. The old 40 raw primary samples remain lost. A final frozen build
requires fresh scoped checks, a fresh full corpus report and fresh JMH raw data.
