/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix.Java10;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.testplugin.TestOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.sandbox.jdt.internal.corext.fix.helper.ChangeBehavior;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava10;

/** Requires a real Charset overload, not just the text StandardCharsets in the output. */
public class CharsetModernizationTest {
    @RegisterExtension
    EclipseJava10 context= new EclipseJava10();

    @BeforeEach
    void setUp() throws Exception {
        var compilerOptions= TestOptions.getDefaultOptions();
        compilerOptions.put(JavaCore.COMPILER_PB_UNUSED_IMPORT, JavaCore.WARNING);
        compilerOptions.put(JavaCore.COMPILER_PB_UNCHECKED_TYPE_OPERATION, JavaCore.WARNING);
        JavaCore.setOptions(compilerOptions);
        TestOptions.initializeCodeGenerationOptions();
        JavaPlugin.getDefault().getCodeTemplateStore().load();
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void scannerNarrowThrows(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {
                    return new java.util.Scanner(file, "UTF-8");
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void formatterRemovesObsoleteThrows(ChangeBehavior mode) throws Exception {
        String after= verify(mode, """
                java.util.Formatter open(java.io.File file)
                        throws java.io.FileNotFoundException, java.io.UnsupportedEncodingException {
                    return new java.util.Formatter(file, "UTF-8");
                }
                """, 1);
        assertFalse(after.contains("UnsupportedEncodingException"), after); //$NON-NLS-1$
        assertTrue(after.contains("Category.FORMAT"), after); //$NON-NLS-1$
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void formatterRemovesObsoleteCatch(ChangeBehavior mode) throws Exception {
        String after= verify(mode, """
                java.util.Formatter open(java.io.File file) throws java.io.FileNotFoundException {
                    try {
                        return new java.util.Formatter(file, "UTF-8", java.util.Locale.ROOT);
                    } catch (java.io.UnsupportedEncodingException ex) {
                        throw new AssertionError(ex);
                    }
                }
                """, 1);
        assertFalse(after.contains("UnsupportedEncodingException"), after); //$NON-NLS-1$
        assertFalse(after.contains("try {"), after); //$NON-NLS-1$
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void initializerAndCatchAreModernized(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Scanner scanner;
                {
                    try {
                        scanner= new java.util.Scanner(new java.io.File("input"));
                    } catch (java.io.FileNotFoundException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void localCallerChain(ChangeBehavior mode) throws Exception {
        verify(mode, """
                private java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {
                    return new java.util.Scanner(file, "UTF-8");
                }
                private java.util.Scanner middle(java.io.File file) throws java.io.FileNotFoundException {
                    return open(file);
                }
                java.util.Scanner caller(java.io.File file) {
                    try { return middle(file); }
                    catch (java.io.FileNotFoundException ex) { throw new AssertionError(ex); }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void sourceOwnedLambdaContract(ChangeBehavior mode) throws Exception {
        verify(mode, """
                interface Open { java.util.Scanner get() throws java.io.FileNotFoundException; }
                Open later(java.io.File file) { return () -> new java.util.Scanner(file, "UTF-8"); }
                java.util.Scanner run(Open open) {
                    try { return open.get(); }
                    catch (java.io.FileNotFoundException ex) { throw new AssertionError(ex); }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void fileFormatterWithImplicitCharset(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Formatter open(String file) throws java.io.FileNotFoundException {
                    return new java.util.Formatter(file);
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void independentSurvivingExceptionKeepsItsCatch(ChangeBehavior mode) throws Exception {
        String after= verify(mode, """
                java.util.Formatter open(java.io.File file, byte[] bytes, String encoding)
                        throws java.io.IOException {
                    try {
                        String other= new String(bytes, encoding);
                        System.out.println(other);
                        return new java.util.Formatter(file, "UTF-8", java.util.Locale.ROOT);
                    } catch (java.io.UnsupportedEncodingException ex) { throw new AssertionError(ex); }
                }
                """, 1);
        assertTrue(after.contains("catch (java.io.UnsupportedEncodingException ex)"), after); //$NON-NLS-1$
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void rethrowThroughParentheses(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {
                    try { return new java.util.Scanner(file, "UTF-8"); }
                    catch (java.io.FileNotFoundException ex) { throw (ex); }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void multiCatchStillNeededByAnotherOperation(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Scanner open(java.io.File file, byte[] bytes, String encoding) {
                    try {
                        System.out.println(new String(bytes, encoding));
                        return new java.util.Scanner(file, "UTF-8");
                    } catch (java.io.FileNotFoundException | java.io.UnsupportedEncodingException ex) {
                        throw new AssertionError(ex);
                    }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void narrowHandlerOverloadIsNotChanged(ChangeBehavior mode) throws Exception {
        String after= verify(mode, """
                void report(java.io.FileNotFoundException ex) { System.out.println("missing"); }
                void report(java.io.IOException ex) { throw new AssertionError("wrong overload"); }
                java.util.Scanner open(java.io.File file) {
                    try { return new java.util.Scanner(file, "UTF-8"); }
                    catch (java.io.FileNotFoundException ex) { report(ex); return null; }
                }
                """, 1);
        assertTrue(after.contains("catch (java.io.FileNotFoundException ex)"), after);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void sourceOwnedMethodReferenceContract(ChangeBehavior mode) throws Exception {
        verify(mode, """
                interface Open { java.util.Scanner get(java.io.File file) throws java.io.FileNotFoundException; }
                private java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {
                    return new java.util.Scanner(file, "UTF-8");
                }
                Open later() { return this::open; }
                java.util.Scanner run(java.io.File file) {
                    try { return later().get(file); }
                    catch (java.io.FileNotFoundException ex) { throw new AssertionError(ex); }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void instanceInitializerUpdatesEveryConstructor(ChangeBehavior mode) throws Exception {
        verify(mode, """
                java.util.Scanner scanner= new java.util.Scanner(new java.io.File("in"), "UTF-8");
                E1() throws java.io.FileNotFoundException { }
                E1(int unused) throws java.io.FileNotFoundException { }
                static E1 create() {
                    try { return new E1(); }
                    catch (java.io.FileNotFoundException ex) { throw new AssertionError(ex); }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void multipleOverridesMustAllAllowTheNewException(ChangeBehavior mode) throws Exception {
        verify(mode, """
                interface First { java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException; }
                interface Second { java.util.Scanner open(java.io.File file) throws java.io.IOException; }
                class Child implements First, Second {
                    public java.util.Scanner open(java.io.File file) throws java.io.FileNotFoundException {
                        return new java.util.Scanner(file, "UTF-8");
                    }
                }
                java.util.Scanner caller(First first, java.io.File file) {
                    try { return first.open(file); }
                    catch (java.io.FileNotFoundException ex) { throw new AssertionError(ex); }
                }
                """, 1);
    }

    @org.junit.jupiter.api.Test
    void standaloneDslAdaptsImplicitFileConstructor() throws Exception {
        verify(ChangeBehavior.KEEP_BEHAVIOR, """
                java.util.Formatter open(java.io.File file) throws java.io.FileNotFoundException {
                    return new java.util.Formatter(file);
                }
                """, 1, """
                new java.util.Formatter($file)
                => new java.util.Formatter($file, java.nio.charset.StandardCharsets.UTF_8, java.util.Locale.ROOT)
                ;;
                """);
    }

    @org.junit.jupiter.api.Test
    void standaloneDslCombinesTwoChangedInvocations() throws Exception {
        verify(ChangeBehavior.KEEP_BEHAVIOR, """
                java.util.Formatter open(java.io.File first, java.io.File second)
                        throws java.io.FileNotFoundException, java.io.UnsupportedEncodingException {
                    try (java.util.Formatter one= new java.util.Formatter(first, "UTF-8")) {
                        one.format("first");
                    }
                    return new java.util.Formatter(second, "UTF-8");
                }
                """, 2, """
                new java.util.Formatter($file, "UTF-8")
                => new java.util.Formatter($file, java.nio.charset.StandardCharsets.UTF_8, java.util.Locale.ROOT)
                ;;
                """);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void inheritedSourceConstructorIsModernized(ChangeBehavior mode) throws Exception {
        verify(mode, """
                static class Input extends java.io.File {
                    private static final long serialVersionUID= 1L;
                    Input() { super("in"); }
                }
                java.util.Scanner open(Input file) throws java.io.FileNotFoundException {
                    return new java.util.Scanner(file);
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void formatterKeepsPrintStreamOwnedEncoding(ChangeBehavior mode) throws Exception {
        var unit= context.getSourceFolder().createPackageFragment("test1", false, null)
                .createCompilationUnit("E1.java", """
                    package test1;
                    public class E1 {
                        java.util.Formatter open(java.io.PrintStream destination) {
                            return new java.util.Formatter(destination);
                        }
                    }
                    """, false, null);
        diagnostics(unit);
        assertNull(new UseExplicitEncodingCleanUpCore(CharsetScopeTest.options(mode)).createFix(new CleanUpContext(unit, parse(unit))));
    }

    @org.junit.jupiter.api.Test
    void fixedBinaryContractReportsAConflictWithoutChangingSource() throws Exception {
        String before= """
                package test1;
                public class E1 {
                    java.util.function.Supplier<java.util.Scanner> later(java.io.File file) {
                        return () -> {
                            try { return new java.util.Scanner(file, "UTF-8"); }
                            catch (java.io.FileNotFoundException ex) { return report(ex); }
                        };
                    }
                    java.util.Scanner report(java.io.FileNotFoundException ex) { return null; }
                }
                """;
        var unit= context.getSourceFolder().createPackageFragment("test1", false, null)
                .createCompilationUnit("E1.java", before, false, null);
        diagnostics(unit);
        var cleanup= new UseExplicitEncodingCleanUpCore(CharsetScopeTest.options(ChangeBehavior.KEEP_BEHAVIOR));
        var status= cleanup.checkPreConditions(unit.getJavaProject(), new ICompilationUnit[] { unit }, null);
        assertTrue(status.hasFatalError(), status.toString());
        assertTrue(status.toString().contains("Fixed external contract"), status.toString());
        assertEquals(before, unit.getSource());
        cleanup.checkPostConditions(null);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void obsoleteExceptionImportsAreRemoved(ChangeBehavior mode) throws Exception {
        String after= verifySource(mode, """
                package test1;
                import java.io.File;
                import java.io.FileNotFoundException;
                import java.io.UnsupportedEncodingException;
                import java.util.Formatter;
                public class E1 {
                    Formatter open(File file) throws FileNotFoundException, UnsupportedEncodingException {
                        return new Formatter(file, "UTF-8");
                    }
                }
                """, 1, null);
        assertFalse(after.contains("import java.io.FileNotFoundException;"), after);
        assertFalse(after.contains("import java.io.UnsupportedEncodingException;"), after);
    }

    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path runtimeDirectory;

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    @org.junit.jupiter.api.parallel.ResourceLock("java.util.Locale")
    void compiledOriginalAndResultKeepFormattingAndUtf8Bytes(ChangeBehavior mode) throws Exception {
        String before= """
                package test1;
                public class E1 {
                    public static String observe() throws java.io.IOException {
                        java.nio.file.Path file= java.nio.file.Files.createTempFile("charset", ".txt");
                        java.util.Locale previous= java.util.Locale.getDefault();
                        java.util.Locale format= java.util.Locale.getDefault(java.util.Locale.Category.FORMAT);
                        try {
                            java.util.Locale.setDefault(java.util.Locale.US);
                            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, java.util.Locale.GERMANY);
                            try (java.util.Formatter out= new java.util.Formatter(file.toFile(), "UTF-8")) {
                                out.format("%.2f %s", 1.25, "ä");
                            }
                            try (java.util.Scanner in= new java.util.Scanner(file.toFile(), "UTF-8")) {
                                return in.nextLine();
                            }
                        } finally {
                            java.util.Locale.setDefault(previous);
                            java.util.Locale.setDefault(java.util.Locale.Category.FORMAT, format);
                            java.nio.file.Files.deleteIfExists(file);
                        }
                    }
                }
                """;
        String after= verifySource(mode, before, 2, null);
        String expected= compileAndRun(before, runtimeDirectory.resolve("before"));
        assertEquals("1,25 ä", expected);
        assertEquals(expected, compileAndRun(after, runtimeDirectory.resolve("after")));
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void wideningDoesNotCaptureAnUnrelatedIOException(ChangeBehavior mode) throws Exception {
        String before= """
                package test1;
                public class E1 {
                    private static java.io.File source() throws java.io.EOFException {
                        throw new java.io.EOFException("argument");
                    }
                    public static String observe() throws java.io.IOException {
                        try {
                            try (java.util.Scanner input= new java.util.Scanner(source(), "UTF-8")) {
                                return input.nextLine();
                            } catch (java.io.FileNotFoundException ex) {
                                return "wrong handler";
                            }
                        } catch (java.io.EOFException ex) {
                            return ex.getMessage();
                        }
                    }
                }
                """;
        String after= verifySource(mode, before, 1, null);
        String expected= compileAndRun(before, runtimeDirectory.resolve("before"));
        assertEquals("argument", expected);
        assertEquals(expected, compileAndRun(after, runtimeDirectory.resolve("after")));
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void scannerResourceCatchIsActuallyWidened(ChangeBehavior mode) throws Exception {
        verify(mode, """
                String read(java.io.File file) {
                    try (java.util.Scanner input= new java.util.Scanner(file, "UTF-8")) {
                        return input.nextLine();
                    } catch (java.io.FileNotFoundException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                """, 1);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void throwsDocumentationFollowsTheModernizedContract(ChangeBehavior mode) throws Exception {
        var options= JavaCore.getOptions();
        options.put(JavaCore.COMPILER_PB_MISSING_JAVADOC_TAGS, JavaCore.WARNING);
        JavaCore.setOptions(options);
        String after= verify(mode, """
                /**
                 * Opens a formatter.
                 * @param file destination
                 * @return formatter
                 * @throws java.io.FileNotFoundException if the destination cannot be opened
                 * @throws java.io.UnsupportedEncodingException if the encoding is unsupported
                 */
                public java.util.Formatter open(java.io.File file)
                        throws java.io.FileNotFoundException, java.io.UnsupportedEncodingException {
                    return new java.util.Formatter(file, "UTF-8");
                }
                """, 1);
        assertFalse(after.contains("@throws java.io.UnsupportedEncodingException"), after);
    }

    @ParameterizedTest
    @EnumSource(ChangeBehavior.class)
    void resourceNlsTagsAndCommentArePreserved(ChangeBehavior mode) throws Exception {
        assertResourceComments(verify(mode, resourceWithComments(), 1));
    }

    @org.junit.jupiter.api.Test
    void standaloneDslRetainsResourceCommentsAndItsCatchRewrite() throws Exception {
        assertResourceComments(verify(ChangeBehavior.KEEP_BEHAVIOR, resourceWithComments(), 1, """
                new java.util.Scanner($file, "UTF-8")
                => new java.util.Scanner($file, java.nio.charset.StandardCharsets.UTF_8)
                ;;
                """));
    }

    private static String resourceWithComments() {
        return """
                String read() {
                    try (java.util.Scanner input= new java.util.Scanner(new java.io.File("source.txt"), "UTF-8")) { //$NON-NLS-1$ //$NON-NLS-2$ keep this comment
                        return input.nextLine();
                    } catch (java.io.FileNotFoundException ex) {
                        throw new IllegalStateException(ex);
                    }
                }
                """;
    }

    private static void assertResourceComments(String after) {
        assertTrue(after.contains("$NON-NLS-1$"), after);
        assertFalse(after.contains("$NON-NLS-2$"), after);
        assertTrue(after.contains("keep this comment"), after);
    }

    private static String compileAndRun(String source, java.nio.file.Path directory) throws Exception {
        java.nio.file.Files.createDirectories(directory);
        var file= directory.resolve("E1.java");
        java.nio.file.Files.writeString(file, source, java.nio.charset.StandardCharsets.UTF_8);
        var compiler= javax.tools.ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler);
        var diagnostics= new javax.tools.DiagnosticCollector<javax.tools.JavaFileObject>();
        try (var manager= compiler.getStandardFileManager(diagnostics, java.util.Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8)) {
            assertTrue(compiler.getTask(null, manager, diagnostics,
                    List.of("--release", "10", "-Xlint:all", "-d", directory.toString()), null,
                    manager.getJavaFileObjects(file)).call(), diagnostics.getDiagnostics().toString());
            assertTrue(diagnostics.getDiagnostics().isEmpty(), diagnostics.getDiagnostics().toString());
        }
        try (var loader= new java.net.URLClassLoader(new java.net.URL[] { directory.toUri().toURL() }, null)) {
            return (String) loader.loadClass("test1.E1").getMethod("observe").invoke(null);
        }
    }

    private String verify(ChangeBehavior mode, String members, int expectedConstructors) throws Exception {
        return verify(mode, members, expectedConstructors, null);
    }

    private String verify(ChangeBehavior mode, String members, int expectedConstructors, String hints) throws Exception {
        String before= "package test1;\npublic class E1 {\n" + members + "}\n"; //$NON-NLS-1$ //$NON-NLS-2$
        return verifySource(mode, before, expectedConstructors, hints);
    }

    private String verifySource(ChangeBehavior mode, String before, int expectedConstructors, String hints) throws Exception {
        ICompilationUnit unit= context.getSourceFolder().createPackageFragment("test1", false, null) //$NON-NLS-1$
                .createCompilationUnit("E1.java", before, false, null); //$NON-NLS-1$
        Map<String, Long> beforeWarnings= diagnostics(unit);
        Map<String, String> options= new HashMap<>();
        options.put(MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true"); //$NON-NLS-1$
        options.put(switch (mode) {
            case KEEP_BEHAVIOR -> MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR;
            case ENFORCE_UTF8 -> MYCleanUpConstants.EXPLICITENCODING_INSERT_UTF8;
            case ENFORCE_UTF8_AGGREGATE -> MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8;
        }, "true"); //$NON-NLS-1$
        var cleanup= new UseExplicitEncodingCleanUpCore(options);
        var fix= hints == null ? cleanup.createFix(new CleanUpContext(unit, parse(unit))) : dslFix(unit, hints);
        assertNotNull(fix, "A positive modernization case must produce a fix"); //$NON-NLS-1$
        var change= fix.createChange(null);
        try {
            String after= change.getPreviewContent(null);
            assertEquals(after, change.getPreviewContent(null));
            assertEquals(before, unit.getSource(), "Preview must not mutate source"); //$NON-NLS-1$
            change.initializeValidationData(null);
            var undo= change.perform(null);
            assertNotNull(undo);
            try {
                diagnostics(unit).forEach((key, count) ->
                        assertTrue(count <= beforeWarnings.getOrDefault(key, 0L), key + "\n" + after)); //$NON-NLS-1$
                AtomicInteger typed= new AtomicInteger();
                parse(unit).accept(new ASTVisitor() {
                    @Override public boolean visit(org.eclipse.jdt.core.dom.MethodDeclaration node) {
                        var exceptions= node.resolveBinding().getExceptionTypes();
                        assertEquals(exceptions.length, Arrays.stream(exceptions).map(t -> t.getQualifiedName()).distinct().count(), after);
                        return true;
                    }
                    @Override public boolean visit(ClassInstanceCreation node) {
                        var binding= node.resolveConstructorBinding();
                        assertNotNull(binding);
                        String owner= binding.getDeclaringClass().getQualifiedName();
                        if (owner.equals("java.util.Scanner") || owner.equals("java.util.Formatter")) { //$NON-NLS-1$ //$NON-NLS-2$
                            assertTrue(binding.getParameterTypes().length > 1, after);
                            assertEquals("java.nio.charset.Charset", binding.getParameterTypes()[1].getQualifiedName(), after); //$NON-NLS-1$
                            typed.incrementAndGet();
                        }
                        return true;
                    }
                });
                assertEquals(expectedConstructors, typed.get(), after);
                assertFalse(after.contains(".name()"), after); //$NON-NLS-1$
                assertNull(hints == null ? cleanup.createFix(new CleanUpContext(unit, parse(unit))) : dslFix(unit, hints), "Idempotence"); //$NON-NLS-1$
                var redo= undo.perform(null);
                if (redo != null) redo.dispose();
                assertEquals(before, unit.getSource());
                assertEquals(beforeWarnings, diagnostics(unit));
                return after;
            } finally { undo.dispose(); }
        } finally { change.dispose(); }
    }

    private static org.eclipse.jdt.ui.cleanup.ICleanUpFix dslFix(ICompilationUnit unit, String hints) throws Exception {
        var root= parse(unit);
        var operations= new java.util.LinkedHashSet<org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation>();
        org.sandbox.jdt.triggerpattern.cleanup.HintFileFixCore.findOperationsFromContent(root, hints, operations);
        return operations.isEmpty() ? null : new org.sandbox.jdt.triggerpattern.cleanup.NlsAwareCleanUpFix("DSL Charset modernization", root,
                operations.toArray(org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation[]::new));
    }

    static CompilationUnit parse(ICompilationUnit unit) {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }

    static Map<String, Long> diagnostics(ICompilationUnit unit) {
        IProblem[] problems= parse(unit).getProblems();
        assertEquals(List.of(), Arrays.stream(problems).filter(IProblem::isError).map(IProblem::getMessage).toList());
        return Arrays.stream(problems).filter(IProblem::isWarning)
                .collect(Collectors.groupingBy(p -> p.getID() + Arrays.toString(p.getArguments()), Collectors.counting()));
    }
}
