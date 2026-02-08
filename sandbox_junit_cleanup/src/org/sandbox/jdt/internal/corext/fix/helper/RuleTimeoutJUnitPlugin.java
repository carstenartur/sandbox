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
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.corext.fix.helper.lib.JunitHolder;
import org.sandbox.jdt.internal.corext.fix.helper.lib.TriggerPatternCleanupPlugin;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/**
 * Plugin to migrate JUnit 4 @Rule Timeout to JUnit 5 @Timeout at class level.
 * 
 * <p>This plugin uses a hybrid approach combining TriggerPattern detection with custom transformation logic:</p>
 * <ul>
 *   <li>TriggerPattern detects @Rule and @ClassRule Timeout fields declaratively</li>
 *   <li>Custom process2Rewrite() handles complex transformation (extracting timeout values, adding class annotation)</li>
 * </ul>
 */
@CleanupPattern(
    value = "@Rule public Timeout $name",
    kind = PatternKind.FIELD,
    qualifiedType = ORG_JUNIT_RULES_TIMEOUT,
    cleanupId = "cleanup.junit.ruletimeout",
    description = "Migrate @Rule Timeout to @Timeout class annotation"
)
public class RuleTimeoutJUnitPlugin extends TriggerPatternCleanupPlugin {

	/**
	 * Override getPatterns() to match both @Rule and @ClassRule variants.
	 * Cannot be expressed with a single pattern, so we provide multiple patterns.
	 */
	@Override
	protected List<Pattern> getPatterns() {
		return List.of(
			new Pattern("@Rule public Timeout $name", PatternKind.FIELD, ORG_JUNIT_RULES_TIMEOUT),
			new Pattern("@ClassRule public Timeout $name", PatternKind.FIELD, ORG_JUNIT_RULES_TIMEOUT),
			// Also match static fields for @ClassRule (these may have static modifier)
			new Pattern("@ClassRule public static Timeout $name", PatternKind.FIELD, ORG_JUNIT_RULES_TIMEOUT)
		);
	}

	/**
	 * Creates a JunitHolder from the matched field, extracting timeout information.
	 */
	@Override
	protected JunitHolder createHolder(Match match) {
		FieldDeclaration fieldDecl = (FieldDeclaration) match.getMatchedNode();
		
		// Get the first fragment (multiple timeout fields in one declaration are not supported)
		VariableDeclarationFragment fragment = (VariableDeclarationFragment) fieldDecl.fragments().get(0);
		if (fragment.resolveBinding() == null) {
			return null; // Skip if binding is unavailable
		}
		
		ITypeBinding binding = fragment.resolveBinding().getType();
		if (binding == null || !ORG_JUNIT_RULES_TIMEOUT.equals(binding.getQualifiedName())) {
			return null; // Type doesn't match
		}
		
		Expression initializer = fragment.getInitializer();
		if (initializer == null) {
			return null; // No initializer to extract timeout from
		}
		
		TimeoutInfo info = extractTimeoutInfo(initializer);
		if (info == null) {
			return null; // Cannot determine timeout value
		}
		
		JunitHolder holder = new JunitHolder();
		holder.setMinv(fieldDecl);
		holder.setValue(String.valueOf(info.value));
		holder.setMinvname(info.unit);
		return holder;
	}

	/**
	 * Extract timeout value and unit from various patterns:
	 * - Timeout.seconds(10) -> value=10, unit=SECONDS
	 * - Timeout.millis(1000) -> value=1000, unit=MILLISECONDS
	 * - new Timeout(1000) -> value=1000, unit=MILLISECONDS
	 * - new Timeout(1, TimeUnit.SECONDS) -> value=1, unit=SECONDS
	 */
	private TimeoutInfo extractTimeoutInfo(Expression initializer) {
		if (initializer instanceof MethodInvocation) {
			MethodInvocation mi = (MethodInvocation) initializer;
			String methodName = mi.getName().getIdentifier();
			
			List<Expression> args = mi.arguments();
			if (args.size() == 1 && args.get(0) instanceof NumberLiteral) {
				long value = parseLong((NumberLiteral) args.get(0));
				if ("seconds".equals(methodName)) {
					return new TimeoutInfo(value, "SECONDS");
				} else if ("millis".equals(methodName)) {
					return new TimeoutInfo(value, "MILLISECONDS");
				}
			}
		} else if (initializer instanceof ClassInstanceCreation) {
			ClassInstanceCreation cic = (ClassInstanceCreation) initializer;
			List<Expression> args = cic.arguments();
			
			if (args.size() == 1 && args.get(0) instanceof NumberLiteral) {
				// new Timeout(n) - milliseconds
				long value = parseLong((NumberLiteral) args.get(0));
				return new TimeoutInfo(value, "MILLISECONDS");
			} else if (args.size() == 2) {
				// new Timeout(n, TimeUnit.X)
				Expression arg0 = args.get(0);
				Expression arg1 = args.get(1);
				
				if (arg0 instanceof NumberLiteral) {
					long value = parseLong((NumberLiteral) arg0);
					String unit = extractTimeUnit(arg1);
					if (unit != null) {
						return new TimeoutInfo(value, unit);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Extract TimeUnit constant from expression like TimeUnit.SECONDS
	 */
	private String extractTimeUnit(Expression expr) {
		if (expr instanceof QualifiedName) {
			QualifiedName qn = (QualifiedName) expr;
			// Prefer type binding to reliably detect java.util.concurrent.TimeUnit
			ITypeBinding qualifierBinding = qn.getQualifier().resolveTypeBinding();
			if (qualifierBinding != null) {
				String qualifiedName = qualifierBinding.getQualifiedName();
				if ("java.util.concurrent.TimeUnit".equals(qualifiedName)) {
					return qn.getName().getIdentifier();
				}
			} else if (qn.getQualifier() instanceof SimpleName) {
				// Fallback when bindings are unavailable: check simple qualifier name
				SimpleName qualifier = (SimpleName) qn.getQualifier();
				if ("TimeUnit".equals(qualifier.getIdentifier())) {
					return qn.getName().getIdentifier();
				}
			}
		} else if (expr instanceof SimpleName) {
			// Handle imported static TimeUnit constants
			SimpleName sn = (SimpleName) expr;
			return sn.getIdentifier();
		}
		return null;
	}

	private long parseLong(NumberLiteral literal) {
		try {
			return Long.parseLong(literal.getToken());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	protected void process2Rewrite(TextEditGroup group, ASTRewrite rewriter, AST ast, ImportRewrite importRewriter,
			JunitHolder junitHolder) {
		FieldDeclaration fieldDecl = junitHolder.getFieldDeclaration();
		
		// Parse timeout info from holder
		long timeoutValue;
		String timeUnit;
		try {
			timeoutValue = Long.parseLong(junitHolder.getValue());
			timeUnit = junitHolder.getMinvname();
		} catch (NumberFormatException e) {
			// Cannot determine timeout value, skip refactoring
			return;
		}
		
		// Find the containing class
		TypeDeclaration typeDecl = ASTNodes.getParent(fieldDecl, TypeDeclaration.class);
		if (typeDecl == null) {
			return;
		}
		
		// Create @Timeout annotation
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
		
		// Add @Timeout annotation to class
		ListRewrite listRewrite = rewriter.getListRewrite(typeDecl, TypeDeclaration.MODIFIERS2_PROPERTY);
		listRewrite.insertFirst(timeoutAnnotation, group);
		
		// Remove the @Rule Timeout field
		rewriter.remove(fieldDecl, group);
		
		// Update imports
		importRewriter.removeImport(ORG_JUNIT_RULE);
		importRewriter.removeImport(ORG_JUNIT_CLASS_RULE);
		importRewriter.removeImport(ORG_JUNIT_RULES_TIMEOUT);
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
					
					@Timeout(value = 10, unit = TimeUnit.SECONDS)
					public class MyTest {
						@Test
						public void test1() {
							// test code
						}
					}
					"""; //$NON-NLS-1$
		}
		return """
				import org.junit.Rule;
				import org.junit.Test;
				import org.junit.rules.Timeout;
				
				public class MyTest {
					@Rule
					public Timeout globalTimeout = Timeout.seconds(10);
					
					@Test
					public void test1() {
						// test code
					}
				}
				"""; //$NON-NLS-1$
	}

	@Override
	public String toString() {
		return "RuleTimeout"; //$NON-NLS-1$
	}

	/**
	 * Helper class to hold timeout information
	 */
	private static class TimeoutInfo {
		final long value;
		final String unit;

		TimeoutInfo(long value, String unit) {
			this.value = value;
			this.unit = unit;
		}
	}
}
