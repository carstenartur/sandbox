/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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

import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.HelperVisitorFactory;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @FixMethodOrder annotations to JUnit 5 @TestMethodOrder.
 * 
 * Handles:
 * - @FixMethodOrder(MethodSorters.NAME_ASCENDING) → @TestMethodOrder(MethodOrderer.MethodName.class)
 * - @FixMethodOrder(MethodSorters.JVM) → @TestMethodOrder(MethodOrderer.Random.class)
 * - @FixMethodOrder(MethodSorters.DEFAULT) → Remove annotation (JUnit 5 default behavior)
 */
public class FixMethodOrderJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= ReferenceHolder.createIndexed();
		
		// Use Fluent API to find @FixMethodOrder annotations
		HelperVisitorFactory.forAnnotation(ORG_JUNIT_FIX_METHOD_ORDER)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof SingleMemberAnnotation) {
					return processFoundNode(fixcore, operations, (SingleMemberAnnotation) visited, aholder);
				}
				return true;
			});
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, SingleMemberAnnotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		
		// Extract MethodSorter value from the annotation
		Expression value= node.getValue();
		if (value instanceof QualifiedName qn) {
			String methodSorter= qn.getName().getIdentifier(); // "NAME_ASCENDING", "JVM", "DEFAULT"
			mh.setAdditionalInfo(methodSorter);
			
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		// If value is not a QualifiedName, skip this annotation (invalid format)
		
		// Return true to continue processing other @FixMethodOrder annotations
		return true;
	}
	
	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		
		Annotation oldAnnotation= junitHolder.getAnnotation();
		String methodSorter= (String) junitHolder.getAdditionalInfo();
		
		// Validate methodSorter is not null
		if (methodSorter == null) {
			// Invalid or unsupported format, just remove the annotation
			rewriter.remove(oldAnnotation, group);
			importRewriter.removeImport(ORG_JUNIT_FIX_METHOD_ORDER);
			importRewriter.removeImport(ORG_JUNIT_RUNNERS_METHOD_SORTERS);
			return;
		}
		
		if ("DEFAULT".equals(methodSorter)) {
			// DEFAULT: Simply remove the annotation (JUnit 5 has no explicit default)
			rewriter.remove(oldAnnotation, group);
		} else if ("NAME_ASCENDING".equals(methodSorter) || "JVM".equals(methodSorter)) {
			// NAME_ASCENDING or JVM: Create new @TestMethodOrder annotation
			SingleMemberAnnotation newAnnotation= ast.newSingleMemberAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST_METHOD_ORDER));
			
			// Create TypeLiteral for MethodOrderer.MethodName.class or MethodOrderer.Random.class
			org.eclipse.jdt.core.dom.TypeLiteral typeLiteral= ast.newTypeLiteral();
			
			if ("NAME_ASCENDING".equals(methodSorter)) {
				// MethodOrderer.MethodName.class
				typeLiteral.setType(ast.newSimpleType(ast.newQualifiedName(
					ast.newSimpleName("MethodOrderer"),
					ast.newSimpleName("MethodName")
				)));
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_METHOD_ORDERER);
			} else { // "JVM"
				// MethodOrderer.Random.class
				typeLiteral.setType(ast.newSimpleType(ast.newQualifiedName(
					ast.newSimpleName("MethodOrderer"),
					ast.newSimpleName("Random")
				)));
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_METHOD_ORDERER);
			}
			
			newAnnotation.setValue(typeLiteral);
			
			// Replace old annotation with new one
			rewriter.replace(oldAnnotation, newAnnotation, group);
			importRewriter.addImport(ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER);
		} else {
			// Unrecognized methodSorter value, just remove the annotation
			rewriter.remove(oldAnnotation, group);
		}
		
		// Remove old imports
		importRewriter.removeImport(ORG_JUNIT_FIX_METHOD_ORDER);
		importRewriter.removeImport(ORG_JUNIT_RUNNERS_METHOD_SORTERS);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.TestMethodOrder;
					import org.junit.jupiter.api.MethodOrderer;
					
					@TestMethodOrder(MethodOrderer.MethodName.class)
					public class MyTest {
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.FixMethodOrder;
				import org.junit.runners.MethodSorters;
				
				@FixMethodOrder(MethodSorters.NAME_ASCENDING)
				public class MyTest {
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "FixMethodOrder"; //$NON-NLS-1$
	}
}
