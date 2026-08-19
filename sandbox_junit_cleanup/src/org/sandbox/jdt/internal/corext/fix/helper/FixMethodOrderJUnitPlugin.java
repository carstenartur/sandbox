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

import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_FIX_METHOD_ORDER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_METHOD_ORDERER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER;
import static org.sandbox.jdt.internal.corext.fix.helper.lib.JUnitConstants.ORG_JUNIT_RUNNERS_METHOD_SORTERS;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite.ImportRewriteContext;
import org.eclipse.jdt.internal.corext.codemanipulation.ContextSensitiveImportRewriteContext;
import org.eclipse.text.edits.TextEditGroup;

import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Migrates JUnit 4 method ordering to the corresponding Jupiter API. */
@CleanupPattern(value = "@FixMethodOrder($sorter)", kind = PatternKind.ANNOTATION,
		qualifiedType = ORG_JUNIT_FIX_METHOD_ORDER, cleanupId = "cleanup.junit.fixmethodorder",
		description = "Migrate @FixMethodOrder to @TestMethodOrder",
		displayName = "JUnit 4 @FixMethodOrder → Jupiter @TestMethodOrder")
public class FixMethodOrderJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		JunitHolder holder= super.createHolder(match);
		Object sorterBinding= match.getBindings().get("$sorter"); //$NON-NLS-1$
		if (sorterBinding instanceof QualifiedName qn) {
			holder.setAdditionalInfo(qn.getName().getIdentifier());
			return holder;
		}
		return null;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation oldAnnotation= junitHolder.getAnnotation();
		String methodSorter= (String) junitHolder.getAdditionalInfo();
		if (methodSorter == null) {
			return;
		}

		if ("DEFAULT".equals(methodSorter)) { //$NON-NLS-1$
			rewriter.remove(oldAnnotation, group);
		} else if ("NAME_ASCENDING".equals(methodSorter) || "JVM".equals(methodSorter)) { //$NON-NLS-1$ //$NON-NLS-2$
			ImportRewriteContext importContext= new ContextSensitiveImportRewriteContext(
					(CompilationUnit) oldAnnotation.getRoot(), oldAnnotation.getStartPosition(), importRewriter);
			SingleMemberAnnotation newAnnotation= ast.newSingleMemberAnnotation();
			newAnnotation.setTypeName(ast.newName(
					importRewriter.addImport(ORG_JUNIT_JUPITER_API_TEST_METHOD_ORDER, importContext)));

			Name methodOrderer= ast.newName(
					importRewriter.addImport(ORG_JUNIT_JUPITER_API_METHOD_ORDERER, importContext));
			TypeLiteral typeLiteral= ast.newTypeLiteral();
			String targetOrderer= "NAME_ASCENDING".equals(methodSorter) //$NON-NLS-1$
					? "MethodName" : "Random"; //$NON-NLS-1$ //$NON-NLS-2$
			typeLiteral.setType(ast.newSimpleType(ast.newQualifiedName(
					methodOrderer, ast.newSimpleName(targetOrderer))));
			newAnnotation.setValue(typeLiteral);
			rewriter.replace(oldAnnotation, newAnnotation, group);
		} else {
			// Unknown custom sorters are not semantics-preserving migrations.
			return;
		}

		importRewriter.removeImport(ORG_JUNIT_FIX_METHOD_ORDER);
		importRewriter.removeImport(ORG_JUNIT_RUNNERS_METHOD_SORTERS);
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
				import org.junit.FixMethodOrder;
				import org.junit.runners.MethodSorters;

				@FixMethodOrder(MethodSorters.NAME_ASCENDING)
				public class OrderedTest {
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "FixMethodOrder"; //$NON-NLS-1$
	}
}
