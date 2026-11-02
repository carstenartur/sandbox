# Independent Bouncy Castle retention audit

This temporary Maven/JUnit project reconstructs the lost retention auditor. It is
qualification tooling for the reviewed Sandbox source, not a production feature
or another optimizer. Its own unit tests do not establish current corpus retention.

`RetentionAuditTest` exercises dropped-region detection, exact original-source
identity, replacement/cost/proof reporting, byte drift, profile changes, loaded
runtime origins and Git source identity. `BaselineIntegrityTest` checks the real
published baseline format and all 146 strict / 149 mathematical regions against
the pinned source archive. The baseline ZIP digest is fixed to the previously
verified PR1785 CI artifact.

`CurrentRetentionTest` is opt-in. It verifies the actual supplied Git checkout
and the scan directories **inside that checkout**. It checks every archive source,
consulted definition, generated source, runtime fingerprint and replacement span;
the ordinary Apply output is reconstructed from the evidence and its independent
documentation edits. It compares exact `(path, offset, length)` identities and
reports every replacement, cost, proof and other evidence change. Any lost region
fails. Any changed cost is written into the report and fails pending explicit
review. Candidate counts do not establish runtime speedup or constant-time behavior.

The only normalized profile differences are numeric-kind ordering and the new
safe defaults `tailRecursion=false` / `valueRuntime=UNSPECIFIED`. Existing budgets
and numerical-contract options must match the published baseline.

## Run

Use the existing Java25.0.5+8 and Maven toolchain, settings and local repository.
Respect the shared task's exclusive JVM/CPU handoff before invoking Maven.

For integrity tests, pass these properties to `mvn test`:

```
-Dretention.baselineRoot=/workspace/scratch/0eb32b15fbb4/recovery/baseline-1785
-Dretention.baselineZip=/workspace/scratch/0eb32b15fbb4/recovery/baseline-1785-bc.zip
-Dretention.sources=/tmp/jdt-pr5614-m2/org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2-sources.jar
```

For actual retention, add all of:

```
-Dretention.currentRepo=/tmp/sandbox-recovered-integration
-Dretention.expectedHead=<exact freshly qualified commit>
-Dretention.expectedTree=<exact freshly qualified tree>
-Dretention.binary=/tmp/jdt-pr5614-m2/org/bouncycastle/bcprov-jdk18on/1.85.2/bcprov-jdk18on-1.85.2.jar
-Dretention.output=<new output directory>
```

Each profile writes `summary.json`, complete `retention.json`, and byte-exact
copies of both review reports. Per-profile output directories must not already
exist. The current audit pins SDK70's already reviewed JAR digest; another SDK
requires an explicit source-bound qualification change.

When `retention.currentRepo` is absent, the current audit is intentionally skipped.
This is a visible absence of current qualification, never a successful retention
result. The real full-corpus source scan and native lifecycle tests remain separate
authorities and are run by the main task.
