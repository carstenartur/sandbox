/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.internal.ui.fix.UseExplicitEncodingCleanUpCore;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;

/** Real cleanup/compilation/Undo contracts for mixed checked-exception scopes. */
public class EncodingExceptionSafetyTest {
    private IProject project;
    private boolean projectCreated;
    private ICompilationUnit unit;

    @BeforeEach
    void setUp() throws Exception {
        project= ResourcesPlugin.getWorkspace().getRoot().getProject("EncodingExceptionSafety"); //$NON-NLS-1$
        assertFalse(project.exists());
        project.create(null);
        projectCreated= true;
        project.open(null);
        var description= project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        var javaProject= JavaCore.create(project);
        var folder= project.getFolder("src"); //$NON-NLS-1$
        folder.create(true, true, null);
        javaProject.setRawClasspath(new IClasspathEntry[] { JavaCore.newSourceEntry(folder.getFullPath()),
                JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) }, //$NON-NLS-1$
                project.getFullPath().append("bin"), null); //$NON-NLS-1$
        Map<String, String> options= new HashMap<>(javaProject.getOptions(true));
        JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
        options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
        options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4"); //$NON-NLS-1$
        javaProject.setOptions(options);
        unit= javaProject.getPackageFragmentRoot(folder).createPackageFragment("test", false, null) //$NON-NLS-1$
                .createCompilationUnit("E.java", "", false, null); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @AfterEach
    void tearDown() throws Exception {
        if (projectCreated && project != null && project.exists()) {
            project.delete(true, true, null);
        }
    }

    static Stream<Arguments> scenarios() {
        String converted= "String first= new String(bytes, \"UTF-8\");"; //$NON-NLS-1$
        String[] survivors= {
                "String second= new String(bytes, encoding);", //$NON-NLS-1$
                "String second= new String(bytes, \"unknown-charset\");", //$NON-NLS-1$
                "throwEncoding();", //$NON-NLS-1$
                "String nested= new String(throwingBytes(), \"UTF-8\");", //$NON-NLS-1$
                "byte[] nested= throwingString().getBytes(\"UTF-8\");", //$NON-NLS-1$
                "if (bytes.length == 0) throw new UnsupportedEncodingException();", //$NON-NLS-1$
                "if (bytes.length == 0) throw new ChildException();", //$NON-NLS-1$
                "throwBroad();" //$NON-NLS-1$
        };
        return Stream.concat(Stream.of(false, true).flatMap(aggregate -> Stream.concat(
                Arrays.stream(survivors).flatMap(survivor -> Stream.of(false, true)
                        .map(first -> Arguments.of(aggregate,
                                "try { " + (first ? converted + survivor : survivor + converted) //$NON-NLS-1$
                                + " } catch (UnsupportedEncodingException ex) { handle(ex); }", true))), //$NON-NLS-1$
                Stream.of(Arguments.of(aggregate, "try { " + converted //$NON-NLS-1$
                        + " } catch (UnsupportedEncodingException ex) { handle(ex); }", false), //$NON-NLS-1$
                        Arguments.of(aggregate, "try { " + converted //$NON-NLS-1$
                        + " String second= new String(bytes, \"UTF-16\");" //$NON-NLS-1$
                        + " } catch (UnsupportedEncodingException ex) { handle(ex); }", false)))), additionalScenarios()); //$NON-NLS-1$
    }

    static Stream<Arguments> additionalScenarios() {
        String call= "String first= new String(bytes, \"UTF-8\");"; //$NON-NLS-1$
        return Stream.of(false, true).flatMap(aggregate -> Stream.of(
                Arguments.of(aggregate, "try (S r= new S()) { " + call + " } catch (UnsupportedEncodingException ex) { handle(ex); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try (R r= new R()) { " + call + " } catch (UnsupportedEncodingException ex) { handle(ex); }", true), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "R r= new R(); try (r) { " + call + " } catch (UnsupportedEncodingException ex) { handle(ex); }", true), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " } catch (java.io.UnsupportedEncodingException ex) { handle(ex); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " } catch (UnsupportedEncodingException ex) { handle(ex); } finally { throwEncoding(); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " try { throwEncoding(); } catch (IOException ex) { handle(ex); } } catch (UnsupportedEncodingException ex) { handle(ex); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " java.util.concurrent.Callable<String> later= () -> new String(bytes, encoding); } catch (UnsupportedEncodingException ex) { handle(ex); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " throwOther(); } catch (UnsupportedEncodingException | ReflectiveOperationException ex) { handle(ex); }", false), //$NON-NLS-1$ //$NON-NLS-2$
                Arguments.of(aggregate, "try { " + call + " throwEncoding(); throwOther(); } catch (UnsupportedEncodingException | ReflectiveOperationException ex) { handle(ex); }", true))); //$NON-NLS-1$ //$NON-NLS-2$
    }

    @ParameterizedTest
    @MethodSource("scenarios") //$NON-NLS-1$
    void preservesNeededHandlersAndRemovesOnlyObsoleteOnes(boolean aggregate, String body, boolean retained)
            throws Exception {
        verify(aggregate, source(body), retained,
                "(?s).*catch \\(\\s*(?:java\\.io\\.)?UnsupportedEncodingException.*"); //$NON-NLS-1$
    }

    static Stream<Arguments> methodScenarios() {
        return Stream.of(false, true).flatMap(aggregate -> Stream.of(false, true)
                .map(survives -> Arguments.of(aggregate, survives)));
    }

    @ParameterizedTest
    @MethodSource("methodScenarios") //$NON-NLS-1$
    void removesThrowsOnlyAfterTheLastExceptionSourceDisappears(boolean aggregate, boolean survives) throws Exception {
        String body= "String first= new String(bytes, \"UTF-8\");" + (survives ? "throwEncoding();" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        String before= source(body).replace("void run(byte[] bytes, String encoding) throws IOException", //$NON-NLS-1$
                "void run(byte[] bytes, String encoding) throws java.io.UnsupportedEncodingException"); //$NON-NLS-1$
        verify(aggregate, before, survives, "(?s).*void run[^\\{]*throws java.io.UnsupportedEncodingException.*"); //$NON-NLS-1$
    }

    private static String source(String body) {
        return """
                package test;
                import java.io.IOException;
                import java.io.UnsupportedEncodingException;
                public class E {
                    static class ChildException extends UnsupportedEncodingException { }
                    static class R implements AutoCloseable { public void close() throws UnsupportedEncodingException { } }
                    static class S implements AutoCloseable { public void close() { } }
                    static void throwOther() throws ReflectiveOperationException { }
                    static void throwEncoding() throws UnsupportedEncodingException { }
                    static void throwBroad() throws IOException { }
                    static byte[] throwingBytes() throws UnsupportedEncodingException { return new byte[0]; }
                    static String throwingString() throws UnsupportedEncodingException { return ""; }
                    static void handle(Exception ex) { }
                    void run(byte[] bytes, String encoding) throws IOException {
                """ + body.indent(8) + "    }\n}\n"; //$NON-NLS-1$
    }

    private void verify(boolean aggregate, String before, boolean retained, String declaration) throws Exception {
        unit.getBuffer().setContents(before);
        unit.save(null, true);
        assertCompiles();
        var cleanup= new UseExplicitEncodingCleanUpCore(Map.of(
                MYCleanUpConstants.EXPLICITENCODING_CLEANUP, "true", //$NON-NLS-1$
                aggregate ? MYCleanUpConstants.EXPLICITENCODING_AGGREGATE_TO_UTF8
                        : MYCleanUpConstants.EXPLICITENCODING_KEEP_BEHAVIOR, "true")); //$NON-NLS-1$
        var fix= cleanup.createFix(new CleanUpContext(unit, parse()));
        assertNotNull(fix);
        var change= fix.createChange(null);
        try {
            String after= change.getPreviewContent(null);
            assertEquals(retained, after.matches(declaration), after);
            assertFalse(after.contains("new String(bytes, \"UTF-8\")"), after); //$NON-NLS-1$
            assertEquals(before, unit.getSource());
            change.initializeValidationData(null);
            var undo= change.perform(null);
            assertNotNull(undo);
            try {
                assertEquals(after, unit.getSource());
                assertCompiles();
                var redo= undo.perform(null);
                if (redo != null) redo.dispose();
                assertEquals(before, unit.getSource());
            } finally {
                undo.dispose();
            }
        } finally {
            change.dispose();
        }
    }

    @Test
    void unresolvedSurvivorDoesNotAuthorizeRemoval() throws Exception {
        String call= "new String(bytes, \"UTF-8\")"; //$NON-NLS-1$
        String source= source("try { String first= " + call //$NON-NLS-1$
                + "; unresolvedCall(); } catch (UnsupportedEncodingException ex) { handle(ex); }"); //$NON-NLS-1$
        unit.getBuffer().setContents(source);
        CompilationUnit root= parse();
        assertTrue(Arrays.stream(root.getProblems()).anyMatch(IProblem::isError));
        ASTNode converted= NodeFinder.perform(root, source.indexOf(call), call.length());
        ASTNode scope= converted;
        while (!(scope instanceof TryStatement)) scope= scope.getParent();
        assertFalse(ExceptionCleanupHelper.canRemoveException(scope, converted,
                "java.io.UnsupportedEncodingException", ASTRewrite.create(root.getAST()))); //$NON-NLS-1$
        assertEquals(source, unit.getSource());
    }

    private CompilationUnit parse() {
        ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private void assertCompiles() {
        assertEquals(java.util.List.of(), Arrays.stream(parse().getProblems()).filter(IProblem::isError)
                .map(IProblem::getMessage).toList());
    }
}
