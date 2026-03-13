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

1. **Java 21 required** — Tycho 5.0.1 and Eclipse 2025-12 need Java 21
2. **Do NOT restructure packages** — `org.sandbox.*` maps to `org.eclipse.*` for JDT porting
3. **Do NOT de-duplicate CleanUpCore classes** — apparent duplication is intentional for JDT porting
4. **Do NOT rename MYCleanUpConstants** — the `MY` prefix avoids conflicts with Eclipse JDT
5. **Do NOT create shared base classes** — each cleanup directly extends `AbstractCleanUp`
6. **Remove unused imports** — Tycho treats them as errors
7. **Add `//$NON-NLS-1$`** to user-facing string literals

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
| `.github/copilot-ref-lessons.md` | When hitting known bugs or recurring issues |