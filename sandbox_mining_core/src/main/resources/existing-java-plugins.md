# Existing Java-Based Cleanup Plugins

These Eclipse JDT cleanup plugins already exist as imperative Java implementations in the sandbox
project. When analyzing commits, if a proposed DSL rule overlaps with one of these plugins, set
`"existsAsJavaPlugin": true` and `"replacesPlugin": "plugin-name"` in the evaluation.

This is **not a blocklist** — the AI should still propose DSL rules for these transformations.
The goal is to identify which Java plugins could potentially be **reimplemented as simpler DSL rules**.

| Plugin | Description | Transformation Examples |
|--------|-------------|------------------------|
| `sandbox_encoding_quickfix` | Replace platform-dependent encoding with explicit `StandardCharsets` | `new FileReader(f)` → `new FileReader(f, StandardCharsets.UTF_8)`, `String.getBytes("UTF-8")` → `String.getBytes(StandardCharsets.UTF_8)` |
| `sandbox_junit_cleanup` | Migrate JUnit 3/4 tests to JUnit 5 | `extends TestCase` → annotations, `@Before` → `@BeforeEach`, `@Test(expected=...)` → `assertThrows()` |
| `sandbox_platform_helper` | Simplify `new Status(...)` calls to factory methods | `new Status(IStatus.ERROR, id, msg)` → `Status.error(msg)` (Java 11+) |
| `sandbox_functional_converter` | Convert imperative loops to Java 8 Streams | Enhanced for-loops → `forEach()`, mapping → `stream().map()` |
| `sandbox_tools` | While-to-For loop converter | `while(iter.hasNext())` → `for(T item : collection)` |
| `sandbox_jface_cleanup` | JFace UI toolkit code modernization | Deprecated JFace API replacements |
| `sandbox_xml_cleanup` | XML refactoring and optimization for PDE files | XSLT, whitespace normalization |
| `sandbox_method_reuse` | Identifies potentially reusable code patterns | Detects duplicated method logic |
| `sandbox_int_to_enum` | Integer constant chains → type-safe enums | `int STATUS_OK = 0` → `enum Status { OK }` |
| `sandbox_use_general_type` | Widen variable types to general supertypes/interfaces | `ArrayList<X> list = ...` → `List<X> list = ...` |
| `sandbox_triggerpattern` | Pattern-based string simplifications, threading fixes | String concatenation → `StringBuilder`, threading safety fixes |
| `sandbox_css_cleanup` | CSS validation and formatting | Prettier/Stylelint integration |
