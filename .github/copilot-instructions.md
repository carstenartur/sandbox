# GitHub Copilot Instructions for Sandbox Project

## Environment Setup

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

## Running Tests

```bash
# Most tests require xvfb
xvfb-run --auto-servernum mvn test -Dtest=TestClass -pl module_name_test

# Exception: these run without xvfb
mvn test -pl sandbox_common_test
mvn test -pl sandbox_mining_core
mvn test -pl sandbox-functional-converter-core
```

## Critical Rules

1. **Java 21 required** — Tycho 5.0.3 and Eclipse 2025-12 need Java 21
2. **Do NOT restructure packages** — `org.sandbox.*` maps to `org.eclipse.*` for JDT porting
3. **Do NOT de-duplicate CleanUpCore classes** — apparent duplication is intentional for JDT porting
4. **Do NOT rename MYCleanUpConstants** — the `MY` prefix avoids conflicts with Eclipse JDT
5. **Use shared cleanup base classes only for established lifecycle contracts** — ordinary cleanups extend `AbstractCleanUp`; coordinated multi-file cleanups may extend `AbstractPlannedMultiFileCleanUp`
6. **Remove unused imports** — Tycho treats them as errors
7. **Add `//$NON-NLS-1$`** to user-facing string literals

## Reviewable Maven/JUnit Change Policy

- Keep review PRs at or below about 1,500 changed text lines. Stop and split at 2,000 lines unless the PR contains a substantive `## Repository policy exception` section before further implementation.
- Large integration branches are draft/reference branches, not merge candidates.
- Maven plus JUnit are the executable test authority. GitHub Actions may provision the environment and invoke Maven, but must not implement a parallel test framework.
- Do not add Python automation, Python test runners, `actions/setup-python`, or new Python invocations in workflows. Existing Python is a shrinking legacy allowlist under `.github/repository-policy/`.
- Shell is limited to thin process and environment adapters. Semantic assertions belong in Java/JUnit.
- Check out upstream repositories in tests through the reusable JGit fixture and verify the exact repository, ref, and commit identity.
- A temporary quarantine must name exact tests, link an open issue, retain the test source, and must not use global failure-ignore switches.
- Do not add trigger-only commits. Rerun the existing workflow or job.
- After a PR is ready, change it only for a concrete review finding or an exact-head gate failure.

The policy is enforced by `sandbox_common_test`; see `.github/copilot-ref-testing.md` for the local command and exception format.

## Build Commands

```bash
mvn -T 1C verify                    # Fast dev build
mvn -Pproduct,repo -T 1C verify     # Full build
```

## Reference Files — Read Only When Relevant to Your Task

| File | When to read |
|------|-------------|
| `.github/copilot-ref-guardrails.md` | Before refactoring or restructuring code |
| `.github/copilot-ref-architecture.md` | To understand modules, packages, plugin patterns |
| `.github/copilot-ref-build.md` | For build profiles, CI, coverage, troubleshooting |
| `.github/copilot-ref-testing.md` | When writing or fixing tests, analyzing CI failures |
| `.github/copilot-ref-encoding.md` | When working on `sandbox_encoding_quickfix` |
| `.github/copilot-ref-junit.md` | When working on `sandbox_junit_cleanup` |
| `.github/copilot-ref-functional.md` | When working on `sandbox_functional_converter` |
| `.github/copilot-ref-plugins.md` | When working on `sandbox_platform_helper`, `sandbox_tools`, `sandbox_jface_cleanup`, or other plugins |
| `.github/copilot-ref-lessons.md` | When hitting known bugs or recurring issues |
