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

import java.util.List;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.util.AnnotationUtils;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Plugin to migrate JUnit 4 {@code @Test(timeout=...)} to JUnit 5 {@code @Timeout}. */
@CleanupPattern(value = "@Test(timeout=$t)", kind = PatternKind.ANNOTATION, qualifiedType = ORG_JUNIT_TEST, cleanupId = "cleanup.junit.test.timeout", description = "Migrate @Test(timeout=...) to @Timeout", displayName = "JUnit 4 @Test(timeout) → JUnit 5 @Timeout")
public class TestTimeoutJUnitPlugin extends TriggerPatternCleanupPlugin {

	@Override
	protected JunitHolder createHolder(Match match) {
		ASTNode node = match.getMatchedNode();
		if (!(node instanceof NormalAnnotation)) {
			return null;
		}
		NormalAnnotation annotation = (NormalAnnotation) node;
		MemberValuePair timeoutPair = null;
		for (Object obj : annotation.values()) {
			MemberValuePair pair = (MemberValuePair) obj;
			String name= pair.getName().getIdentifier();
			if ("expected".equals(name)) { //$NON-NLS-1$
				return null;
			}
			if ("timeout".equals(name)) { //$NON-NLS-1$
				timeoutPair = pair;
			}
		}
		if (timeoutPair == null || !(timeoutPair.getValue() instanceof NumberLiteral number)) {
			return null;
		}
		try {
			Long.parseLong(number.getToken());
		} catch (NumberFormatException exception) {
			return null;
		}
		JunitHolder holder = new JunitHolder();
		holder.setMinv(annotation);
		holder.setAdditionalInfo(timeoutPair);
		return holder;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		NormalAnnotation testAnnotation = (NormalAnnotation) junitHolder.getAnnotation();
		MemberValuePair timeoutPair = (MemberValuePair) junitHolder.getAdditionalInfo();
		if (timeoutPair == null || !(timeoutPair.getValue() instanceof NumberLiteral timeoutValue)) {
			return;
		}
		final long timeoutMillis;
		try {
			timeoutMillis = Long.parseLong(timeoutValue.getToken());
		} catch (NumberFormatException exception) {
			return;
		}
		boolean seconds = timeoutMillis % 1000 == 0 && timeoutMillis >= 1000;
		long timeout = seconds ? timeoutMillis / 1000 : timeoutMillis;
		String timeUnit = seconds ? "SECONDS" : "MILLISECONDS"; //$NON-NLS-1$ //$NON-NLS-2$
		NormalAnnotation timeoutAnnotation = ast.newNormalAnnotation();
		timeoutAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TIMEOUT));
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value")); //$NON-NLS-1$
		valuePair.setValue(ast.newNumberLiteral(String.valueOf(timeout)));
		timeoutAnnotation.values().add(valuePair);
		MemberValuePair unitPair = ast.newMemberValuePair();
		unitPair.setName(ast.newSimpleName("unit")); //$NON-NLS-1$
		QualifiedName timeUnitName = ast.newQualifiedName(ast.newSimpleName("TimeUnit"), ast.newSimpleName(timeUnit)); //$NON-NLS-1$
		unitPair.setValue(timeUnitName);
		timeoutAnnotation.values().add(unitPair);
		MethodDeclaration method = ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		if (method != null) {
			ListRewrite listRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
			listRewrite.insertAfter(timeoutAnnotation, testAnnotation, group);
		}
		List<MemberValuePair> testValues = testAnnotation.values();
		if (testValues.size() == 1 && testValues.get(0) == timeoutPair) {
			MarkerAnnotation markerTestAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_TEST);
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			rewriter.remove(timeoutPair, group);
		}
		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TIMEOUT);
		importRewriter.addImport("java.util.concurrent.TimeUnit"); //$NON-NLS-1$
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (afterRefactoring) {
			return """
					import org.junit.jupiter.api.Test;
					import org.junit.jupiter.api.Timeout;
					import java.util.concurrent.TimeUnit;

					@Test
					@Timeout(value = 1, unit = TimeUnit.SECONDS)
					public void testWithTimeout() {
						// Test code
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Test;

				@Test(timeout = 1000)
				public void testWithTimeout() {
					// Test code
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "TestTimeout"; //$NON-NLS-1$
	}
}
