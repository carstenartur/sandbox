# Compiler reporting investigation

Standalone, pinned investigation of JDT Core #5434 and Surefire #3439. This is
not the APT cleanup scenario, is not a dependency of the Sandbox product, and
does not change JDT's tests or install an unreviewed runner into normal builds.

## Run both sides

Use Java 21 and Maven, and separate evidence directories outside `target`:

```sh
mvn -B -ntp -f qa/upstream-jdt/compiler-reporting/pom.xml clean verify \
  -Dprobe.expectBaseline=true -Dprobe.output=/tmp/jdt-reports-baseline
mvn -B -ntp -f qa/upstream-jdt/compiler-reporting/pom.xml clean verify \
  -Ppatched-reporter -Dprobe.output=/tmp/jdt-reports-patched
```

The baseline assertions require the known amplification to occur; a resolution,
compilation, or XML-parse error is NOT reproduction evidence. The corrected
assertions require one report completion for the successful parameterized
workloads and correct XML suite totals. A separate case checks disabled classes
do not prematurely flush the containing suite. Use fresh output directories.

## Real execution, not a model

The fixtures execute actual Jupiter 5.14.3 parameterized classes via the Platform
launcher. Its actual Surefire 3.5.6 `RunListenerAdapter` forwards to the actual
`DefaultReporterFactory` and `StatelessXmlReporter`. A transparent listener
counts completion notifications and the whole resulting XML size after each
full rewrite. Complete case identities, multiplicities, and outcome states are
compared before and after; XML remains enabled.

The seven probe tests cover direct/nested suites, 32/64 invocations, successful,
failed, erroneous, aborted and disabled test methods, disabled classes and
nested before-all/after-all failures. The synthetic fixture failures are
expected evidence and must remain in both XML inventories.

`PrepareAdapter.java` verifies the exact upstream Git blob IDs before changing
anything, preserves Apache license headers, and writes source hashes. There
are TWO separately motivated changes:

1. only the reporting owner emits the start/completion pair; nested class,
   parameterized invocation, failure, and skipped-class notifications must not
   rewrite or reset their running owner's entire report;
2. with no retries configured, XML summary counts count every serialized row.
   Unmodified 3.5.6 can report 3 tests for 96 rows, and mark a container error as
   a flake merely because a different method succeeded. The existing retry
   aggregation branch is left unchanged.

Class code-source locations prove whether stock or patched implementations are
loaded. Generated Apache sources are test-only; normal product dependencies
and upstream repositories are unchanged.

## Evidence / limits

Measurements and inventory text files are written for each scenario, with
original XML and probe test reports. CI also exports exact jars, original and
patched source, source digests, Java version and the tested Git commit.
Elapsed times include instrumentation and are NOT a compiler benchmark.

The first successful A/B experiment is workflow run 36915113908 at Sandbox
`838316d173e083c3cb4244334d7e9c547188f1fe`: direct 32 invocations produced
33 rewrites, nested suites 35; 64 nested invocations produced 67. The initial
adapter-only correction reduced these to one, with identical case inventories.
The later outcome/header tests deliberately go beyond that initial result.

This remains a draft investigation, not an upstream-ready Surefire release.
Retry semantics, parallel execution, all Surefire provider cases, interruption
checkpoints, report display naming, and console/global summary aggregation need
separate coverage. A single final write changes when partial XML becomes
available; production rollout must choose a bounded checkpoint strategy or
per-invocation reports without reintroducing repeated full-history writes.
The complete Tycho/JDT before/after run has not yet been certified. Resources,
Eclipse test-view behaviour, and migration completeness are separate gates.

Pins:
- Core baseline: `28a176085b773202bf2a0b2b22dbec741d473df2`
- Stephan's final migration: `bb86061a7a8d0c3ee4d02f358cf30be6e3e6ae4e`
- Tycho 5.0.4; Surefire 3.5.6; Jupiter 5.14.3

Sources:
- https://github.com/eclipse-jdt/eclipse.jdt.core/pull/5434
- https://github.com/apache/maven-surefire/issues/3439
