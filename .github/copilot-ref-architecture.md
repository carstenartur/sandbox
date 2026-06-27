# Architecture & Module Reference

> **Read this when**: You need to understand the codebase structure, navigate modules, or create new plugins.

## Module Organization

Every plugin has three paired modules:

1. **Implementation** (`sandbox_*`) — The actual code
2. **Feature** (`sandbox_*_feature`) — Eclipse feature packaging
3. **Test** (`sandbox_*_test`) — JUnit 5 tests

## Module Types

| Type | Naming | Packaging | Needs Xvfb? |
|------|--------|-----------|-------------|
| Eclipse Plugin | `sandbox_*` | Tycho/eclipse-plugin | Yes |
| Standard Java | `sandbox-*-core` | Maven JAR | No |
| Feature | `sandbox_*_feature` | Eclipse Feature | N/A |
| Test | `sandbox_*_test` | Eclipse Plugin Test | Yes (except `sandbox_common_test`) |
| Infrastructure | `sandbox_target`, `sandbox_coverage`, `sandbox_product`, `sandbox_updatesite` | Various | N/A |

**Key**: Modules with `-` (hyphen) in the name are standard Maven modules — no Eclipse needed.

## Key Modules

| Module | Purpose |
|--------|---------|
| `sandbox_encoding_quickfix` | Replace platform-dependent encoding with `StandardCharsets.UTF_8` |
| `sandbox_platform_helper` | Simplify `new Status(...)` → `Status.error()` factory methods |
| `sandbox_functional_converter` | Convert imperative loops to Java 8 Streams |
| `sandbox-functional-converter-core` | AST-independent loop transformation core (plain Maven JAR) |
| `sandbox_junit_cleanup` | Migrate JUnit 3/4 → JUnit 5 |
| `sandbox_tools` | While-to-For converter (already merged into Eclipse JDT) |
| `sandbox_usage_view` | Table view for inconsistent naming detection |
| `sandbox_extra_search` | Search tool for Eclipse/Java upgrade critical classes |
| `sandbox_common` | Shared code across plugins (MYCleanUpConstants, etc.) |
| `sandbox_common_core` | Bundled hint files, DSL engine, non-Eclipse utilities |
| `sandbox_mining_core` | Architecture mining CLI and analysis (plain Maven JAR) |

## Package Structure

```
org.sandbox.jdt.internal.corext.*  → Core transformation/fix logic
org.sandbox.jdt.internal.ui.*      → UI components and preferences
org.eclipse.jdt.ui.tests.quickfix.* → Test cases (in test modules)
```

To port to Eclipse JDT: replace `sandbox` → `eclipse` in package paths.

## Eclipse JDT Cleanup Pattern

When creating a new cleanup:

1. Create the cleanup class in `org.sandbox.jdt.internal.corext` package
2. Define constants in `MYCleanUpConstants` (`sandbox_common/src/.../fix2/`)
3. Register in `plugin.xml` under `org.eclipse.jdt.ui.cleanUps` extension point
4. Create test cases in corresponding `*_test` module
5. Document in the plugin's README.md

## Plugin Documentation Requirements

Each plugin directory **MUST** contain:
- `ARCHITECTURE.md` — Design, structure, key components
- `TODO.md` — Open tasks, known issues, planned improvements

Each feature module **MUST** contain:
- `feature.properties` — English descriptions
- `feature_de.properties` — German translations

## Code Style

- Java 21 features where appropriate
- Eclipse Public License 2.0 headers on all files
- `@Override` annotations always
- JUnit 5 for all tests
- Remove unused imports (Tycho treats as errors)
- Add `//$NON-NLS-1$` to user-facing string literals
