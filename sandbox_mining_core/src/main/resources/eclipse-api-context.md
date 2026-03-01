## Eclipse Platform API Context for Mining

This section provides context about commonly used Eclipse APIs that
appear in refactoring-relevant commits.

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

### Try-with-Resources

- `InputStream`, `OutputStream`, `Reader`, `Writer` should use try-with-resources
- `ICompilationUnit.getWorkingCopy()` must be paired with `discardWorkingCopy()`

### Encoding / Charset

- `new FileReader(file)` → `new FileReader(file, StandardCharsets.UTF_8)`
- `new InputStreamReader(is)` → `new InputStreamReader(is, StandardCharsets.UTF_8)`
- `String.getBytes()` → `String.getBytes(StandardCharsets.UTF_8)`
