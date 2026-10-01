# Compiler reporting investigation

This standalone Maven probe investigates JDT Core PR #5434 and Apache Surefire
#3439. It is deliberately separate from the existing APT cleanup scenario and
from the normal Sandbox product build.

The initial commit is the RED regression test: it expects one bounded report
write but runs the unmodified Surefire 3.5.6 adapter and reporter. A failure with
`REPEATED_REPORT_COMPLETION` is expected until the proposed correction is tested.
A compilation or dependency-resolution failure is NOT reproduction evidence.

Run with Java 21 and Maven:

```sh
mvn -B -ntp -f qa/upstream-jdt/compiler-reporting/pom.xml clean verify
```

The test launches real Jupiter parameterized classes directly and through nested
Platform suites, then forwards all notifications to Surefire's real reporter.
A transparent listener measures completed report writes and the complete XML
size after each write. Final XML inventories and measurements are kept under
`target/reporting-probe`; the adapter's code-source location is also recorded.
No test output/reporting is disabled, and no compiler or subprocess startup
workload is involved. Elapsed time includes instrumentation and is not a JDT
benchmark.

Pinned investigation inputs:

- JDT Core baseline: `28a176085b773202bf2a0b2b22dbec741d473df2`
- Stephan's final migration: `bb86061a7a8d0c3ee4d02f358cf30be6e3e6ae4e`
- Tycho: 5.0.4, whose POM selects Surefire 3.5.6
- Surefire: 3.5.6
- Jupiter: 5.14.3 (the version in the independent Surefire issue)

Sources:
- https://github.com/eclipse-jdt/eclipse.jdt.core/pull/5434
- https://github.com/apache/maven-surefire/issues/3439

This probe does not certify the full JDT suite, Tycho integration, resource
lifecycle, display names, test-view behaviour, or migration completeness.
Those remain separate acceptance gates on the continuation branch.
