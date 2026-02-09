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
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.PrimitiveType;
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
 * <li>Detects static final int constant declarations with a common underscore-delimited prefix</li>
 * <li>Finds switch statements that use these constants as case labels</li>
 * <li>Verifies the switch selector is a method parameter (so the type can be updated)</li>
 * <li>Verifies constants are not referenced outside the switch (to avoid broken references)</li>
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

		compilationUnit.accept(new ASTVisitor() {
			@Override
			public boolean visit(SwitchStatement node) {
				if (nodesprocessed.contains(node)) {
					return true;
				}

				// Only process if the switch selector is a method parameter (so we can update its type)
				Expression switchExpr = node.getExpression();
				if (!(switchExpr instanceof SimpleName switchName)) {
					return true;
				}
				MethodDeclaration method = findEnclosingMethod(node);
				if (method == null || !isMethodParameter(method, switchName.getIdentifier())) {
					return true;
				}

				// Collect constants from the same enclosing type as the switch
				TypeDeclaration enclosingType = findEnclosingType(node);
				if (enclosingType == null) {
					return true;
				}
				Map<String, FieldDeclaration> intConstants = collectIntConstants(enclosingType);
				if (intConstants.size() < 2) {
					return true;
				}

				// Find case labels that reference collected constants (SimpleName only)
				List<String> usedConstants = new ArrayList<>();
				Map<String, FieldDeclaration> usedFields = new LinkedHashMap<>();

				for (Object stmt : node.statements()) {
					if (stmt instanceof SwitchCase switchCase && !switchCase.isDefault()) {
						for (Object expr : switchCase.expressions()) {
							if (expr instanceof SimpleName sn) {
								String constName = sn.getIdentifier();
								if (intConstants.containsKey(constName)) {
									usedConstants.add(constName);
									usedFields.put(constName, intConstants.get(constName));
								}
							}
						}
					}
				}

				if (usedConstants.size() < 2) {
					return true;
				}

				String prefix = findCommonPrefix(usedConstants);
				if (prefix == null) {
					return true;
				}

				// Validate all derived enum constant names are valid Java identifiers
				for (String constName : usedConstants) {
					String enumValueName = constName.substring(prefix.length());
					if (enumValueName.isEmpty() || !Character.isJavaIdentifierStart(enumValueName.charAt(0))) {
						return true;
					}
				}

				// Verify constants are not referenced outside the switch statement
				if (hasReferencesOutsideSwitch(compilationUnit, usedFields.keySet(), node)) {
					return true;
				}

				IntConstantHolder holder = new IntConstantHolder();
				holder.switchStatement = node;
				holder.constantFields.putAll(usedFields);
				holder.constantNames.addAll(usedConstants);
				holder.comparedVariable = switchName.getIdentifier();
				holder.nodesProcessed = nodesprocessed;

				ReferenceHolder<Integer, IntConstantHolder> dataholder = new ReferenceHolder<>();
				dataholder.put(0, holder);
				operations.add(fixcore.rewrite(dataholder));
				nodesprocessed.add(node);

				return true;
			}
		});
	}

	/**
	 * Collect static final int field declarations from the given type declaration.
	 */
	private static Map<String, FieldDeclaration> collectIntConstants(TypeDeclaration typeDecl) {
		Map<String, FieldDeclaration> intConstants = new LinkedHashMap<>();
		for (FieldDeclaration field : typeDecl.getFields()) {
			int modifiers = field.getModifiers();
			if (Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers)) {
				Type type = field.getType();
				if (type.isPrimitiveType()
						&& ((PrimitiveType) type).getPrimitiveTypeCode() == PrimitiveType.INT) {
					for (Object fragment : field.fragments()) {
						VariableDeclarationFragment vdf = (VariableDeclarationFragment) fragment;
						intConstants.put(vdf.getName().getIdentifier(), field);
					}
				}
			}
		}
		return intConstants;
	}

	/**
	 * Check if the given variable name is a parameter of the method.
	 */
	private static boolean isMethodParameter(MethodDeclaration method, String varName) {
		for (Object param : method.parameters()) {
			SingleVariableDeclaration svd = (SingleVariableDeclaration) param;
			if (svd.getName().getIdentifier().equals(varName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check if any of the named constants are referenced outside the given switch statement.
	 * Returns true if there are external references (meaning we should NOT transform).
	 */
	private static boolean hasReferencesOutsideSwitch(CompilationUnit cu, Set<String> constantNames,
			SwitchStatement switchStatement) {
		AtomicBoolean hasExternalRef = new AtomicBoolean(false);
		cu.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (hasExternalRef.get()) {
					return false;
				}
				if (!constantNames.contains(node.getIdentifier())) {
					return true;
				}
				IBinding binding = node.resolveBinding();
				if (!(binding instanceof IVariableBinding vb) || !vb.isField()) {
					return true;
				}
				// Check if this reference is inside the switch statement or in a field declaration
				ASTNode parent = node.getParent();
				while (parent != null) {
					if (parent == switchStatement) {
						return true; // inside the switch - OK
					}
					if (parent instanceof FieldDeclaration) {
						return true; // in the field declaration itself - OK
					}
					parent = parent.getParent();
				}
				// Reference found outside the switch statement
				hasExternalRef.set(true);
				return false;
			}
		});
		return hasExternalRef.get();
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
