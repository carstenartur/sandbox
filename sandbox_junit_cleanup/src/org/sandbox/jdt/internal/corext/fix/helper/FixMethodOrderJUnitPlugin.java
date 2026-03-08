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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 @FixMethodOrder annotations to JUnit
 * 5 @TestMethodOrder.
 * 
 * <p>Uses TriggerPattern-based declarative architecture with @CleanupPattern for
 * detection. The transformation logic remains custom because the value mapping
 * (MethodSorters → MethodOrderer) is too complex for @RewriteRule.</p>
 * 
 * <p>Handles:</p>
 * <ul>
 *   <li>@FixMethodOrder(MethodSorters.NAME_ASCENDING) → @TestMethodOrder(MethodOrderer.MethodName.class)</li>
 *   <li>@FixMethodOrder(MethodSorters.JVM) → @TestMethodOrder(MethodOrderer.Random.class)</li>
 *   <li>@FixMethodOrder(MethodSorters.DEFAULT) → Remove annotation (JUnit 5 default behavior)</li>
 * </ul>
 * 
 * @since 1.3.0
 */
@CleanupPattern(value = "@FixMethodOrder($sorter)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_FIX_METHOD_ORDER, cleanupId = "cleanup.junit.fixmethodorder", description = "Migrate @FixMethodOrder to @TestMethodOrder", displayName = "JUnit 4 @FixMethodOrder → JUnit 5 @TestMethodOrder")
public class FixMethodOrderJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		JunitHolder holder = super.createHolder(match);

		// Extract MethodSorter value from the $sorter binding
		Object sorterBinding = match.getBindings().get("$sorter"); //$NON-NLS-1$
		if (sorterBinding instanceof QualifiedName qn) {
			String methodSorter = qn.getName().getIdentifier(); // "NAME_ASCENDING", "JVM", "DEFAULT"
			holder.setAdditionalInfo(methodSorter);
			return holder;
		}

		// If value is not a QualifiedName, skip this annotation (invalid format)
		return null;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {

		Annotation oldAnnotation = junitHolder.getAnnotation();
		String methodSorter = (String) junitHolder.getAdditionalInfo();

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
			SingleMemberAnnotation newAnnotation = ast.newSingleMemberAnnotation();
			newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST_METHOD_ORDER));

			// Create TypeLiteral for MethodOrderer.MethodName.class or
			// MethodOrderer.Random.class
			org.eclipse.jdt.core.dom.TypeLiteral typeLiteral = ast.newTypeLiteral();

			if ("NAME_ASCENDING".equals(methodSorter)) {
				// MethodOrderer.MethodName.class
				typeLiteral.setType(ast.newSimpleType(
						ast.newQualifiedName(ast.newSimpleName("MethodOrderer"), ast.newSimpleName("MethodName"))));
				importRewriter.addImport(ORG_JUNIT_JUPITER_API_METHOD_ORDERER);
			} else { // "JVM"
				// MethodOrderer.Random.class
				typeLiteral.setType(ast.newSimpleType(
						ast.newQualifiedName(ast.newSimpleName("MethodOrderer"), ast.newSimpleName("Random"))));
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
