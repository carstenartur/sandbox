/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Parameter usage is a Java syntax question, not a regular-expression word boundary. */
@SuppressWarnings("nls")
class LambdaParameterReferenceTest {
    private IProject project;
    private IJavaProject javaProject;
    private ICompilationUnit unit;
    private CompilationUnit root;

    @BeforeEach
    void createProject() throws Exception {
        project= ResourcesPlugin.getWorkspace().getRoot().getProject("LambdaParameterReference");
        project.create(null);
        project.open(null);
        var description= project.getDescription();
        description.setNatureIds(new String[] { JavaCore.NATURE_ID });
        project.setDescription(description, null);
        javaProject= JavaCore.create(project);
        var src= project.getFolder("src");
        src.create(true, true, null);
        javaProject.setRawClasspath(new org.eclipse.jdt.core.IClasspathEntry[] {
                JavaCore.newSourceEntry(src.getFullPath()),
                JavaCore.newContainerEntry(new Path("org.eclipse.jdt.launching.JRE_CONTAINER")) },
                project.getFullPath().append("bin"), null);
        sourceVersion(JavaCore.VERSION_22);
        unit= javaProject.getPackageFragmentRoot(src).createPackageFragment("probe", false, null)
                .createCompilationUnit("Example.java", "package probe; public class Example {}", false, null);
        root= parse();
    }

    @AfterEach
    void deleteProject() throws Exception {
        if (project != null && project.exists()) project.delete(true, true, null);
    }

    @Test void leadingDollarParameterIsNotRenamedAway() throws Exception { checkUsed("$value"); }
    @Test void trailingDollarParameterIsNotRenamedAway() throws Exception { checkUsed("value$"); }
    @Test void unicodeParameterIsPreserved() throws Exception { checkUsed("über"); }

    @Test
    void stringContentsAreNotVariableReferences() throws Exception {
        var renderer= renderer();
        check(renderer.renderForEach(root.getAST().newSimpleName("items"),
                List.of("System.out.println(\"value\")"), "value", false), "_");
    }

    @Test
    void blockStatementsRecognizeDollarParameters() throws Exception {
        check(renderer().renderForEach(root.getAST().newSimpleName("items"),
                List.of("System.out.println($value)", "System.out.println(\"again\")"), "$value", false), "$value");
    }

    @Test
    void absentBodyKeepsTheDocumentedEmptyBodyFallback() throws Exception {
        check(renderer().renderForEachWithBody(root.getAST().newSimpleName("items"), () -> null, "value", false), "_");
    }

    @Test
    void olderSourceLevelsKeepNamedParameters() throws Exception {
        sourceVersion(JavaCore.VERSION_21);
        check(renderer().renderForEachWithBody(root.getAST().newSimpleName("items"), () -> null, "value", false), "value");
    }

    @Test
    void unspecifiedSourceLevelDoesNotIntroduceUnnamedSyntax() {
        AST ast= root.getAST();
        var renderer= new ASTStreamRenderer(ast, ASTRewrite.create(ast), null, null);
        assertEquals("value", parameter(renderer.renderForEach(ast.newSimpleName("items"),
                List.of("System.out.println(\"value\")"), "value", false)));
    }

    private void checkUsed(String name) throws Exception {
        check(renderer().renderForEach(root.getAST().newSimpleName("items"),
                List.of("System.out.println(" + name + ")"), name, false), name);
    }

    private ASTStreamRenderer renderer() {
        return new ASTStreamRenderer(root.getAST(), ASTRewrite.create(root.getAST()), root, null);
    }

    private void check(Expression call, String expectedParameter) throws Exception {
        assertEquals(expectedParameter, parameter(call), call.toString());
        unit.getBuffer().setContents("package probe; public class Example { public void run(java.util.List<String> items) { "
                + call + "; } }");
        assertFalse(Arrays.stream(parse().getProblems()).anyMatch(problem -> problem.isError()),
                () -> Arrays.toString(parse().getProblems()));
    }

    private static String parameter(Expression call) {
        var lambda= (LambdaExpression) ((MethodInvocation) call).arguments().get(0);
        return ((VariableDeclarationFragment) lambda.parameters().get(0)).getName().getIdentifier();
    }

    private CompilationUnit parse() {
        var parser= ASTParser.newParser(AST.getJLSLatest());
        parser.setSource(unit);
        parser.setResolveBindings(true);
        return (CompilationUnit) parser.createAST(null);
    }

    private void sourceVersion(String version) {
        var options= javaProject.getOptions(true);
        JavaCore.setComplianceOptions(version, options);
        javaProject.setOptions(options);
    }
}
