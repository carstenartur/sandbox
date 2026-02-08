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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.IntToEnumFixCore;
import org.sandbox.jdt.internal.corext.fix.helper.IntToEnumHelper.IntConstantHolder;

/**
 * Helper class for converting switch statements using int constants to use enums.
 *
 * <p>This handles the case where a switch statement already exists with int constant
 * case labels. The transformation:</p>
 * <ol>
 * <li>Detects public static final int constant declarations with a common prefix</li>
 * <li>Finds switch statements that use these constants as case labels</li>
 * <li>Generates an enum type from the constant names</li>
 * <li>Replaces the int constants in switch cases with enum values</li>
 * <li>Removes the old int constant field declarations</li>
 * <li>Updates the method parameter type from int to the enum type</li>
 * </ol>
 */
public class SwitchIntToEnumHelper extends AbstractTool<ReferenceHolder<Integer, IntConstantHolder>> {

	@Override
	public void find(IntToEnumFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed) {

		// Phase 1: Collect all static final int field declarations
		Map<String, FieldDeclaration> intConstants = new LinkedHashMap<>();
		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(FieldDeclaration node) {
				int modifiers = node.getModifiers();
				if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
					Type type = node.getType();
					if (type.isPrimitiveType()) {
						PrimitiveType pt = (PrimitiveType) type;
						if (pt.getPrimitiveTypeCode() == PrimitiveType.INT) {
							for (Object fragment : node.fragments()) {
								VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
								intConstants.put(vdf.getName().getIdentifier(), node);
							}
						}
					}
				}
				return true;
			}
		});

		if (intConstants.size() < 2) {
			return;
		}

		// Phase 2: Find switch statements that reference these constants
		ReferenceHolder<Integer, IntConstantHolder> dataholder = new ReferenceHolder<>();

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SwitchStatement node) {
				if (nodesprocessed.contains(node)) {
					return true;
				}

				List<String> usedConstants = new ArrayList<>();
				Map<String, FieldDeclaration> usedFields = new LinkedHashMap<>();

				for (Object stmt : node.statements()) {
					if (stmt instanceof SwitchCase switchCase && !switchCase.isDefault()) {
						for (Object expr : switchCase.expressions()) {
							String constName = extractConstantName(expr);
							if (constName != null && intConstants.containsKey(constName)) {
								usedConstants.add(constName);
								usedFields.put(constName, intConstants.get(constName));
							}
						}
					}
				}

				if (usedConstants.size() >= 2) {
					String prefix = findCommonPrefix(usedConstants);
					if (prefix != null && !prefix.isEmpty()) {
						IntConstantHolder holder = new IntConstantHolder();
						holder.switchStatement = node;
						holder.constantFields.putAll(usedFields);
						holder.constantNames.addAll(usedConstants);

						Expression switchExpr = node.getExpression();
						if (switchExpr instanceof SimpleName sn) {
							holder.comparedVariable = sn.getIdentifier();
						}
						holder.nodesProcessed = nodesprocessed;

						dataholder.put(dataholder.size(), holder);
						nodesprocessed.add(node);
					}
				}

				return true;
			}
		});

		if (!dataholder.isEmpty()) {
			operations.add(fixcore.rewrite(dataholder));
		}
	}

	private static String extractConstantName(Object expr) {
		if (expr instanceof SimpleName sn) {
			return sn.getIdentifier();
		} else if (expr instanceof QualifiedName qn) {
			return qn.getName().getIdentifier();
		}
		return null;
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
			if (prefix == null || prefix.isEmpty()) {
				continue;
			}

			String enumName = prefixToEnumName(prefix);

			// 1. Create enum declaration
			EnumDeclaration enumDecl = ast.newEnumDeclaration();
			enumDecl.setName(ast.newSimpleName(enumName));
			enumDecl.modifiers().add(ast.newModifier(Modifier.ModifierKeyword.PUBLIC_KEYWORD));

			for (String constName : data.constantNames) {
				EnumConstantDeclaration enumConst = ast.newEnumConstantDeclaration();
				String enumValueName = constName.substring(prefix.length());
				enumConst.setName(ast.newSimpleName(enumValueName));
				enumDecl.enumConstants().add(enumConst);
			}

			// 2. Find the enclosing TypeDeclaration
			TypeDeclaration typeDecl = findEnclosingType(data.switchStatement);
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
			for (Object stmt : data.switchStatement.statements()) {
				if (stmt instanceof SwitchCase switchCase && !switchCase.isDefault()) {
					for (Object expr : switchCase.expressions()) {
						String constName = extractConstantName(expr);
						if (constName != null && data.constantFields.containsKey(constName)) {
							String enumValueName = constName.substring(prefix.length());
							SimpleName newName = ast.newSimpleName(enumValueName);
							rewrite.replace((ASTNode) expr, newName, group);
						}
					}
				}
			}

			// 6. Update method parameter type from int to enum
			MethodDeclaration method = findEnclosingMethod(data.switchStatement);
			if (method != null && data.comparedVariable != null) {
				for (Object param : method.parameters()) {
					SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
					if (svd.getName().getIdentifier().equals(data.comparedVariable)) {
						Type newType = ast.newSimpleType(ast.newSimpleName(enumName));
						rewrite.replace(svd.getType(), newType, group);
					}
				}
			}
		}
	}

	/**
	 * Find the longest common prefix of all constant names, ending at an underscore boundary.
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
		// Trim to last underscore for a clean prefix boundary
		int lastUnderscore = prefix.lastIndexOf('_');
		if (lastUnderscore > 0) {
			return prefix.substring(0, lastUnderscore + 1);
		}
		return prefix;
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

	private static TypeDeclaration findEnclosingType(ASTNode node) {
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parent instanceof TypeDeclaration typeDecl) {
				return typeDecl;
			}
			parent = parent.getParent();
		}
		return null;
	}

	private static MethodDeclaration findEnclosingMethod(ASTNode node) {
		ASTNode parent = node.getParent();
		while (parent != null) {
			if (parent instanceof MethodDeclaration methodDecl) {
				return methodDecl;
			}
			parent = parent.getParent();
		}
		return null;
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					// Before:
					public static final int STATUS_PENDING = 0;
					public static final int STATUS_APPROVED = 1;

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
				public enum Status {
				    PENDING, APPROVED
				}

				switch (status) {
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
