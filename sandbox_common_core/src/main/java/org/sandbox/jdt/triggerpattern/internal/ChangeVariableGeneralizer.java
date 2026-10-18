/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.BreakStatement;
import org.eclipse.jdt.core.dom.ContinueStatement;
import org.eclipse.jdt.core.dom.ExpressionMethodReference;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.LabeledStatement;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SuperMethodReference;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeMethodReference;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.sandbox.jdt.triggerpattern.internal.RuleAuthoringDocument.ChangeExample;

/**
 * Syntactic suggestions for explicit author confirmation. These are variable-shaped
 * names, not inferred bindings or a proof of an API migration's semantic scope.
 * Methods, types, member selectors, labels, comments and literals are left intact.
 */
public final class ChangeVariableGeneralizer {

	private ChangeVariableGeneralizer() {
	}

	/** Returns shared variable-shaped names; none is selected automatically. */
	public static List<String> candidates(ChangeExample example) {
		Map<String, List<SimpleName>> before = variableNames(example.before());
		before.keySet().retainAll(variableNames(example.after()).keySet());
		return List.copyOf(before.keySet());
	}

	/** Replaces only AST occurrences belonging to explicitly selected suggestions. */
	public static ChangeExample generalize(ChangeExample example, Set<String> selected) {
		if (!candidates(example).containsAll(selected)) {
			throw new IllegalArgumentException("Only suggested variable names may be generalized"); //$NON-NLS-1$
		}
		return new ChangeExample(replace(example.before(), selected), replace(example.after(), selected));
	}

	private static String replace(String code, Set<String> selected) {
		List<SimpleName> occurrences = new ArrayList<>();
		variableNames(code).forEach((name, nodes) -> {
			if (selected.contains(name)) {
				occurrences.addAll(nodes);
			}
		});
		occurrences.sort(Comparator.comparingInt(SimpleName::getStartPosition).reversed());
		StringBuilder result = new StringBuilder(code);
		for (SimpleName node : occurrences) {
			result.insert(node.getStartPosition(), '$');
		}
		return result.toString();
	}

	private static Map<String, List<SimpleName>> variableNames(String code) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setSource(code.toCharArray());
		String trimmed = code.stripTrailing();
		parser.setKind(trimmed.endsWith(";") || trimmed.endsWith("}") //$NON-NLS-1$ //$NON-NLS-2$
				? ASTParser.K_STATEMENTS : ASTParser.K_EXPRESSION);
		parser.setCompilerOptions(JavaCore.getOptions());
		ASTNode tree = parser.createAST(null);
		Map<String, List<SimpleName>> names = new TreeMap<>();
		if (tree == null || (tree.getFlags() & ASTNode.MALFORMED) != 0) {
			return names;
		}
		tree.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				String name = node.getIdentifier();
				if (Character.isLowerCase(name.codePointAt(0)) && isVariablePosition(node)) {
					names.computeIfAbsent(name, ignored -> new ArrayList<>()).add(node);
				}
				return false;
			}
		});
		return names;
	}

	private static boolean isVariablePosition(SimpleName name) {
		ASTNode parent = name.getParent();
		if (parent instanceof Name || parent instanceof Type) {
			return false;
		}
		if (name.isDeclaration()) {
			return parent instanceof VariableDeclaration;
		}
		return !(parent instanceof MethodInvocation call && call.getName() == name)
				&& !(parent instanceof SuperMethodInvocation superCall && superCall.getName() == name)
				&& !(parent instanceof FieldAccess field && field.getName() == name)
				&& !(parent instanceof SuperFieldAccess superField && superField.getName() == name)
				&& !(parent instanceof ExpressionMethodReference reference && reference.getName() == name)
				&& !(parent instanceof TypeMethodReference typeReference && typeReference.getName() == name)
				&& !(parent instanceof SuperMethodReference superReference && superReference.getName() == name)
				&& !(parent instanceof LabeledStatement label && label.getLabel() == name)
				&& !(parent instanceof BreakStatement statement && statement.getLabel() == name)
				&& !(parent instanceof ContinueStatement continuation && continuation.getLabel() == name);
	}
}
