# Plugin-Specific Reference (All Remaining Plugins)

> **Read this when**: Working on `sandbox_platform_helper`, `sandbox_tools`, `sandbox_jface_cleanup`, `sandbox_xml_cleanup`, `sandbox_extra_search`, `sandbox_usage_view`, `sandbox_method_reuse`, `sandbox_use_general_type`, `sandbox_int_to_enum`, `sandbox_css_cleanup`, or `sandbox_cleanup_application`.

---

## Platform Status Helper (`sandbox_platform_helper`)

Simplifies `new Status(...)` → factory methods (Java 11+).

### Transformations

| Old Constructor | New Factory Method |
|----------------|-------------------|
| `new Status(IStatus.ERROR, ...)` | `Status.error(msg, ex)` |
| `new Status(IStatus.WARNING, ...)` | `Status.warning(msg, ex)` |
| `new Status(IStatus.INFO, ...)` | `Status.info(msg, ex)` |
| `new Status(IStatus.OK, ...)` | `Status.ok(msg, ex)` |
| `new Status(IStatus.CANCEL, ...)` | ❌ Not transformed (no factory) |
| `new MultiStatus(id, code, ...)` | Normalizes code to `IStatus.OK` (no factory methods for MultiStatus) |

### Key Rules
- Factory methods only accept `(message, exception)` — **plugin ID is always dropped**
- Java 11+ required — checks `JavaModelUtil.is11OrHigher()`
- Cleanup constant: `PLATFORM_STATUS_CLEANUP` in `MYCleanUpConstants`
- DSL hint file `platform-status.sandbox-hint` covers 3/4/5-arg constructors

### Core Classes
- `SimplifyPlatformStatusFixCore` — enum of transformation types
- `AbstractSimplifyPlatformStatus` — base helper using `HelperVisitorFactory.forClassInstanceCreation(Status.class)`
- `MultiStatusSimplifyPlatformStatus` — MultiStatus code normalization

### Tests
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_platform_helper_test
```

---

## While-to-For Converter (`sandbox_tools`)

> ✅ **MERGED INTO ECLIPSE JDT** — not actively developed. Exists as reference implementation.

Converts while loops to for loops.

### Conversion Criteria
1. Loop variable initialized immediately before while
2. Loop variable incremented at end of loop body (`i++`, `++i`, `i += 1`, `i -= 1`)
3. Loop condition uses the loop variable
4. Loop variable not modified elsewhere in body
5. Loop variable not used after the while statement

### NOT Supported (intentionally conservative)
- Complex expressions: `i += foo()`
- Multiple increments: `i++; j++`
- Conditional increments: `if (...) i++`

### Core Class
`WhileToForConverter` — `canConvert()`, `convert()`, `extractInitializer()`, `extractIncrement()`

Active code now lives in Eclipse JDT: `org.eclipse.jdt.internal.corext.fix.*`

### Tests
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_tools_test
```

---

## JFace SubMonitor Migration (`sandbox_jface_cleanup`)

Migrates deprecated `SubProgressMonitor` to modern `SubMonitor` API.

### Transformation Pattern
```java
// Before:
monitor.beginTask("Task", 100);
IProgressMonitor sub = new SubProgressMonitor(monitor, 60);

// After:
SubMonitor subMonitor = SubMonitor.convert(monitor, "Task", 100);
IProgressMonitor sub = subMonitor.split(60);
```

### Core Class: `JFacePlugin` (extends `AbstractTool`)

Three passes in `find()`:
1. **beginTask + SubProgressMonitor** — chained `AstProcessorBuilder` visitors
2. **Standalone SubProgressMonitor** — no associated beginTask
3. **Type references** — fields, parameters, return types, casts → replace with `IProgressMonitor`

### Flag Mapping
| SubProgressMonitor Flag | SubMonitor Equivalent |
|------------------------|----------------------|
| `SUPPRESS_SUBTASK_LABEL` | `SubMonitor.SUPPRESS_SUBTASK` |
| `PREPEND_MAIN_LABEL_TO_SUBTASK` | Dropped (no equivalent) |

### Key Details
- Generates unique variable names (`subMonitor`, `subMonitor2`, ...) via `generateUniqueVariableName()`
- Removes `SubProgressMonitor` import, adds `SubMonitor` import
- Preserves `SubProgressMonitor` import if unmapped flags still reference it
- Idempotent — safe to run multiple times
- Each `AstProcessorBuilder` pass runs independently (separate builder instances — see lesson #28)

### Tests
```bash
xvfb-run --auto-servernum mvn test -pl sandbox_jface_cleanup_test
```

---

## Functional Loop Converter (`sandbox_functional_converter`)

See dedicated file: `.github/copilot-ref-functional.md`

---

## Other Plugins (Brief Reference)

### PDE XML Cleanup (`sandbox_xml_cleanup`)
- Optimizes Eclipse PDE XML files (plugin.xml, feature.xml)
- Reduces whitespace, converts spaces to tabs
- XSLT-based transformation
- Only processes PDE-relevant files in project root, OSGI-INF, META-INF

### Extra Search (`sandbox_extra_search`)
- Search view for deprecated/critical API usage
- Pre-populated list of commonly deprecated classes
- Integrates with Eclipse JDT SearchEngine

### Usage View (`sandbox_usage_view`)
- Detects naming conflicts (same name, different types)
- AST-based analysis with `AstProcessorBuilder`
- SWTBot UI tests: `xvfb-run --auto-servernum mvn verify -Pswtbot -pl sandbox_usage_view_test`

### Method Reuse (`sandbox_method_reuse`)
- Identifies opportunities to reuse existing methods
- Token-based and AST-based duplicate detection
- Under development

### Use General Type (`sandbox_use_general_type`)
- Suggests replacing specific types with general supertypes (`ArrayList` → `List`)
- Uses TriggerPattern DSL for rule definitions

### Int to Enum (`sandbox_int_to_enum`)
- Experimental: converts `static final int` constants to enum types

### CSS Cleanup (`sandbox_css_cleanup`)
- CSS validation/formatting using Prettier and Stylelint
- Requires npm tools (graceful fallback when not installed)

### Cleanup CLI (`sandbox_cleanup_application`)
- Equinox CLI for running cleanups from command line
- Requires `-data` parameter for Eclipse workspace
- Supports recursive directory processing and configurable cleanup profiles

---

## Common Patterns Across All Plugins

1. **Every cleanup** extends `AbstractCleanUp` directly — NO shared base classes
2. **Every helper** follows `find()` / `rewrite()` / `getPreview()` pattern
3. **Cleanup constants** registered in `MYCleanUpConstants` (the `MY` prefix is intentional)
4. **Package structure** mirrors Eclipse JDT: `org.sandbox.*` → `org.eclipse.*` for porting
5. **Test modules** end with `_test` suffix and require xvfb (except `sandbox_common_test`, `sandbox_mining_core`, `sandbox-functional-converter-core`)