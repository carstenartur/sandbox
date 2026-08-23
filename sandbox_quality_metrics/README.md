# Sandbox Quality Metrics

This build-support module converts the machine-readable evidence from the authoritative Maven verification into the JSON endpoints used by the README badges and the compact test-report index published on GitHub Pages.

The module is deliberately a plain Maven/Java module with `tycho.mode=maven`. It has no dependency on an Eclipse target platform or OSGi bundle, so testing the badge generator cannot trigger Tycho resolution of `sandbox_common`, `sandbox_common_core`, or any cleanup plug-in.

The generator reads:

- individual `<testcase>` elements from `target/surefire-reports/TEST-*.xml` and `target/failsafe-reports/TEST-*.xml`;
- the single root `INSTRUCTION` counter from `sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml`;
- every generated `target/site/surefire-report.html`, including reports from modules whose names do not end in `_test`.

It validates required JUnit summary attributes against the contained test cases, rejects conflicting outcomes and incomplete coverage counters, and fails closed when evidence is absent. The generated `quality-summary.json` binds the exact totals and raw coverage counters to the source commit.

Run its regression tests independently with:

```bash
mvn --file sandbox_quality_metrics/pom.xml clean verify
```

The coverage workflow packages the same Maven-tested generator after the complete reactor verification and publishes its output only for a successful `main` build.
