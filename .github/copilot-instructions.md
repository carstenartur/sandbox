# GitHub Copilot Coding Agent Instructions

Keep these instructions short. The agent has limited context budget.

## Environment Setup

```bash
export JAVA_HOME=/usr/lib/jvm/temurin-21-jdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
```

## Running Tests

```bash
# Most tests require xvfb
xvfb-run --auto-servernum mvn test -Dtest=TestClass -pl module_name_test

# Exception: sandbox_common_test runs without xvfb
mvn test -pl sandbox_common_test
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

## For More Details

See these reference files (read only if needed for your specific task):
- `.github/copilot-ref-architecture.md` — Module structure, patterns, conventions
- `.github/copilot-ref-build.md` — Build profiles, CI, coverage, troubleshooting
- `.github/copilot-ref-lessons.md` — Past session learnings, recurring bugs, fixes
