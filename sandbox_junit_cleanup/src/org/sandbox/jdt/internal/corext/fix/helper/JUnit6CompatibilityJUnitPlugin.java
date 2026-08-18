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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_METHOD_ORDERER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER;

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Applies source-level compatibility rewrites for APIs removed by JUnit 6.
 *
 * <p>The initial compatibility rule replaces the removed
 * {@code MethodOrderer.Alphanumeric} implementation with
 * {@code MethodOrderer.MethodName}. Detection is binding-based: a user-defined
 * nested class named {@code Alphanumeric} is never changed by spelling alone.</p>
 *
 * <p>Build files, PDE manifests, Eclipse classpath containers and service
 * registrations are deliberately outside this local source cleanup. They require
 * the atomic project-resource migration plan tracked by the JUnit migration
 * architecture.</p>
 */
public final class JUnit6CompatibilityJUnitPlugin
		extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	private static final String JUPITER_ALPHANUMERIC=
			"org.junit.jupiter.api.MethodOrderer.Alphanumeric"; //$NON-NLS-1$

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		HelperVisitorFactory.forAnnotation(ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)
				.in(compilationUnit)
				.excluding(nodesprocessed)
				.processEach(dataHolder, (visited, holder) -> {
					if (!(visited instanceof SingleMemberAnnotation annotation)
							|| !(annotation.getValue() instanceof TypeLiteral typeLiteral)) {
						return true;
					}
					ITypeBinding binding= typeLiteral.getType().resolveBinding();
					if (binding == null
							|| !JUPITER_ALPHANUMERIC.equals(binding.getErasure().getQualifiedName())) {
						return true;
					}
					nodesprocessed.add(annotation);
					return addStandardRewriteOperation(fixcore, operations, annotation, holder);
				});
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation oldAnnotation= junitHolder.getAnnotation();
		SingleMemberAnnotation replacement= ast.newSingleMemberAnnotation();
		replacement.setTypeName(ast.newName(
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER)));

		Name methodOrderer= ast.newName(
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_METHOD_ORDERER));
		TypeLiteral typeLiteral= ast.newTypeLiteral();
		typeLiteral.setType(ast.newSimpleType(ast.newQualifiedName(
				methodOrderer,
				ast.newSimpleName("MethodName")))); //$NON-NLS-1$
		replacement.setValue(typeLiteral);

		rewriter.replace(oldAnnotation, replacement, group);
		importRewriter.removeImport(JUPITER_ALPHANUMERIC);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.MethodOrderer;
					import org.junit.jupiter.api.TestMethodOrder;

					@TestMethodOrder(MethodOrderer.MethodName.class)
					public class OrderedTest {
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.jupiter.api.MethodOrderer;
				import org.junit.jupiter.api.TestMethodOrder;

				@TestMethodOrder(MethodOrderer.Alphanumeric.class)
				public class OrderedTest {
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "JUnit6Compatibility"; //$NON-NLS-1$
	}
}
