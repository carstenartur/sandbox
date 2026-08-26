# GitHub Copilot Instructions for Sandbox Project

## Environment setup

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

The executable baseline is Java 21, Tycho 5.0.4, and Eclipse 2026-06 / Platform 4.40.

## Running tests

```bash
# Most Eclipse plug-in tests require Xvfb on Linux
xvfb-run --auto-servernum mvn test -Dtest=TestClass -pl module_name_test

# Plain Maven tests that do not require a workbench
mvn test -pl sandbox_common_test
mvn test -pl sandbox_mining_core
mvn test -pl sandbox-functional-converter-core
```

## Critical rules

1. **Java 21 is required** — the current Tycho and Eclipse baseline is built and tested with Java 21.
2. **Do not restructure packages casually** — `org.sandbox.*` often maps deliberately to `org.eclipse.*` for JDT porting.
3. **Do not de-duplicate CleanUpCore classes merely because they look similar** — some separation mirrors JDT execution layers.
4. **Do not rename `MYCleanUpConstants`** — the prefix avoids conflicts with Eclipse JDT classes.
5. **Use shared cleanup base classes only for established lifecycle contracts** — ordinary cleanups extend `AbstractCleanUp`; coordinated multi-file cleanups may extend `AbstractPlannedMultiFileCleanUp`.
6. **Remove unused imports** — Eclipse/Tycho builds may treat them as errors.
7. **Add `//$NON-NLS-1$` markers where required** in Eclipse plug-in Java sources.
8. **Keep save-action support explicit** — project-wide or structural transformations must not become save actions implicitly.

## Reviewable Maven/JUnit change policy

- Keep review PRs at or below about 1,500 changed text lines. Stop and split at 2,000 lines unless the PR contains a substantive `## Repository policy exception` section before further implementation.
- Large integration branches are draft/reference branches, not merge candidates.
- Maven plus JUnit are the executable test authority. GitHub Actions may provision the environment and invoke Maven, but must not implement a parallel test framework.
- Do not add Python automation, Python test runners, `actions/setup-python`, or new Python invocations in workflows. Existing Python is a shrinking legacy allowlist under `.github/repository-policy/`.
- Shell is limited to thin process and environment adapters. Semantic assertions belong in Java/JUnit.
- Check out upstream repositories in tests through the reusable JGit fixture and verify the exact repository, ref, and commit identity.
- A temporary quarantine must name exact tests, link an open issue, retain the test source, and must not use global failure-ignore switches.
- Do not add trigger-only commits. Rerun the existing workflow or job.
- After a PR is ready, change it only for a concrete review finding or an exact-head gate failure.
- Never accept unrelated screenshot differences merely to make a visual gate pass.

The policy is enforced by `sandbox_common_test`; see `.github/copilot-ref-testing.md` for the local command and exception format.

## Build commands

```bash
mvn -T 1C clean verify                # Development reactor
mvn -Pproduct,repo -T 1C clean verify # Product and update site
mvn -Pdistribution clean verify       # Sequential release/distribution gate
```

## Reference files — read only when relevant

| File | When to read |
|---|---|
| `.github/copilot-ref-guardrails.md` | Before refactoring or restructuring code |
| `.github/copilot-ref-architecture.md` | To understand modules, packages, and plug-in patterns |
| `.github/copilot-ref-build.md` | For build profiles, CI, coverage, and troubleshooting |
| `.github/copilot-ref-testing.md` | When writing or fixing tests or analyzing CI failures |
| `.github/copilot-ref-encoding.md` | When working on `sandbox_encoding_quickfix` |
| `.github/copilot-ref-junit.md` | When working on `sandbox_junit_cleanup` |
| `.github/copilot-ref-functional.md` | When working on `sandbox_functional_converter` |
| `.github/copilot-ref-plugins.md` | When working on the remaining cleanup plug-ins |
| `.github/copilot-ref-lessons.md` | When hitting known bugs or recurring issues |
