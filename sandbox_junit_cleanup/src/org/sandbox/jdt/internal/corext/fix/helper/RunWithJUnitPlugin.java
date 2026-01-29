/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.*;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @RunWith and @Suite.SuiteClasses to JUnit 5 equivalents.
 */
public class RunWithJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		
		// Find @RunWith annotations
		HelperVisitor.forAnnotation(ORG_JUNIT_RUNWITH)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof SingleMemberAnnotation) {
					return processFoundNodeRunWith(fixcore, operations, (Annotation) visited, aholder);
				}
				return true;
			});
		
		// Find @Suite.SuiteClasses annotations
		HelperVisitor.forAnnotation(ORG_JUNIT_SUITE_SUITECLASSES)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof SingleMemberAnnotation) {
					return processFoundNodeSuite(fixcore, operations, (Annotation) visited, aholder);
				}
				return true;
			});
	}

	private boolean processFoundNodeRunWith(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value= mynode.getValue();
			if (value instanceof TypeLiteral myvalue) {
				ITypeBinding classBinding= myvalue.resolveTypeBinding();
				String runnerQualifiedName = null;
				
				// Try to get qualified name from binding
				// For TypeLiteral (e.g., Suite.class), we need to get the type being referenced, not Class itself
				if (classBinding != null) {
					// Get the type from the TypeLiteral's type, not from the Class binding
					Type type = myvalue.getType();
					if (type != null) {
						ITypeBinding typeBinding = type.resolveBinding();
						if (typeBinding != null) {
							runnerQualifiedName = typeBinding.getQualifiedName();
						}
					}
				}
				
				// If binding resolution failed, try to get fully qualified name from the AST
				if (runnerQualifiedName == null || runnerQualifiedName.isEmpty()) {
					Type runnerType = myvalue.getType();
					if (runnerType != null) {
						String typeName = runnerType.toString();
						// Only use it if it's a fully qualified name (contains a dot)
						if (typeName != null && typeName.contains(".")) {
							runnerQualifiedName = typeName;
						}
						// Special case: Suite is a JUnit library class, so we can safely migrate it
						// even with just the simple name (unlike third-party frameworks)
						else if ("Suite".equals(typeName)) {
							runnerQualifiedName = ORG_JUNIT_SUITE;
						}
						// For other simple names, we can't safely migrate to avoid false positives
					}
				}
				
				// Handle Suite runner
				if (ORG_JUNIT_SUITE.equals(runnerQualifiedName)) {
					mh.value= ORG_JUNIT_RUNWITH;
					dataHolder.put(dataHolder.size(), mh);
					operations.add(fixcore.rewrite(dataHolder));
					return true; // Continue processing other annotations
				}
				
				// Handle Mockito runners - only check qualified names to avoid false positives
				if (ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER.equals(runnerQualifiedName) ||
						ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER.equals(runnerQualifiedName)) {
					mh.value= ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER;
					dataHolder.put(dataHolder.size(), mh);
					operations.add(fixcore.rewrite(dataHolder));
					return true; // Continue processing other annotations
				}
				
				// Handle Spring runners - only check qualified names to avoid false positives
				if (ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER.equals(runnerQualifiedName) ||
						ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER.equals(runnerQualifiedName)) {
					mh.value= ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER;
					dataHolder.put(dataHolder.size(), mh);
					operations.add(fixcore.rewrite(dataHolder));
					return true; // Continue processing other annotations
				}
			}
		}
		// Return true to continue processing other annotations
		return true;
	}

	private boolean processFoundNodeSuite(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.minv= node;
		mh.minvname= node.getTypeName().getFullyQualifiedName();
		mh.value= ORG_JUNIT_SUITE_SUITECLASSES;
		dataHolder.put(dataHolder.size(), mh);
		operations.add(fixcore.rewrite(dataHolder));
		// Return true to continue processing other annotations
		return true;
	}

	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv= junitHolder.getAnnotation();
		Annotation newAnnotation= null;
		
		if (ORG_JUNIT_SUITE_SUITECLASSES.equals(junitHolder.value)) {
			// Handle @Suite.SuiteClasses migration
			SingleMemberAnnotation mynode= (SingleMemberAnnotation) minv;
			newAnnotation= ast.newSingleMemberAnnotation();
			((SingleMemberAnnotation) newAnnotation)
					.setValue(ASTNodes.createMoveTarget(rewriter, mynode.getValue()));
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SELECT_CLASSES));
			importRewriter.addImport(ORG_JUNIT_PLATFORM_SUITE_API_SELECT_CLASSES);
			importRewriter.removeImport(ORG_JUNIT_SUITE_SUITECLASSES);
			importRewriter.removeImport(ORG_JUNIT_SUITE);
		} else if (ORG_JUNIT_RUNWITH.equals(junitHolder.value)) {
			// Handle @RunWith(Suite.class) migration
			newAnnotation= ast.newMarkerAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_SUITE));
			// Add new import FIRST, then remove old ones
			importRewriter.addImport(ORG_JUNIT_JUPITER_SUITE);
			importRewriter.removeImport(ORG_JUNIT_SUITE);
			importRewriter.removeImport(ORG_JUNIT_RUNWITH);
		} else if (ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER.equals(junitHolder.value)) {
			// Handle @RunWith(MockitoJUnitRunner.class) migration
			SingleMemberAnnotation extendWithAnnotation= ast.newSingleMemberAnnotation();
			extendWithAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXTEND_WITH));
			TypeLiteral typeLiteral= ast.newTypeLiteral();
			typeLiteral.setType(ast.newSimpleType(ast.newName(MOCKITO_EXTENSION)));
			extendWithAnnotation.setValue(typeLiteral);
			newAnnotation= extendWithAnnotation;
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
			importRewriter.addImport(ORG_MOCKITO_JUNIT_JUPITER_MOCKITO_EXTENSION);
			importRewriter.removeImport(ORG_MOCKITO_JUNIT_MOCKITO_JUNIT_RUNNER);
			importRewriter.removeImport(ORG_MOCKITO_RUNNERS_MOCKITO_JUNIT_RUNNER);
		} else if (ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER.equals(junitHolder.value)) {
			// Handle @RunWith(SpringRunner.class) migration
			SingleMemberAnnotation extendWithAnnotation= ast.newSingleMemberAnnotation();
			extendWithAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_EXTEND_WITH));
			TypeLiteral typeLiteral= ast.newTypeLiteral();
			typeLiteral.setType(ast.newSimpleType(ast.newName(SPRING_EXTENSION)));
			extendWithAnnotation.setValue(typeLiteral);
			newAnnotation= extendWithAnnotation;
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_EXTENSION_EXTEND_WITH);
			importRewriter.addImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT_JUPITER_SPRING_EXTENSION);
			importRewriter.removeImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_RUNNER);
			importRewriter.removeImport(ORG_SPRINGFRAMEWORK_TEST_CONTEXT_JUNIT4_SPRING_JUNIT4_CLASS_RUNNER);
		}
		
		if (newAnnotation != null) {
			ASTNodes.replaceButKeepComment(rewriter, minv, newAnnotation, group);
			importRewriter.removeImport(ORG_JUNIT_RUNWITH);
		}
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Suite
					@SelectClasses({
						MyTest.class
					})
					"""; //$NON-NLS-1$
		}
		return """
				@RunWith(Suite.class)
				@Suite.SuiteClasses({
					MyTest.class
				})
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RunWith"; //$NON-NLS-1$
	}
}
