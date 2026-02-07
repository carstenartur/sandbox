/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
 * Copyright (C) 2025 hammer
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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.HelperVisitor;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JUnitCleanUpFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.lib.AbstractTool;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;

/**
 * Plugin to migrate JUnit 4 @Category annotations to JUnit 5 @Tag annotations.
 * 
 * Handles:
 * - Single category: @Category(FastTests.class) → @Tag("FastTests")
 * - Multiple categories: @Category({Fast.class, Slow.class}) → @Tag("Fast") @Tag("Slow")
 * - Categories on both class and method level
 */
public class CategoryJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder= new ReferenceHolder<>();
		HelperVisitor.forAnnotation(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORY)
			.in(compilationUnit)
			.excluding(nodesprocessed)
			.processEach(dataHolder, (visited, aholder) -> {
				if (visited instanceof Annotation) {
					return processFoundNode(fixcore, operations, (Annotation) visited, aholder);
				}
				return true;
			});
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Annotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		JunitHolder mh= new JunitHolder();
		mh.setMinv(node);
		mh.setMinvname(node.getTypeName().getFullyQualifiedName());
		
		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value= mynode.getValue();
			List<String> categoryNames= extractCategoryNames(value);
			if (!categoryNames.isEmpty()) {
				mh.setValue(String.join(",", categoryNames));
				dataHolder.put(dataHolder.size(), mh);
				operations.add(fixcore.rewrite(dataHolder));
			}
		}
		// Return true to continue processing other @Category annotations
		// (the fluent API interprets false as "stop all processing")
		return true;
	}

	/**
	 * Extracts category class names from the annotation value.
	 * Handles both single category (TypeLiteral) and multiple categories (ArrayInitializer).
	 */
	private List<String> extractCategoryNames(Expression value) {
		List<String> categoryNames= new ArrayList<>();
		
		if (value instanceof TypeLiteral typeLiteral) {
			// Single category: @Category(FastTests.class)
			String className= extractSimpleClassName(typeLiteral);
			if (className != null) {
				categoryNames.add(className);
			}
		} else if (value instanceof ArrayInitializer arrayInit) {
			// Multiple categories: @Category({Fast.class, Slow.class})
			@SuppressWarnings("unchecked")
			List<Expression> expressions= arrayInit.expressions();
			for (Expression expr : expressions) {
				if (expr instanceof TypeLiteral typeLiteral) {
					String className= extractSimpleClassName(typeLiteral);
					if (className != null) {
						categoryNames.add(className);
					}
				}
			}
		}
		
		return categoryNames;
	}

	/**
	 * Extracts the simple class name from a TypeLiteral.
	 * For example, FastTests.class → "FastTests"
	 */
	private String extractSimpleClassName(TypeLiteral typeLiteral) {
		Type type= typeLiteral.getType();
		if (type != null) {
			String typeName= type.toString();
			// Get simple name (remove package if present)
			int lastDot= typeName.lastIndexOf('.');
			return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
		}
		return null;
	}

	@Override
	protected
	void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv= junitHolder.getAnnotation();
		String[] categoryNames= junitHolder.getValue().split(",");
		
		// Determine if annotation is on a method or class and get the appropriate ListRewrite
		ListRewrite listRewrite= null;
		MethodDeclaration method= ASTNodes.getParent(minv, MethodDeclaration.class);
		TypeDeclaration type= ASTNodes.getParent(minv, TypeDeclaration.class);
		
		if (method != null) {
			listRewrite= rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		} else if (type != null) {
			listRewrite= rewriter.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		}
		
		if (listRewrite != null) {
			// Create @Tag annotation for each category
			for (String categoryName : categoryNames) {
				SingleMemberAnnotation tagAnnotation= ast.newSingleMemberAnnotation();
				tagAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TAG));
				
				StringLiteral tagValue= ast.newStringLiteral();
				tagValue.setLiteralValue(categoryName);
				tagAnnotation.setValue(tagValue);
				
				// Insert the new annotation before the original one
				listRewrite.insertBefore(tagAnnotation, minv, group);
			}
			
			// Remove the original @Category annotation
			listRewrite.remove(minv, group);
		}
		
		// Update imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TAG);
		importRewriter.removeImport(ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORY);
	}
	
	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					@Tag("FastTests")
					@Test
					public void fastTest() {
						// test code
					}
					"""; //$NON-NLS-1$
		}
		return """
				@Category(FastTests.class)
				@Test
				public void fastTest() {
					// test code
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Category"; //$NON-NLS-1$
	}
}
