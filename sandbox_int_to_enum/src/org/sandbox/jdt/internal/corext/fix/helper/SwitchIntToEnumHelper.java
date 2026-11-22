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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.AstProcessing;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.IntToEnumFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper.IntConstantHolder;

/**
 * Converts an existing int switch only after the shared single-unit analysis
 * proves a private constant domain, private method, all parameter uses and callers.
 * Case bodies and fall-through order are preserved; accepted callers receive the
 * corresponding enum values together with the parameter type change.
 */
public class SwitchIntToEnumHelper extends AbstractTool<ReferenceHolder<Integer, IntConstantHolder>> {

	@Override
	public void find(IntToEnumFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {
		AstProcessing.independent().visit(SwitchStatement.class, node -> {
			if (nodesprocessed.contains(node)) {
				return;
			}
			IntConstantHolder holder = IntToEnumHelper.createSwitchCandidate(compilationUnit, node);
			if (holder == null) {
				return;
			}
			holder.nodesProcessed = nodesprocessed;
			ReferenceHolder<Integer, IntConstantHolder> dataholder = new ReferenceHolder<>();
			dataholder.put(0, holder);
			operations.add(fixcore.rewrite(dataholder));
			nodesprocessed.add(node);
		}).build(compilationUnit);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void rewrite(IntToEnumFixCore fixCore, ReferenceHolder<Integer, IntConstantHolder> holder,
			CompilationUnitRewrite cuRewrite, TextEditGroup group) {

		for (IntConstantHolder data : holder.values()) {
			if (data.switchStatement == null) {
				continue;
			}

			AST ast = cuRewrite.getRoot().getAST();
			ASTRewrite rewrite = cuRewrite.getASTRewrite();

			String prefix = findCommonPrefix(data.constantNames);
			if (prefix == null) {
				continue;
			}

			String enumName = prefixToEnumName(prefix);

			// 1. Create enum declaration
			EnumDeclaration enumDecl = ast.newEnumDeclaration();
			enumDecl.setName(ast.newSimpleName(enumName));
			Javadoc doc = ast.newJavadoc();
			TagElement tag = ast.newTagElement();
			TextElement text = ast.newTextElement();
			text.setText("Values accepted by the " + data.comparedVariable + " switch."); //$NON-NLS-1$ //$NON-NLS-2$
			tag.fragments().add(text);
			doc.tags().add(tag);
			enumDecl.setJavadoc(doc);
			enumDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PRIVATE_KEYWORD));

			for (String constName : data.constantNames) {
				EnumConstantDeclaration enumConst = ast.newEnumConstantDeclaration();
				String enumValueName = constName.substring(prefix.length());
				enumConst.setName(ast.newSimpleName(enumValueName));
				Javadoc original = data.constantFields.get(constName).getJavadoc();
				if (original != null) {
					enumConst.setJavadoc((Javadoc) ASTNode.copySubtree(ast, original));
				}
				enumDecl.enumConstants().add(enumConst);
			}

			// 2. Find the enclosing TypeDeclaration
			TypeDeclaration typeDecl = data.enclosingType;
			if (typeDecl == null) {
				continue;
			}

			ListRewrite bodyRewrite = rewrite.getListRewrite(typeDecl, TypeDeclaration.BODY_DECLARATIONS_PROPERTY);

			// 3. Insert enum before the first constant field being removed
			FieldDeclaration firstField = data.constantFields.values().iterator().next();
			bodyRewrite.insertBefore(enumDecl, firstField, group);

			// 4. Remove old int constant field declarations
			Set<FieldDeclaration> fieldsToRemove = new HashSet<>(data.constantFields.values());
			for (FieldDeclaration field : fieldsToRemove) {
				List<?> fragments = field.fragments();
				boolean allFragmentsRemoved = true;
				for (Object frag : fragments) {
					VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
					if (!data.constantFields.containsKey(vdf.getName().getIdentifier())) {
						allFragmentsRemoved = false;
						break;
					}
				}
				if (allFragmentsRemoved) {
					bodyRewrite.remove(field, group);
				} else {
					ListRewrite fragRewrite = rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY);
					for (Object frag : fragments) {
						VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
						if (data.constantFields.containsKey(vdf.getName().getIdentifier())) {
							fragRewrite.remove(vdf, group);
						}
					}
				}
			}

			// 5. Update switch case labels to enum values
			SwitchCase firstCase = null;
			boolean hasDefault = false;
			for (Object stmt : data.switchStatement.statements()) {
				if (stmt instanceof SwitchCase switchCase) {
					if (firstCase == null) {
						firstCase = switchCase;
					}
					if (switchCase.isDefault()) {
						hasDefault = true;
						continue;
					}
					for (Object expr : switchCase.expressions()) {
						if (expr instanceof SimpleName sn) {
							String constName = sn.getIdentifier();
							if (data.constantFields.containsKey(constName)) {
								String enumValueName = constName.substring(prefix.length());
								SimpleName newName = ast.newSimpleName(enumValueName);
								rewrite.replace(sn, newName, group);
							}
						}
					}
				}
			}

			// Previously an unmatched int left the switch without executing a case.
			// Insert its equivalent first so existing fall-through paths keep their targets.
			if (!hasDefault && firstCase != null) {
				SwitchCase fallback = ast.newSwitchCase();
				fallback.setSwitchLabeledRule(firstCase.isSwitchLabeledRule());
				ListRewrite statements = rewrite.getListRewrite(data.switchStatement, SwitchStatement.STATEMENTS_PROPERTY);
				statements.insertFirst(fallback, group);
				ASTNode body = firstCase.isSwitchLabeledRule()
						? rewrite.createStringPlaceholder("{ /* No matching constant. */ }", ASTNode.BLOCK) //$NON-NLS-1$
						: ast.newBreakStatement();
				statements.insertAfter(body, fallback, group);
			}

			// 6. Update the proven parameter and every accepted local call site.
			Type newType = ast.newSimpleType(ast.newSimpleName(enumName));
			rewrite.replace(data.parameter.getType(), newType, group);
			for (Map.Entry<Expression, String> reference : data.constantReferences.entrySet()) {
				if (!(reference.getKey().getParent() instanceof SwitchCase)) {
					String constant = reference.getValue().substring(prefix.length());
					rewrite.replace(reference.getKey(), ast.newName(enumName + "." + constant), group); //$NON-NLS-1$
				}
			}
		}
	}

	/**
	 * Find the longest common prefix of all constant names, ending at an underscore boundary.
	 * Returns null if no valid underscore-delimited prefix is found.
	 */
	static String findCommonPrefix(List<String> names) {
		if (names == null || names.isEmpty()) {
			return null;
		}
		String first = names.get(0);
		int prefixEnd = first.length();
		for (String name : names) {
			prefixEnd = Math.min(prefixEnd, name.length());
			for (int i = 0; i < prefixEnd; i++) {
				if (first.charAt(i) != name.charAt(i)) {
					prefixEnd = i;
					break;
				}
			}
		}
		String prefix = first.substring(0, prefixEnd);
		// Trim to last underscore for a clean prefix boundary.
		// If there is no underscore (or only at position 0), we do not
		// consider this a valid prefix according to the architecture
		// contract (which requires underscore-delimited prefixes).
		int lastUnderscore = prefix.lastIndexOf('_');
		if (lastUnderscore > 0) {
			return prefix.substring(0, lastUnderscore + 1);
		}
		return null;
	}

	/**
	 * Convert a constant prefix like "STATUS_" to an enum name like "Status".
	 */
	static String prefixToEnumName(String prefix) {
		if (prefix.endsWith("_")) { //$NON-NLS-1$
			prefix = prefix.substring(0, prefix.length() - 1);
		}
		StringBuilder sb = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : prefix.toLowerCase().toCharArray()) {
			if (c == '_') {
				capitalizeNext = true;
			} else {
				if (capitalizeNext) {
					sb.append(Character.toUpperCase(c));
					capitalizeNext = false;
				} else {
					sb.append(c);
				}
			}
		}
		return sb.toString();
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					// Before:
					private static final int STATUS_PENDING = 0;
					private static final int STATUS_APPROVED = 1;

					switch (status) {
					    case STATUS_PENDING:
					        // handle pending
					        break;
					    case STATUS_APPROVED:
					        // handle approved
					        break;
					}
					"""; //$NON-NLS-1$
		}
		return """
				// After:
				/** Values accepted by the status switch. */
				private enum Status {
				    PENDING, APPROVED
				}

				switch (status) {
				    default:
				        break;
				    case PENDING:
				        // handle pending
				        break;
				    case APPROVED:
				        // handle approved
				        break;
				}
				"""; //$NON-NLS-1$
	}
}
