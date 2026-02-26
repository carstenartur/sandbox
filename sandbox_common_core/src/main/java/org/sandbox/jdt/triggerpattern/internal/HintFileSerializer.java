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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Serializes a {@link HintFile} back to the {@code .sandbox-hint} DSL text format.
 * 
 * <p>This is the inverse of {@link HintFileParser}. Given a populated {@code HintFile}
 * model, it produces a textual representation that can be written to a
 * {@code .sandbox-hint} file and subsequently re-parsed by {@link HintFileParser}.</p>
 * 
 * <p>Metadata directives are emitted for non-null/non-default fields. Rules are
 * written with their source pattern, optional guard, rewrite alternatives, and
 * the {@code ;;} terminator.</p>
 * 
 * @since 1.3.2
 */
public final class HintFileSerializer {
	
	private static final String LINE_SEP = "\n"; //$NON-NLS-1$
	
	/**
	 * Serializes the given {@link HintFile} to {@code .sandbox-hint} DSL text.
	 * 
	 * @param hintFile the hint file to serialize (must not be {@code null})
	 * @return the serialized DSL text
	 */
	public String serialize(HintFile hintFile) {
		Objects.requireNonNull(hintFile, "hintFile must not be null"); //$NON-NLS-1$
		
		StringBuilder sb = new StringBuilder();
		
		appendMetadata(sb, hintFile);
		appendRules(sb, hintFile.getRules());
		
		return sb.toString();
	}
	
	/**
	 * Appends metadata directives for non-null/non-default fields.
	 */
	private void appendMetadata(StringBuilder sb, HintFile hintFile) {
		if (hintFile.getId() != null) {
			sb.append("<!id: ").append(hintFile.getId()).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		if (hintFile.getDescription() != null) {
			sb.append("<!description: ").append(hintFile.getDescription()).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		if (!"info".equals(hintFile.getSeverityAsString())) { //$NON-NLS-1$
			sb.append("<!severity: ").append(hintFile.getSeverityAsString()).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		if (hintFile.getMinJavaVersion() > 0) {
			sb.append("<!minJavaVersion: ").append(hintFile.getMinJavaVersion()).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		if (!hintFile.getTags().isEmpty()) {
			StringJoiner joiner = new StringJoiner(", "); //$NON-NLS-1$
			for (String tag : hintFile.getTags()) {
				joiner.add(tag);
			}
			sb.append("<!tags: ").append(joiner).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		for (String include : hintFile.getIncludes()) {
			sb.append("<!include: ").append(include).append('>').append(LINE_SEP); //$NON-NLS-1$
		}
		if (hintFile.isCaseInsensitive()) {
			sb.append("<!caseInsensitive>").append(LINE_SEP); //$NON-NLS-1$
		}
	}
	
	/**
	 * Appends all transformation rules.
	 */
	private void appendRules(StringBuilder sb, List<TransformationRule> rules) {
		for (TransformationRule rule : rules) {
			if (sb.length() > 0) {
				sb.append(LINE_SEP);
			}
			appendRule(sb, rule);
		}
	}
	
	/**
	 * Appends a single transformation rule in DSL format.
	 */
	private void appendRule(StringBuilder sb, TransformationRule rule) {
		// Optional description prefix
		if (rule.getDescription() != null) {
			sb.append('"').append(rule.getDescription()).append("\":").append(LINE_SEP); //$NON-NLS-1$
		}
		
		// Source pattern with optional guard
		sb.append(rule.sourcePattern().getValue());
		if (rule.sourceGuard() != null) {
			sb.append(" :: ").append(formatGuard(rule.sourceGuard())); //$NON-NLS-1$
		}
		sb.append(LINE_SEP);
		
		// Rewrite alternatives
		for (RewriteAlternative alt : rule.alternatives()) {
			sb.append("=> ").append(alt.replacementPattern()); //$NON-NLS-1$
			if (!alt.isOtherwise()) {
				sb.append(" :: ").append(formatGuard(alt.condition())); //$NON-NLS-1$
			}
			sb.append(LINE_SEP);
		}
		
		// Terminator
		sb.append(";;").append(LINE_SEP); //$NON-NLS-1$
	}
	
	/**
	 * Formats a {@link GuardExpression} back to DSL text.
	 * 
	 * @param guard the guard expression
	 * @return the DSL representation
	 */
	private static String formatGuard(GuardExpression guard) {
		return switch (guard) {
			case GuardExpression.FunctionCall fc -> formatFunctionCall(fc);
			case GuardExpression.And and -> formatGuard(and.left()) + " && " + formatGuard(and.right()); //$NON-NLS-1$
			case GuardExpression.Or or -> formatGuard(or.left()) + " || " + formatGuard(or.right()); //$NON-NLS-1$
			case GuardExpression.Not not -> "!" + formatGuard(not.operand()); //$NON-NLS-1$
		};
	}
	
	/**
	 * Formats a {@link GuardExpression.FunctionCall}, handling the special
	 * {@code instanceof} syntax.
	 */
	private static String formatFunctionCall(GuardExpression.FunctionCall fc) {
		if ("instanceof".equals(fc.name()) && fc.args().size() == 2) { //$NON-NLS-1$
			return fc.args().get(0) + " instanceof " + fc.args().get(1); //$NON-NLS-1$
		}
		if (fc.args().isEmpty()) {
			return fc.name() + "()"; //$NON-NLS-1$
		}
		StringJoiner joiner = new StringJoiner(", "); //$NON-NLS-1$
		for (String arg : fc.args()) {
			joiner.add(arg);
		}
		return fc.name() + "(" + joiner + ")"; //$NON-NLS-1$ //$NON-NLS-2$
	}
}
