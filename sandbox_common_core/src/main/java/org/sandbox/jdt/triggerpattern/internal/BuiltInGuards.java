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

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;

/**
 * Built-in guard function implementations for the trigger pattern engine.
 *
 * <p>This utility class contains all built-in guard functions extracted from
 * {@code GuardRegistry}. It has no OSGi or Eclipse runtime dependencies and
 * uses only {@code org.eclipse.jdt.core.dom.*} and pure Java.</p>
 *
 * <p>Call {@link #registerAll(Map)} to populate a guard map with all built-in
 * guard functions.</p>
 *
 * @since 1.3.2
 */
public final class BuiltInGuards {

	private BuiltInGuards() {
		// utility class
	}

	/**
	 * Registers all built-in guard functions into the provided map.
	 *
	 * @param guards the map to populate with guard function entries
	 */
	public static void registerAll(Map<String, GuardFunction> guards) {
		// Type guards
		guards.put("instanceof", BuiltInGuards::evaluateInstanceOf); //$NON-NLS-1$

		// Structural guards
		guards.put("matchesAny", BuiltInGuards::evaluateMatchesAny); //$NON-NLS-1$
		guards.put("matchesNone", BuiltInGuards::evaluateMatchesNone); //$NON-NLS-1$
		guards.put("hasNoSideEffect", BuiltInGuards::evaluateHasNoSideEffect); //$NON-NLS-1$
		guards.put("referencedIn", BuiltInGuards::evaluateReferencedIn); //$NON-NLS-1$

		// Java version guards
		guards.put("sourceVersionGE", BuiltInGuards::evaluateSourceVersionGE); //$NON-NLS-1$
		guards.put("sourceVersionLE", BuiltInGuards::evaluateSourceVersionLE); //$NON-NLS-1$
		guards.put("sourceVersionBetween", BuiltInGuards::evaluateSourceVersionBetween); //$NON-NLS-1$

		// Element kind guards
		guards.put("isStatic", BuiltInGuards::evaluateIsStatic); //$NON-NLS-1$
		guards.put("isFinal", BuiltInGuards::evaluateIsFinal); //$NON-NLS-1$
		guards.put("elementKindMatches", BuiltInGuards::evaluateElementKindMatches); //$NON-NLS-1$

		// Annotation guards
		guards.put("hasAnnotation", BuiltInGuards::evaluateHasAnnotation); //$NON-NLS-1$
		guards.put("isDeprecated", BuiltInGuards::evaluateIsDeprecated); //$NON-NLS-1$

		// Negated pattern guards
		guards.put("contains", BuiltInGuards::evaluateContains); //$NON-NLS-1$
		guards.put("notContains", BuiltInGuards::evaluateNotContains); //$NON-NLS-1$
	}

	/**
	 * Checks if the bound node's type matches a given type name via ITypeBinding.
	 * Supports array types (e.g., {@code Type[]}).
	 * Args: [placeholderName, typeName]
	 */
	private static boolean evaluateInstanceOf(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String typeName = args[1].toString();

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		ITypeBinding typeBinding = resolveTypeBinding(node);
		if (typeBinding == null) {
			return false;
		}

		// Handle array types: "Type[]"
		if (typeName.endsWith("[]") && typeName.length() > 2) { //$NON-NLS-1$
			if (!typeBinding.isArray()) {
				return false;
			}
			String elementTypeName = typeName.substring(0, typeName.length() - 2);
			return matchesTypeName(typeBinding.getElementType(), elementTypeName);
		}

		return matchesTypeName(typeBinding, typeName);
	}

	/**
	 * Returns true if a placeholder's text matches any of the given literals.
	 * With a single argument, returns true if the binding exists and is non-null.
	 * With multiple arguments, checks if the placeholder's value matches any literal.
	 *
	 * <p>For literal AST nodes (StringLiteral, NumberLiteral, CharacterLiteral, BooleanLiteral),
	 * the literal value is extracted for comparison. For other nodes, the source text is used.</p>
	 *
	 * Args: [placeholderName] or [placeholderName, literal1, literal2, ...]
	 */
	private static boolean evaluateMatchesAny(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);

		// If only placeholder name given, check existence
		if (args.length == 1) {
			if (node != null) {
				return true;
			}
			List<ASTNode> listBinding = ctx.getListBinding(placeholderName);
			return !listBinding.isEmpty();
		}

		// Multiple arguments: check if node value matches any literal
		if (node == null) {
			return false;
		}
		String nodeText = extractNodeText(node);
		for (int i = 1; i < args.length; i++) {
			String literal = stripQuotes(args[i].toString());
			if (nodeText.equals(literal)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns true if a placeholder's text matches none of the given literals.
	 * With a single argument, returns true if the binding does not exist.
	 * With multiple arguments, checks that the placeholder's value matches no literal.
	 *
	 * <p>For literal AST nodes (StringLiteral, NumberLiteral, CharacterLiteral, BooleanLiteral),
	 * the literal value is extracted for comparison. For other nodes, the source text is used.</p>
	 *
	 * Args: [placeholderName] or [placeholderName, literal1, literal2, ...]
	 */
	private static boolean evaluateMatchesNone(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return true;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);

		// If only placeholder name given, check non-existence
		if (args.length == 1) {
			if (node != null) {
				return false;
			}
			List<ASTNode> listBinding = ctx.getListBinding(placeholderName);
			return listBinding.isEmpty();
		}

		// Multiple arguments: check that node value matches none of the literals
		if (node == null) {
			return true;
		}
		String nodeText = extractNodeText(node);
		for (int i = 1; i < args.length; i++) {
			String literal = stripQuotes(args[i].toString());
			if (nodeText.equals(literal)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Checks if an expression has no side effects.
	 * Currently checks that the node is not a method invocation (conservative check).
	 * Args: [placeholderName]
	 */
	private static boolean evaluateHasNoSideEffect(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return true;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return true;
		}
		// Conservative: method invocations may have side effects
		return !(node instanceof MethodInvocation);
	}

	/**
	 * Checks if the source version is greater than or equal to a given version.
	 * Args: [version]
	 */
	private static boolean evaluateSourceVersionGE(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		double requiredVersion = parseVersion(args[0].toString());
		double sourceVersion = parseVersion(ctx.getSourceVersion());
		return sourceVersion >= requiredVersion;
	}

	/**
	 * Checks if the source version is less than or equal to a given version.
	 * Args: [version]
	 */
	private static boolean evaluateSourceVersionLE(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		double requiredVersion = parseVersion(args[0].toString());
		double sourceVersion = parseVersion(ctx.getSourceVersion());
		return sourceVersion <= requiredVersion;
	}

	/**
	 * Checks if the source version is within a given range (inclusive).
	 * Args: [minVersion, maxVersion]
	 */
	private static boolean evaluateSourceVersionBetween(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		double minVersion = parseVersion(args[0].toString());
		double maxVersion = parseVersion(args[1].toString());
		double sourceVersion = parseVersion(ctx.getSourceVersion());
		return sourceVersion >= minVersion && sourceVersion <= maxVersion;
	}

	/**
	 * Checks if a binding has the static modifier.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsStatic(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}
		int modifiers = resolveModifiers(node);
		return Modifier.isStatic(modifiers);
	}

	/**
	 * Checks if a binding has the final modifier.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsFinal(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}
		int modifiers = resolveModifiers(node);
		return Modifier.isFinal(modifiers);
	}

	/**
	 * Checks if a binding has a specific annotation.
	 * Args: [placeholderName, annotationName]
	 */
	@SuppressWarnings("unchecked")
	private static boolean evaluateHasAnnotation(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String annotationName = args[1].toString();

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		// Navigate to the enclosing body declaration to check annotations
		BodyDeclaration bodyDecl = findEnclosingBodyDeclaration(node);
		if (bodyDecl != null) {
			for (Object modifier : bodyDecl.modifiers()) {
				if (modifier instanceof Annotation annotation) {
					String name = getAnnotationName(annotation);
					if (annotationName.equals(name)) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Checks if a binding is deprecated (has @Deprecated annotation or Javadoc @deprecated tag).
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsDeprecated(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		// Check via binding
		IBinding binding = resolveBinding(node);
		if (binding != null) {
			return binding.isDeprecated();
		}

		return false;
	}

	/**
	 * Checks if variable {@code $x} is referenced within the AST subtree bound to {@code $y}.
	 * Args: [variablePlaceholderName, expressionPlaceholderName]
	 */
	private static boolean evaluateReferencedIn(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String varName = args[0].toString();
		String exprName = args[1].toString();

		ASTNode varNode = ctx.getBinding(varName);
		ASTNode exprNode = ctx.getBinding(exprName);
		if (varNode == null || exprNode == null) {
			return false;
		}

		// Get the identifier text of the variable
		String varIdentifier;
		if (varNode instanceof SimpleName simpleName) {
			varIdentifier = simpleName.getIdentifier();
		} else {
			varIdentifier = varNode.toString().trim();
		}

		// Walk the expression subtree looking for a SimpleName with the same identifier
		boolean[] found = { false };
		exprNode.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName name) {
				if (name.getIdentifier().equals(varIdentifier)) {
					found[0] = true;
				}
				return !found[0]; // stop visiting once found
			}
		});
		return found[0];
	}

	/**
	 * Checks if a binding is of a specific element kind.
	 * Supported kinds: FIELD, METHOD, LOCAL_VARIABLE, PARAMETER, TYPE.
	 * Args: [placeholderName, elementKind]
	 */
	private static boolean evaluateElementKindMatches(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String elementKind = args[1].toString();

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		return matchesElementKind(node, elementKind);
	}

	/**
	 * Checks if a text pattern occurs within the enclosing method body.
	 *
	 * <p>This guard traverses the enclosing method's body looking for a simple text
	 * match in the source representation. Useful for checking whether a particular
	 * call (e.g., {@code close()}) exists in the same method.</p>
	 *
	 * Args: [textToFind] or [placeholderName, textToFind]
	 */
	private static boolean evaluateContains(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}

		String textToFind;
		ASTNode contextNode;

		if (args.length >= 2) {
			// contains($x, "text") - search in $x's enclosing method
			String placeholderName = args[0].toString();
			textToFind = stripQuotes(args[1].toString());
			contextNode = ctx.getBinding(placeholderName);
		} else {
			// contains("text") - search in matched node's enclosing method
			textToFind = stripQuotes(args[0].toString());
			contextNode = ctx.getMatchedNode();
		}

		if (contextNode == null) {
			return false;
		}

		Block methodBody = findEnclosingMethodBody(contextNode);
		if (methodBody == null) {
			return false;
		}

		return methodBody.toString().contains(textToFind);
	}

	/**
	 * Checks if a text pattern does NOT occur within the enclosing method body.
	 *
	 * <p>Negation of {@link #evaluateContains}. Useful for detecting "missing calls"
	 * patterns (e.g., "close() is missing after open()").</p>
	 *
	 * Args: [textToFind] or [placeholderName, textToFind]
	 */
	private static boolean evaluateNotContains(GuardContext ctx, Object... args) {
		return !evaluateContains(ctx, args);
	}

	// ---- Helper methods ----

	/**
	 * Resolves the type binding for an AST node.
	 */
	private static ITypeBinding resolveTypeBinding(ASTNode node) {
		if (node instanceof Name name) {
			IBinding binding = name.resolveBinding();
			if (binding instanceof IVariableBinding varBinding) {
				return varBinding.getType();
			}
			if (binding instanceof ITypeBinding typeBinding) {
				return typeBinding;
			}
		}
		if (node instanceof MethodInvocation methodInv) {
			IMethodBinding methodBinding = methodInv.resolveMethodBinding();
			if (methodBinding != null) {
				return methodBinding.getReturnType();
			}
		}
		if (node instanceof Expression expr) {
			return expr.resolveTypeBinding();
		}
		return null;
	}

	/**
	 * Checks if a type binding matches a given type name (simple or qualified).
	 */
	private static boolean matchesTypeName(ITypeBinding typeBinding, String typeName) {
		if (typeBinding.getName().equals(typeName)) {
			return true;
		}
		if (typeBinding.getQualifiedName().equals(typeName)) {
			return true;
		}
		// Check supertypes
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null && matchesTypeName(superclass, typeName)) {
			return true;
		}
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (matchesTypeName(iface, typeName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Resolves an IBinding from an AST node.
	 */
	private static IBinding resolveBinding(ASTNode node) {
		if (node instanceof SimpleName simpleName) {
			return simpleName.resolveBinding();
		}
		if (node instanceof MethodDeclaration methodDecl) {
			return methodDecl.resolveBinding();
		}
		if (node instanceof VariableDeclarationFragment varFrag) {
			return varFrag.resolveBinding();
		}
		if (node instanceof SingleVariableDeclaration singleVar) {
			return singleVar.resolveBinding();
		}
		return null;
	}

	/**
	 * Resolves the modifiers for an AST node via its binding.
	 */
	private static int resolveModifiers(ASTNode node) {
		IBinding binding = resolveBinding(node);
		if (binding != null) {
			return binding.getModifiers();
		}
		if (node instanceof BodyDeclaration bodyDecl) {
			return bodyDecl.getModifiers();
		}
		return 0;
	}

	/**
	 * Gets the simple name of an annotation.
	 */
	private static String getAnnotationName(Annotation annotation) {
		if (annotation instanceof MarkerAnnotation marker) {
			return marker.getTypeName().getFullyQualifiedName();
		}
		if (annotation instanceof SingleMemberAnnotation sma) {
			return sma.getTypeName().getFullyQualifiedName();
		}
		if (annotation instanceof NormalAnnotation normal) {
			return normal.getTypeName().getFullyQualifiedName();
		}
		return ""; //$NON-NLS-1$
	}

	/**
	 * Parses a Java source version string to a numeric value.
	 * Handles formats like "1.8", "11", "17", "21".
	 */
	private static double parseVersion(String version) {
		if (version == null || version.isEmpty()) {
			return 0;
		}
		try {
			return Double.parseDouble(version);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/**
	 * Extracts a comparable text value from an AST node.
	 *
	 * <p>For literal nodes, extracts the literal value (without surrounding quotes).
	 * For other nodes, falls back to {@code toString().trim()}.</p>
	 *
	 * @param node the AST node to extract text from
	 * @return the extracted text value
	 */
	private static String extractNodeText(ASTNode node) {
		if (node instanceof StringLiteral stringLiteral) {
			return stringLiteral.getLiteralValue();
		}
		if (node instanceof CharacterLiteral charLiteral) {
			return String.valueOf(charLiteral.charValue());
		}
		if (node instanceof NumberLiteral numberLiteral) {
			return numberLiteral.getToken();
		}
		if (node instanceof BooleanLiteral boolLiteral) {
			return String.valueOf(boolLiteral.booleanValue());
		}
		return node.toString().trim();
	}

	/**
	 * Strips surrounding quotes from a string literal argument.
	 */
	private static String stripQuotes(String value) {
		if (value.length() >= 2
				&& ((value.startsWith("\"") && value.endsWith("\"")) //$NON-NLS-1$ //$NON-NLS-2$
				|| (value.startsWith("'") && value.endsWith("'")))) { //$NON-NLS-1$ //$NON-NLS-2$
			return value.substring(1, value.length() - 1);
		}
		return value;
	}

	/**
	 * Finds the enclosing method body for an AST node.
	 */
	private static Block findEnclosingMethodBody(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof MethodDeclaration methodDecl) {
				return methodDecl.getBody();
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Finds the nearest enclosing BodyDeclaration for an AST node.
	 */
	private static BodyDeclaration findEnclosingBodyDeclaration(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof BodyDeclaration bodyDecl) {
				return bodyDecl;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Checks if an AST node matches the given element kind string.
	 */
	private static boolean matchesElementKind(ASTNode node, String elementKind) {
		// Try via binding first
		IBinding binding = resolveBinding(node);
		if (binding != null) {
			return matchesBindingKind(binding, elementKind);
		}
		// Fallback: check AST node type directly
		return matchesNodeKind(node, elementKind);
	}

	/**
	 * Matches a binding against an element kind string.
	 */
	private static boolean matchesBindingKind(IBinding binding, String elementKind) {
		return switch (elementKind.toUpperCase(Locale.ROOT)) {
			case "FIELD" -> binding instanceof IVariableBinding vb && vb.isField(); //$NON-NLS-1$
			case "METHOD" -> binding instanceof IMethodBinding; //$NON-NLS-1$
			case "LOCAL_VARIABLE" -> binding instanceof IVariableBinding vb && !vb.isField() && !vb.isParameter(); //$NON-NLS-1$
			case "PARAMETER" -> binding instanceof IVariableBinding vb && vb.isParameter(); //$NON-NLS-1$
			case "TYPE" -> binding instanceof ITypeBinding; //$NON-NLS-1$
			default -> false;
		};
	}

	/**
	 * Matches an AST node type against an element kind string (fallback when binding is unavailable).
	 */
	private static boolean matchesNodeKind(ASTNode node, String elementKind) {
		return switch (elementKind.toUpperCase(Locale.ROOT)) {
			case "FIELD" -> node instanceof FieldDeclaration //$NON-NLS-1$
					|| (node instanceof VariableDeclarationFragment vdf && vdf.getParent() instanceof FieldDeclaration);
			case "METHOD" -> node instanceof MethodDeclaration; //$NON-NLS-1$
			case "LOCAL_VARIABLE" -> node instanceof VariableDeclarationStatement //$NON-NLS-1$
					|| (node instanceof VariableDeclarationFragment vdf && vdf.getParent() instanceof VariableDeclarationStatement);
			case "PARAMETER" -> node instanceof SingleVariableDeclaration; //$NON-NLS-1$
			case "TYPE" -> node instanceof TypeDeclaration //$NON-NLS-1$
					|| node instanceof EnumDeclaration
					|| node instanceof RecordDeclaration;
			default -> false;
		};
	}
}
