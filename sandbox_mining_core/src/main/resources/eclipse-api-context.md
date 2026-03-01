## Eclipse Platform API Context for Mining

This section provides context about commonly used Eclipse APIs that
appear in refactoring-relevant commits. Based on actual Eclipse Platform 2025 commits.

### Deprecated API Replacements (2025 patterns)

#### Platform.run → SafeRunner.run
Old (deprecated):
```java
import org.eclipse.core.runtime.Platform;
Platform.run(new SafeRunnable() { ... });
```
New:
```java
import org.eclipse.core.runtime.SafeRunner;
SafeRunner.run(new SafeRunnable() { ... });
```
**Source:** eclipse.platform.ui commit `b4b98c6e` (2025)

#### String.replaceAll with literal → String.replace
Old (unnecessary regex overhead):
```java
text.replaceAll("\\$", "\\$\\$");    // regex escaping needed
taggedText.replaceAll("&amp;", "&#038;");  // literal replacement via regex
```
New (direct string replacement):
```java
text.replace("$", "$$");    // no escaping needed
taggedText.replace("&amp;", "&#038;");  // faster, no regex
```
**Source:** eclipse.platform.ui commit `7bb8891b` (2025)

#### IPageLayout.addFastView (removed)
The `addFastView` API was removed entirely in 2025.
```java
// Removed — no replacement needed, fast views concept is gone
layout.addFastView("viewId");
```
**Source:** eclipse.platform.ui commit `40552f3c` (2025)

### Status API (Eclipse 4.7+)

Old pattern:
```java
new Status(IStatus.ERROR, pluginId, message);
new Status(IStatus.WARNING, pluginId, message);
```

New factory methods (Eclipse 4.28 / Java 11+):
```java
Status.error(message);
Status.warning(message);
Status.info(message);
```

### JUnit 4 → JUnit 5 Migration Patterns

These patterns were seen across multiple 2025 commits in eclipse.platform.ui:

```java
// Annotation replacements
@Before → @BeforeEach
@After → @AfterEach
@BeforeClass → @BeforeAll
@AfterClass → @AfterAll
@Ignore → @Disabled
@Rule → ExtendWith/RegisterExtension

// Assertion class change
import org.junit.Assert → import org.junit.jupiter.api.Assertions
Assert.assertEquals(expected, actual) → assertEquals(expected, actual)
Assert.assertTrue(condition) → assertTrue(condition)

// Runner to Extension
@RunWith(JUnit4.class) → removed
TestRule → Extension model
```
**Source:** eclipse.platform.ui commits `0e238b7c`, `37ad17df`, `447e516e` (2025)

### Resource API

- `IResource` is the base for `IFile`, `IFolder`, `IProject`, `IWorkspaceRoot`
- `IResource.accept(IResourceVisitor)` walks resource trees
- `IResource.getAdapter(Class)` provides type adaptation

### JDT DOM API

- `ASTNode` subtypes: `Expression`, `Statement`, `Type`, `BodyDeclaration`
- `ASTParser.newParser(AST.JLS_Latest)` creates parsers
- `ASTRewrite` records changes to AST, applied via `TextEdit`
- `ImportRewrite` manages import additions/removals

### SWT/JFace Patterns

- `Display.syncExec()` / `Display.asyncExec()` for UI thread access
- `Job.create(name, monitor -> { ... })` for background work
- `IStructuredSelection` for tree/table viewer selections

### Collections Modernization

- `Collections.unmodifiableList(list)` → `List.copyOf(list)` (Java 10+)
- `Collections.emptyList()` → `List.of()` (Java 9+)
- `new ArrayList<>(Arrays.asList(...))` → `new ArrayList<>(List.of(...))`

### Clean-up Patterns (eclipse.jdt.ui 2025)

The following clean-ups were actively being developed in 2025:
- **Module imports**: `import module java.base;` instead of individual imports (Java 25+)
- **Switch expressions**: Converting switch statements to switch expressions where possible
- **Diamond operator**: Not offering type arguments where `<>` would suffice
- **System properties**: Replacing `System.getProperty("java.specification.version")` with constants
- **Unnecessary arrays**: Removing redundant `new Type[]` in varargs calls
- **Add final for fields**: Adding `final` modifier to effectively-final fields

**Source:** eclipse.jdt.ui commits `66872a95`, `46f45bcb`, `90ff95a0`, `ce67be20` (2025)

### Encoding / Charset

- `new FileReader(file)` → `new FileReader(file, StandardCharsets.UTF_8)`
- `new InputStreamReader(is)` → `new InputStreamReader(is, StandardCharsets.UTF_8)`
- `String.getBytes()` → `String.getBytes(StandardCharsets.UTF_8)`

### NLS Marker Handling

When removing array creation expressions or other refactorings that affect lines with
`//$NON-NLS-n$` markers, the markers must be preserved or renumbered. This was a bug
fix pattern seen in 2025.

**Source:** eclipse.jdt.ui commit `3702d32e` (2025)
