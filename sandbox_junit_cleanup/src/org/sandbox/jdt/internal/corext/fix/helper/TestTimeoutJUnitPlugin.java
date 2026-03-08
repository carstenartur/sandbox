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

/**
 * Plugin to migrate JUnit 4 @Test(timeout=...) to JUnit 5 @Timeout.
 * 
 * @since 1.3.0
 */
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
			if ("timeout".equals(pair.getName().getIdentifier())) { //$NON-NLS-1$
				timeoutPair = pair;
				break;
			}
		}
		if (timeoutPair == null) {
			return null;
		}
		Expression value = timeoutPair.getValue();
		if (!(value instanceof NumberLiteral)) {
			return null;
		}
		try {
			Long.parseLong(((NumberLiteral) value).getToken());
		} catch (NumberFormatException e) {
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

		if (timeoutPair == null) {
			return;
		}

		Expression timeoutValue = timeoutPair.getValue();
		if (!(timeoutValue instanceof NumberLiteral)) {
			return;
		}

		final long timeoutMillis;
		try {
			timeoutMillis = Long.parseLong(((NumberLiteral) timeoutValue).getToken());
		} catch (NumberFormatException e) {
			// Malformed timeout value, skip refactoring for this method
			return;
		}

		// Determine the best time unit (optimize for readability)
		// Use SECONDS if the value is >= 1000ms and evenly divisible by 1000
		// This makes the timeout more readable (e.g., "5 seconds" vs "5000
		// milliseconds")
		long timeout;
		String timeUnit;
		if (timeoutMillis % 1000 == 0 && timeoutMillis >= 1000) {
			// Use SECONDS for better readability (e.g., 1 second instead of 1000
			// milliseconds)
			timeout = timeoutMillis / 1000;
			timeUnit = "SECONDS";
		} else {
			// Use MILLISECONDS for values < 1000ms or not evenly divisible by 1000
			timeout = timeoutMillis;
			timeUnit = "MILLISECONDS";
		}

		// Create @Timeout annotation
		NormalAnnotation timeoutAnnotation = ast.newNormalAnnotation();
		timeoutAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TIMEOUT));

		// Add value parameter
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value"));
		valuePair.setValue(ast.newNumberLiteral(String.valueOf(timeout)));
		timeoutAnnotation.values().add(valuePair);

		// Add unit parameter
		MemberValuePair unitPair = ast.newMemberValuePair();
		unitPair.setName(ast.newSimpleName("unit"));
		QualifiedName timeUnitName = ast.newQualifiedName(ast.newSimpleName("TimeUnit"), ast.newSimpleName(timeUnit));
		unitPair.setValue(timeUnitName);
		timeoutAnnotation.values().add(unitPair);

		// Add the @Timeout annotation to the method (after @Test)
		MethodDeclaration method = ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		if (method != null) {
			ListRewrite listRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
			listRewrite.insertAfter(timeoutAnnotation, testAnnotation, group);
		}

		// Remove the timeout parameter from @Test annotation
		// If the timeout is the only parameter, replace the NormalAnnotation with a
		// MarkerAnnotation
		// to avoid leaving an empty @Test() annotation.
		List<MemberValuePair> testValues = testAnnotation.values();
		if (testValues.size() == 1 && testValues.get(0) == timeoutPair) {
			MarkerAnnotation markerTestAnnotation = AnnotationUtils.createMarkerAnnotation(ast, ANNOTATION_TEST);
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			rewriter.remove(timeoutPair, group);
		}

		// Add imports - order matters: remove old import first, then add new imports
		importRewriter.removeImport(ORG_JUNIT_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TIMEOUT);
		importRewriter.addImport("java.util.concurrent.TimeUnit");
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
