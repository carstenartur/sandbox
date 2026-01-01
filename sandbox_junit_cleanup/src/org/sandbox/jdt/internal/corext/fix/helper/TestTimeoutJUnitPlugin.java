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
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
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
 * Plugin to migrate JUnit 4 @Test(timeout=...) to JUnit 5 @Timeout.
 */
public class TestTimeoutJUnitPlugin extends AbstractTool<ReferenceHolder<Integer, JunitHolder>> {

	@Override
	public void find(JUnitCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		ReferenceHolder<Integer, JunitHolder> dataHolder = new ReferenceHolder<>();
		HelperVisitor.callNormalAnnotationVisitor(ORG_JUNIT_TEST, compilationUnit, dataHolder, nodesprocessed,
				(visited, aholder) -> processFoundNode(fixcore, operations, visited, aholder));
	}

	private boolean processFoundNode(JUnitCleanUpFixCore fixcore,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, NormalAnnotation node,
			ReferenceHolder<Integer, JunitHolder> dataHolder) {
		
		// Check if this @Test annotation has a timeout parameter
		Long timeoutValue = null;
		MemberValuePair timeoutPair = null;
		
		@SuppressWarnings("unchecked")
		List<MemberValuePair> values = node.values();
		for (MemberValuePair pair : values) {
			if ("timeout".equals(pair.getName().getIdentifier())) {
				timeoutPair = pair;
				Expression value = pair.getValue();
				if (value instanceof NumberLiteral) {
					try {
						timeoutValue = Long.parseLong(((NumberLiteral) value).getToken());
					} catch (NumberFormatException e) {
						// Skip invalid timeout values
						return false;
					}
				} else {
					// Timeout value is not a simple number literal (could be a constant or expression)
					// Skip this case as it requires more complex analysis
					return false;
				}
				break;
			}
		}
		
		// Only process if we found a timeout parameter with a valid numeric value
		if (timeoutValue != null && timeoutPair != null) {
			JunitHolder mh = new JunitHolder();
			mh.minv = node;
			mh.minvname = node.getTypeName().getFullyQualifiedName();
			mh.value = String.valueOf(timeoutValue);
			mh.additionalInfo = timeoutPair; // Store the timeout pair for removal
			dataHolder.put(dataHolder.size(), mh);
			operations.add(fixcore.rewrite(dataHolder));
		}
		
		return false;
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		NormalAnnotation testAnnotation = (NormalAnnotation) junitHolder.getAnnotation();
		MemberValuePair timeoutPair = (MemberValuePair) junitHolder.additionalInfo;
		
		String timeoutString = junitHolder.value;
		if (timeoutString == null) {
			// Cannot determine timeout value, skip refactoring for this method
			return;
		}
		
		final long timeoutMillis;
		try {
			timeoutMillis = Long.parseLong(timeoutString);
		} catch (NumberFormatException e) {
			// Malformed timeout value, skip refactoring for this method
			return;
		}
		
		// Determine the best time unit (optimize for readability)
		// Use SECONDS if the value is >= 1000ms and evenly divisible by 1000
		// This makes the timeout more readable (e.g., "5 seconds" vs "5000 milliseconds")
		long timeoutValue;
		String timeUnit;
		if (timeoutMillis % 1000 == 0 && timeoutMillis >= 1000) {
			// Use SECONDS for better readability (e.g., 1 second instead of 1000 milliseconds)
			timeoutValue = timeoutMillis / 1000;
			timeUnit = "SECONDS";
		} else {
			// Use MILLISECONDS for values < 1000ms or not evenly divisible by 1000
			timeoutValue = timeoutMillis;
			timeUnit = "MILLISECONDS";
		}
		
		// Add @Timeout annotation
		NormalAnnotation timeoutAnnotation = ast.newNormalAnnotation();
		timeoutAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TIMEOUT));
		
		// Add value parameter
		MemberValuePair valuePair = ast.newMemberValuePair();
		valuePair.setName(ast.newSimpleName("value"));
		valuePair.setValue(ast.newNumberLiteral(String.valueOf(timeoutValue)));
		timeoutAnnotation.values().add(valuePair);
		
		// Add unit parameter
		MemberValuePair unitPair = ast.newMemberValuePair();
		unitPair.setName(ast.newSimpleName("unit"));
		QualifiedName timeUnitName = ast.newQualifiedName(
			ast.newSimpleName("TimeUnit"),
			ast.newSimpleName(timeUnit)
		);
		unitPair.setValue(timeUnitName);
		timeoutAnnotation.values().add(unitPair);
		
		// Add the @Timeout annotation to the method (after @Test)
		MethodDeclaration method = ASTNodes.getParent(testAnnotation, MethodDeclaration.class);
		if (method != null) {
			ListRewrite listRewrite = rewriter.getListRewrite(method, MethodDeclaration.MODIFIERS2_PROPERTY);
			listRewrite.insertAfter(timeoutAnnotation, testAnnotation, group);
		}
		
		// Remove the timeout parameter from @Test annotation
		// If the timeout is the only parameter, replace the NormalAnnotation with a MarkerAnnotation
		// to avoid leaving an empty @Test() annotation.
		@SuppressWarnings("unchecked")
		List<MemberValuePair> testValues = testAnnotation.values();
		if (testValues.size() == 1 && testValues.get(0) == timeoutPair) {
			MarkerAnnotation markerTestAnnotation = ast.newMarkerAnnotation();
			markerTestAnnotation.setTypeName(ast.newSimpleName(ANNOTATION_TEST));
			ASTNodes.replaceButKeepComment(rewriter, testAnnotation, markerTestAnnotation, group);
		} else {
			rewriter.remove(timeoutPair, group);
		}
		
		// Add imports
		importRewriter.addImport(ORG_JUNIT_JUPITER_TEST);
		importRewriter.addImport(ORG_JUNIT_JUPITER_API_TIMEOUT);
		importRewriter.addImport("java.util.concurrent.TimeUnit");
		importRewriter.removeImport(ORG_JUNIT_TEST);
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
