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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
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
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 @Category annotations to JUnit 5 @Tag annotations.
 * 
 * <p>Uses TriggerPattern-based declarative architecture with @CleanupPattern for
 * detection. The transformation logic remains custom because one @Category
 * annotation maps to multiple @Tag annotations — too complex for @RewriteRule.</p>
 * 
 * <p>Handles:</p>
 * <ul>
 *   <li>Single category: @Category(FastTests.class) → @Tag("FastTests")</li>
 *   <li>Multiple categories: @Category({Fast.class, Slow.class}) → @Tag("Fast") @Tag("Slow")</li>
 *   <li>Categories on both class and method level</li>
 * </ul>
 * 
 * @since 1.3.0
 */
@CleanupPattern(value = "@Category($value)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_EXPERIMENTAL_CATEGORIES_CATEGORY, cleanupId = "cleanup.junit.category", description = "Migrate @Category to @Tag", displayName = "JUnit 4 @Category → JUnit 5 @Tag")
public class CategoryJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		JunitHolder holder = super.createHolder(match);
		Annotation node = holder.getAnnotation();

		if (node instanceof SingleMemberAnnotation mynode) {
			Expression value = mynode.getValue();
			List<String> categoryNames = extractCategoryNames(value);
			if (!categoryNames.isEmpty()) {
				holder.setValue(String.join(",", categoryNames)); //$NON-NLS-1$
				return holder;
			}
		}

		// Cannot extract category names, skip
		return null;
	}

	/**
	 * Extracts category class names from the annotation value. Handles both single
	 * category (TypeLiteral) and multiple categories (ArrayInitializer).
	 */
	private List<String> extractCategoryNames(Expression value) {
		List<String> categoryNames = new ArrayList<>();

		if (value instanceof TypeLiteral typeLiteral) {
			// Single category: @Category(FastTests.class)
			String className = extractSimpleClassName(typeLiteral);
			if (className != null) {
				categoryNames.add(className);
			}
		} else if (value instanceof ArrayInitializer arrayInit) {
			// Multiple categories: @Category({Fast.class, Slow.class})
			List<Expression> expressions = arrayInit.expressions();
			for (Expression expr : expressions) {
				if (expr instanceof TypeLiteral typeLiteral) {
					String className = extractSimpleClassName(typeLiteral);
					if (className != null) {
						categoryNames.add(className);
					}
				}
			}
		}

		return categoryNames;
	}

	/**
	 * Extracts the simple class name from a TypeLiteral. For example,
	 * FastTests.class → "FastTests"
	 */
	private String extractSimpleClassName(TypeLiteral typeLiteral) {
		Type type = typeLiteral.getType();
		if (type != null) {
			String typeName = type.toString();
			// Get simple name (remove package if present)
			int lastDot = typeName.lastIndexOf('.');
			return lastDot >= 0 ? typeName.substring(lastDot + 1) : typeName;
		}
		return null;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		Annotation minv = junitHolder.getAnnotation();
		String[] categoryNames = junitHolder.getValue().split(",");

		// Determine if annotation is on a method or class and get the appropriate
		// ListRewrite
		ListRewrite listRewrite = null;
		MethodDeclaration method = ASTNodes.getParent(minv, MethodDeclaration.class);
		TypeDeclaration type = ASTNodes.getParent(minv, TypeDeclaration.class);

		if (method != null) {
			listRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
		} else if (type != null) {
			listRewrite = rewriter.getListRewrite(type, TypeDeclaration.MODIFIERS2_PROPERTY);
		}

		if (listRewrite != null) {
			// Create @Tag annotation for each category
			for (String categoryName : categoryNames) {
				SingleMemberAnnotation tagAnnotation = ast.newSingleMemberAnnotation();
				tagAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TAG));

				StringLiteral tagValue = ast.newStringLiteral();
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
