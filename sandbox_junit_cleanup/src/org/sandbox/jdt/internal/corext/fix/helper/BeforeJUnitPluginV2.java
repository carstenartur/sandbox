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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 @Before annotations to JUnit 5 @BeforeEach.
 * Uses TriggerPattern for declarative pattern matching.
 */
public class BeforeJUnitPluginV2 extends TriggerPatternCleanupPlugin {

	@Override
	protected List<Pattern> getPatterns() {
		return List.of(
			new Pattern("@Before", PatternKind.ANNOTATION, ORG_JUNIT_BEFORE)
		);
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
			ImportRewrite importRewriter, JunitHolder junitHolder) {
		Annotation annotation = junitHolder.getAnnotation();
		MarkerAnnotation newAnnotation = ast.newMarkerAnnotation();
		newAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_BEFORE_EACH));
		ASTNodes.replaceButKeepComment(rewriter, annotation, newAnnotation, group);
		importRewriter.removeImport(ORG_JUNIT_BEFORE);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_BEFORE_EACH);
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
				@BeforeEach
				public static void setUp() throws Exception {
				}
				"""; //$NON-NLS-1$
		}
		return """
			@Before
			public static void setUp() throws Exception {
			}
			"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "Before (TriggerPattern)"; //$NON-NLS-1$
	}
}
