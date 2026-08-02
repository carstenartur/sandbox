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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_SELECT_CLASSES;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ANNOTATION_SUITE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_SUITE;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3MigrationExclusions;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JUnit3SuiteModel;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Migrates JUnit 3 {@code public static Test suite()} aggregators to the
 * JUnit Platform suite model.
 *
 * <p>The aggregator pattern is dominant in {@code org.eclipse.jdt.ui.tests*}
 * ({@code AllTests}, {@code *TestSuite}). Only aggregators whose selected
 * classes are provable from the source are migrated; everything else is left
 * untouched, matching the fail-closed contract of the JUnit 3 hierarchy planner.
 *
 * <pre>
 * public class AllTests {                     &#64;Suite
 *     public static Test suite() {      →     &#64;SelectClasses({ FooTest.class, BarTest.class })
 *         TestSuite suite= new TestSuite();   public class AllTests {
 *         suite.addTestSuite(FooTest.class);  }
 *         suite.addTestSuite(BarTest.class);
 *         return suite;
 *     }
 * }
 * </pre>
 */
public class SuiteMethodJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(TypeDeclaration node) {
				MethodDeclaration suite= findMigratableSuiteMethod(node, nodesprocessed);
				if (suite == null) {
					return true;
				}
				JUnit3SuiteModel.Result model= JUnit3SuiteModel.analyze(suite);
				if (!model.supported()) {
					return true;
				}
				nodesprocessed.add(node);
				nodesprocessed.add(suite);
				ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
				JunitHolder holder= new JunitHolder();
				holder.setMinv(suite);
				holder.setAdditionalInfo(model.selectedTypes());
				dataHolder.put(dataHolder.size(), holder);
				operations.add(fixcore.rewrite(dataHolder));
				return true;
			}
		});
	}

	private MethodDeclaration findMigratableSuiteMethod(TypeDeclaration node, Set<ASTNode> nodesprocessed) {
		if (nodesprocessed.contains(node) || !(node.getParent() instanceof CompilationUnit)
				|| node.getSuperclassType() != null || node.isInterface() || hasJUnitAnnotation(node)
				|| isExcludedOrTestCase(node)) {
			return null;
		}
		MethodDeclaration suite= null;
		for (MethodDeclaration method : node.getMethods()) {
			if (JUnit3SuiteModel.isSuiteBuilder(method)) {
				suite= method;
			} else if (!method.isConstructor()) {
				// Any additional behavior in an aggregator is not represented by @Suite.
				return null;
			}
		}
		if (suite == null || nodesprocessed.contains(suite) || node.getFields().length != 0
				|| node.getTypes().length != 0) {
			return null;
		}
		return suite;
	}

	/**
	 * Rejects aggregators that are test cases themselves or derive from a base
	 * type whose execution contract must not be migrated.
	 */
	private boolean isExcludedOrTestCase(TypeDeclaration node) {
		ITypeBinding binding= node.resolveBinding();
		if (binding == null) {
			return node.getSuperclassType() != null;
		}
		if (JUnit3MigrationExclusions.isExcluded(binding)) {
			return true;
		}
		for (ITypeBinding current= binding.getSuperclass(); current != null; current= current.getSuperclass()) {
			if ("junit.framework.TestCase".equals(current.getErasure().getQualifiedName())) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private boolean hasJUnitAnnotation(TypeDeclaration node) {
		for (Object modifier : node.modifiers()) {
			if (!(modifier instanceof Annotation annotation)) {
				continue;
			}
			ITypeBinding binding= annotation.resolveTypeBinding();
			String name= binding == null || binding.isRecovered()
					? annotation.getTypeName().getFullyQualifiedName()
					: binding.getQualifiedName();
			if (name.startsWith("org.junit.") || name.startsWith("junit.")) { //$NON-NLS-1$ //$NON-NLS-2$
				return true;
			}
		}
		return false;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		if (!(junitHolder.getMinv() instanceof MethodDeclaration suite)
				|| !(suite.getParent() instanceof TypeDeclaration type)
				|| !(junitHolder.getAdditionalInfo() instanceof List<?> selectedTypes)
				|| selectedTypes.isEmpty()) {
			return;
		}

		rewriter.remove(suite, group);

		ListRewrite modifiers= rewriter.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		MarkerAnnotation suiteAnnotation= ast.newMarkerAnnotation();
		suiteAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SUITE));
		modifiers.insertFirst(suiteAnnotation, group);

		SingleMemberAnnotation selectClasses= ast.newSingleMemberAnnotation();
		selectClasses.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
		if (selectedTypes.size() == 1) {
			selectClasses.setValue(newTypeLiteral(ast, String.valueOf(selectedTypes.get(0))));
		} else {
			ArrayInitializer initializer= ast.newArrayInitializer();
			for (Object selected : selectedTypes) {
				addExpression(initializer, newTypeLiteral(ast, String.valueOf(selected)));
			}
			selectClasses.setValue(initializer);
		}
		modifiers.insertAfter(selectClasses, suiteAnnotation, group);

		importRewriter.addImport(ORG_JUNIT_JUPITER_SUITE);
		importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES);
		importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST);
		importRewriter.removeImport(JUnit3SuiteModel.JUNIT3_TEST_SUITE);
	}

	@SuppressWarnings("unchecked")
	private static void addExpression(ArrayInitializer initializer, TypeLiteral literal) {
		initializer.expressions().add(literal);
	}

	private static TypeLiteral newTypeLiteral(AST ast, String typeName) {
		TypeLiteral literal= ast.newTypeLiteral();
		literal.setType(ast.newSimpleType(ast.newName(typeName)));
		return literal;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Suite
					@SelectClasses({ FooTest.class, BarTest.class })
					public class AllTests {
					}
					"""; //$NON-NLS-1$
		}
		return """
				public class AllTests {
					public static Test suite() {
						TestSuite suite= new TestSuite();
						suite.addTestSuite(FooTest.class);
						suite.addTestSuite(BarTest.class);
						return suite;
					}
				}
				"""; //$NON-NLS-1$
	}
}
