# Sandbox Quality Metrics

This build-only Maven module turns evidence from the authoritative reactor verification into the small JSON endpoints used by the README badges.

The generator deliberately reads machine-readable files rather than Maven console output:

- individual `<testcase>` elements from `target/surefire-reports/TEST-*.xml` and `target/failsafe-reports/TEST-*.xml`;
- the root `LINE` counter from `sandbox_coverage/target/site/jacoco-aggregate/jacoco.xml`.

It fails instead of publishing a zero or `unknown` value when either evidence source is absent. The output includes separate test, skipped-test and line-coverage endpoints plus `quality-summary.json` with the verified commit and raw counters.

The module is ordinary Java and JUnit code executed through Maven. GitHub Actions only provisions the environment, invokes the existing Maven build, runs the already compiled generator, and publishes its output after a successful `main` build.
