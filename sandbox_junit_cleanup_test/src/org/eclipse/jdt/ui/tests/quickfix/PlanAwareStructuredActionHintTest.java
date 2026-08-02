/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.junit.JUnitCore;

import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan;
import org.sandbox.jdt.triggerpattern.api.SemanticRewritePlan.NodeKey;
import org.sandbox.jdt.triggerpattern.cleanup.PlanAwareHintFileFixCore;
import org.sandbox.jdt.triggerpattern.cleanup.actions.StructuredRewriteActionOperation;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

class PlanAwareStructuredActionHintTest {

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@Test
	void factBackedActionSequenceReachesTheTypedAstOperation() throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment("sample", true, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("Sample.java", //$NON-NLS-1$
				"""
				package sample;
				public class Sample {
					public void testOne() {
					}
				}
				""", false, null);
		CompilationUnit ast= parse(unit);
		MethodDeclaration method= findMethod(ast, "testOne"); //$NON-NLS-1$
		NodeKey methodKey= NodeKey.from(method);
		SemanticRewritePlan plan= SemanticRewritePlan.builder("structured-demo") //$NON-NLS-1$
				.add(methodKey, "TEST_METHOD") //$NON-NLS-1$
				.putInteger(methodKey, "order", 7) //$NON-NLS-1$
				.build();
		String hint= """
				<!id: structured-demo>
				<!requires-plan: structured-demo>
				void $name() :: plannedRole($name, "TEST_METHOD")
				=>! addAnnotation(target=$name, annotation="org.junit.jupiter.api.Test");
				    addAnnotation(target=$name, annotation="org.junit.jupiter.api.Order",
				        value=planValue($name, "order"));
				    removeModifier(target=$name, modifier=public)
				;;
				""";
		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<org.eclipse.jdt.core.dom.ASTNode> processed= new LinkedHashSet<>();

		Set<NodeKey> covered= PlanAwareHintFileFixCore.findOperationsFromContent(ast, hint, plan,
				unit.getJavaProject().getOptions(true), operations, processed);

		assertEquals(Set.of(methodKey), covered);
		assertEquals(1, operations.size());
		assertInstanceOf(StructuredRewriteActionOperation.class, operations.iterator().next());
		assertTrue(processed.contains(method));
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setProject(context.getJavaProject());
		parser.setSource(unit);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(IASTSharedValues.SHARED_BINDING_RECOVERY);
		parser.setStatementsRecovery(IASTSharedValues.SHARED_AST_STATEMENT_RECOVERY);
		parser.setCompilerOptions(RefactoringASTParser.getCompilerOptions(context.getJavaProject()));
		return (CompilationUnit) parser.createAST(null);
	}

	private static MethodDeclaration findMethod(CompilationUnit ast, String name) {
		MethodDeclaration[] result= new MethodDeclaration[1];
		ast.accept(new ASTVisitor() {
			@Override
			public boolean visit(MethodDeclaration node) {
				if (name.equals(node.getName().getIdentifier())) {
					result[0]= node;
				}
				return result[0] == null;
			}
		});
		return java.util.Objects.requireNonNull(result[0]);
	}
}
