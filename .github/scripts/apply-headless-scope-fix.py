from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected one occurrence in {path}, found {count}: {old[:80]!r}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


wrapper = "sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java"
replace_once(
    wrapper,
    '''\t\tif (!root.exists()) {
\t\t\treturn List.of(value);
\t\t}
\t\tif (root.isFile()) {''',
    '''\t\tif (!root.exists()) {
\t\t\treturn List.of(value);
\t\t}
\t\tPath inputPath= root.toPath();
\t\tPath classificationRoot= root.isDirectory()
\t\t\t\t? inputPath
\t\t\t\t: inputPath.toAbsolutePath().normalize().getParent();
\t\tCleanupSourceSetClassifier classifier= CleanupSourceSetClassifier.create(
\t\t\t\tclassificationRoot == null ? inputPath : classificationRoot);
\t\tif (root.isFile()) {''')
replace_once(wrapper, "accepts(root.toPath(), scope)", "accepts(classifier, inputPath, scope)")
replace_once(wrapper, "Files.walk(root.toPath())", "Files.walk(inputPath)")
replace_once(wrapper, "accepts(path, scope)", "accepts(classifier, path, scope)")
replace_once(
    wrapper,
    '''\tprivate static boolean accepts(Path path, RequestedScope scope) {
\t\tboolean testPath= false;
\t\tfor (Path segment : path.toAbsolutePath().normalize()) {
\t\t\tString name= segment.toString();
\t\t\tif ("test".equals(name) || "tests".equals(name)) { //$NON-NLS-1$ //$NON-NLS-2$
\t\t\t\ttestPath= true;
\t\t\t\tbreak;
\t\t\t}
\t\t}
\t\treturn scope == RequestedScope.TEST ? testPath : !testPath;
\t}''',
    '''\tprivate static boolean accepts(CleanupSourceSetClassifier classifier, Path path, RequestedScope scope) {
\t\tboolean testSource= classifier.isTestSource(path);
\t\treturn scope == RequestedScope.TEST ? testSource : !testSource;
\t}''')


test = "sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapperTest.java"
replace_once(
    test,
    '''\t@Test
\tvoid bothScopeLeavesCallerArgumentsUntouched() throws Exception {''',
    '''\t@Test
\tvoid ancestorDirectoryNamedTestDoesNotReclassifyMainSources() throws Exception {
\t\tPath project= createProject(temporaryDirectory.resolve("test/workspace/example-project")); //$NON-NLS-1$

\t\tString[] filtered= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
\t\t\t\tnew String[] { "--scope", "main", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

\t\tassertContains(filtered, project.resolve("src/main/java/Example.java")); //$NON-NLS-1$
\t\tassertNotContains(filtered, project.resolve("src/test/java/ExampleTest.java")); //$NON-NLS-1$
\t}

\t@Test
\tvoid packageDirectoryNamedTestUnderMainRemainsMainSource() throws Exception {
\t\tPath project= createProject();
\t\tPath mainPackageSource= project.resolve("src/main/java/org/example/test/Helper.java"); //$NON-NLS-1$
\t\tFiles.createDirectories(mainPackageSource.getParent());
\t\tFiles.writeString(mainPackageSource, "package org.example.test; class Helper {}", //$NON-NLS-1$
\t\t\t\tStandardCharsets.UTF_8);

\t\tString[] main= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
\t\t\t\tnew String[] { "--scope", "main", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
\t\tString[] tests= ScopeFilteringCodeCleanupApplicationWrapper.filterArgumentsForScope(
\t\t\t\tnew String[] { "--scope", "test", "--config", "cleanup.properties", project.toString() }); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

\t\tassertContains(main, mainPackageSource);
\t\tassertNotContains(tests, mainPackageSource);
\t}

\t@Test
\tvoid bothScopeLeavesCallerArgumentsUntouched() throws Exception {''')
replace_once(
    test,
    '''\tprivate Path createProject() throws Exception {
\t\tPath project= temporaryDirectory.resolve("example-project"); //$NON-NLS-1$
\t\tFiles.createDirectories(project.resolve("src/main/java")); //$NON-NLS-1$
\t\tFiles.createDirectories(project.resolve("src/test/java")); //$NON-NLS-1$
\t\tFiles.writeString(project.resolve(".project"), "<projectDescription/>", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
\t\tFiles.writeString(project.resolve("src/main/java/Example.java"), //$NON-NLS-1$
\t\t\t\t"class Example {}", StandardCharsets.UTF_8); //$NON-NLS-1$
\t\tFiles.writeString(project.resolve("src/test/java/ExampleTest.java"), //$NON-NLS-1$
\t\t\t\t"class ExampleTest {}", StandardCharsets.UTF_8); //$NON-NLS-1$
\t\treturn project;
\t}''',
    '''\tprivate Path createProject() throws Exception {
\t\treturn createProject(temporaryDirectory.resolve("example-project")); //$NON-NLS-1$
\t}

\tprivate static Path createProject(Path project) throws Exception {
\t\tFiles.createDirectories(project.resolve("src/main/java")); //$NON-NLS-1$
\t\tFiles.createDirectories(project.resolve("src/test/java")); //$NON-NLS-1$
\t\tFiles.writeString(project.resolve(".project"), "<projectDescription/>", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
\t\tFiles.writeString(project.resolve("src/main/java/Example.java"), //$NON-NLS-1$
\t\t\t\t"class Example {}", StandardCharsets.UTF_8); //$NON-NLS-1$
\t\tFiles.writeString(project.resolve("src/test/java/ExampleTest.java"), //$NON-NLS-1$
\t\t\t\t"class ExampleTest {}", StandardCharsets.UTF_8); //$NON-NLS-1$
\t\treturn project;
\t}''')


readme = "sandbox_cleanup_application/README.md"
replace_once(
    readme,
    '| `--scope main\\|test\\|both` | Selects Java paths by exact directory segments named `test` or `tests`; default `both` |',
    '| `--scope main\\|test\\|both` | Selects conventional source sets (`src/test`, `src/tests`, or explicit `test`/`tests` roots); default `both` |')
replace_once(
    readme,
    '''For `main` and `test`, the public application wrapper expands directory roots into a deterministic sorted list of concrete Java files before cleanup begins. This makes a project root, `src`, `src/test`, and individual source files obey the same rule:

- `main`: no exact `test`/`tests` path segment;
- `test`: at least one exact `test`/`tests` path segment;
- `both`: caller inputs are left unchanged.''',
    '''For `main` and `test`, the public application wrapper expands directory roots into a deterministic sorted list of concrete Java files before cleanup begins. When an Eclipse `.project` ancestor is available, classification is relative to that project root; unrelated workspace ancestors and Java package names therefore cannot change the source set.

- `main`: `src/main` and Java paths without a conventional test-source marker;
- `test`: `src/test`, `src/tests`, top-level project `test`/`tests` directories, and explicit roots named `test`/`tests`;
- `both`: caller inputs are left unchanged.''')
replace_once(
    readme,
    '- The scope filter recognizes conventional directories named exactly `test` or `tests`; use explicit roots with `both` for custom source sets.',
    '- The scope filter recognizes conventional `src/test`, `src/tests`, and explicit `test`/`tests` roots; use explicit roots with `both` for custom source sets.')


usage = "sandbox_cleanup_application_help/html/usage.html"
replace_once(
    usage,
    '      <li><code>--scope main|test|both</code>: filters paths by exact directory segments named <code>test</code> or <code>tests</code>.</li>',
    '      <li><code>--scope main|test|both</code>: selects conventional <code>src/test</code>, <code>src/tests</code>, or explicit <code>test</code>/<code>tests</code> roots; project-relative classification ignores workspace ancestors and package names.</li>')


reference = "sandbox_cleanup_application_help/html/reference.html"
replace_once(
    reference,
    '''      <li><code>main</code> includes Java paths without an exact <code>test</code> or <code>tests</code> directory segment.</li>
      <li><code>test</code> includes Java paths with such a directory segment, including nested <code>src/test/java</code> trees.</li>''',
    '''      <li><code>main</code> includes <code>src/main</code> and paths without a conventional test-source marker.</li>
      <li><code>test</code> includes <code>src/test</code>, <code>src/tests</code>, top-level project <code>test</code>/<code>tests</code> directories, and explicit roots with those names.</li>''')
replace_once(
    reference,
    '''   <p>If a valid scoped directory contains no matching Java files, the run is a
      successful zero-file run rather than a command-line error.</p>''',
    '''   <p>When an Eclipse <code>.project</code> ancestor is available, classification
      is relative to that project root. A workspace ancestor or Java package named
      <code>test</code> therefore does not reclassify main sources. If a valid scoped
      directory contains no matching Java files, the run is a successful zero-file
      run rather than a command-line error.</p>''')
replace_once(
    reference,
    '      <li>The scope filter recognizes exact directory names <code>test</code> and <code>tests</code>; pass custom source-set roots explicitly with scope <code>both</code>.</li>',
    '      <li>The scope filter recognizes conventional <code>src/test</code>, <code>src/tests</code>, and explicit <code>test</code>/<code>tests</code> roots; pass custom source sets explicitly with scope <code>both</code>.</li>')
