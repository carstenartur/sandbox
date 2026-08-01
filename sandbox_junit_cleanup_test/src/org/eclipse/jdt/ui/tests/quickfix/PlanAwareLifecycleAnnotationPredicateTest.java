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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.dom.AST;
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
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;

/** Regression contract for lifecycle annotation facts used by the JUnit 3 DSL. */
class PlanAwareLifecycleAnnotationPredicateTest {

	private static final String HINT= """
			<!id: lifecycle-demo>
			<!requires-plan: lifecycle-demo>
			<!predicate hasOverrideAnnotation($method):
			    hasAnnotation($method, Override) || hasAnnotation($method, java.lang.Override)>

			@id: lifecycle.override
			void $name() :: plannedRole($name, "LIFECYCLE") && plannedValue($name, "removeOverride", true) && hasOverrideAnnotation($name)
			=>! removeAnnotation(target=$name, annotation="java.lang.Override");
			    addAnnotation(target=$name, annotation="org.junit.jupiter.api.BeforeEach")
			;;

			@id: lifecycle.plain
			void $name() :: plannedRole($name, "LIFECYCLE") && plannedValue($name, "removeOverride", false) && !hasOverrideAnnotation($name)
			=>! addAnnotation(target=$name, annotation="org.junit.jupiter.api.BeforeEach")
			;;
			"""; //$NON-NLS-1$

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava17();

	@Test
	void acceptsBothOverrideSpellingsWhenThePlanRecordedTheAnnotation() throws CoreException {
		ICompilationUnit unit= createUnit("lifecyclespelling", """
				package lifecyclespelling;
				interface Contract {
					void simple();
					void qualified();
				}
				public class Sample implements Contract {
					@Override
					public void simple() {
					}

					@java.lang.Override
					public void qualified() {
					}
				}
				"""); //$NON-NLS-1$
		CompilationUnit ast= parse(unit);
		MethodDeclaration simple= findMethod(ast, "simple"); //$NON-NLS-1$
		MethodDeclaration qualified= findMethod(ast, "qualified"); //$NON-NLS-1$
		NodeKey simpleKey= NodeKey.from(simple);
		NodeKey qualifiedKey= NodeKey.from(qualified);
		SemanticRewritePlan plan= SemanticRewritePlan.builder("lifecycle-demo") //$NON-NLS-1$
				.add(simpleKey, "LIFECYCLE").putBoolean(simpleKey, "removeOverride", true) //$NON-NLS-1$ //$NON-NLS-2$
				.add(qualifiedKey, "LIFECYCLE").putBoolean(qualifiedKey, "removeOverride", true) //$NON-NLS-1$ //$NON-NLS-2$
				.build();

		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<org.eclipse.jdt.core.dom.ASTNode> processed= new LinkedHashSet<>();
		Set<NodeKey> covered= PlanAwareHintFileFixCore.findOperationsFromContent(ast, HINT, plan,
				unit.getJavaProject().getOptions(true), operations, processed);

		assertEquals(Set.of(simpleKey, qualifiedKey), covered);
		assertEquals(2, operations.size());
		assertTrue(processed.contains(simple));
		assertTrue(processed.contains(qualified));
	}

	@Test
	void rejectsAFalseFactWhenOverrideAppearedAfterPlanning() throws CoreException {
		ICompilationUnit unit= createUnit("lifecyclestale", """
				package lifecyclestale;
				interface Contract {
					void changed();
				}
				public class Sample implements Contract {
					@Override
					public void changed() {
					}
				}
				"""); //$NON-NLS-1$
		CompilationUnit ast= parse(unit);
		MethodDeclaration method= findMethod(ast, "changed"); //$NON-NLS-1$
		NodeKey key= NodeKey.from(method);
		SemanticRewritePlan stalePlan= SemanticRewritePlan.builder("lifecycle-demo") //$NON-NLS-1$
				.add(key, "LIFECYCLE").putBoolean(key, "removeOverride", false) //$NON-NLS-1$ //$NON-NLS-2$
				.build();

		Set<CompilationUnitRewriteOperationWithSourceRange> operations= new LinkedHashSet<>();
		Set<org.eclipse.jdt.core.dom.ASTNode> processed= new LinkedHashSet<>();
		Set<NodeKey> covered= PlanAwareHintFileFixCore.findOperationsFromContent(ast, HINT, stalePlan,
				unit.getJavaProject().getOptions(true), operations, processed);

		assertTrue(covered.isEmpty());
		assertTrue(operations.isEmpty());
		assertTrue(processed.isEmpty());
	}

	private ICompilationUnit createUnit(String packageName, String source) throws CoreException {
		IPackageFragmentRoot root= context.createClasspathForJUnit(JUnitCore.JUNIT5_CONTAINER_PATH);
		IPackageFragment pack= root.createPackageFragment(packageName, true, null);
		return pack.createCompilationUnit("Sample.java", source, false, null); //$NON-NLS-1$
	}

	private CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
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
