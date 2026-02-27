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
import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.BooleanLiteral;
import org.eclipse.jdt.core.dom.CharacterLiteral;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
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
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.NumberLiteral;
import org.eclipse.jdt.core.dom.PackageDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.nullability.NullabilityGuard;
import org.sandbox.jdt.triggerpattern.nullability.NullabilityResult;
import org.sandbox.jdt.triggerpattern.nullability.NullStatus;

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

	/** Cached NullabilityGuard instance to avoid repeated initialization. */
	private static final NullabilityGuard NULLABILITY_GUARD = new NullabilityGuard();

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
		guards.put("subtypeOf", BuiltInGuards::evaluateSubtypeOf); //$NON-NLS-1$

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

		// Nullability guards
		guards.put("isNullable", BuiltInGuards::evaluateIsNullable); //$NON-NLS-1$
		guards.put("isNonNull", BuiltInGuards::evaluateIsNonNull); //$NON-NLS-1$

		// NetBeans compatibility: otherwise guard (always true)
		guards.put("otherwise", (ctx, args) -> true); //$NON-NLS-1$

		// Literal and type guards
		guards.put("isLiteral", BuiltInGuards::evaluateIsLiteral); //$NON-NLS-1$
		guards.put("isNullLiteral", BuiltInGuards::evaluateIsNullLiteral); //$NON-NLS-1$
		guards.put("isCharsetString", BuiltInGuards::evaluateIsCharsetString); //$NON-NLS-1$
		guards.put("isSingleCharacter", BuiltInGuards::evaluateIsSingleCharacter); //$NON-NLS-1$
		guards.put("isRegexp", BuiltInGuards::evaluateIsRegexp); //$NON-NLS-1$

		// Context guards
		guards.put("isInTryWithResourceBlock", BuiltInGuards::evaluateIsInTryWithResourceBlock); //$NON-NLS-1$
		guards.put("isPassedToMethod", BuiltInGuards::evaluateIsPassedToMethod); //$NON-NLS-1$
		guards.put("inSerializableClass", BuiltInGuards::evaluateInSerializableClass); //$NON-NLS-1$
		guards.put("containsAnnotation", BuiltInGuards::evaluateContainsAnnotation); //$NON-NLS-1$
		guards.put("parentMatches", BuiltInGuards::evaluateParentMatches); //$NON-NLS-1$

		// Scope guards
		guards.put("inClass", BuiltInGuards::evaluateInClass); //$NON-NLS-1$
		guards.put("inPackage", BuiltInGuards::evaluateInPackage); //$NON-NLS-1$

		// Modifier guard
		guards.put("hasModifier", BuiltInGuards::evaluateHasModifier); //$NON-NLS-1$

		// Cleanup mode guard — checks sandbox.cleanup.mode compiler option
		guards.put("mode", BuiltInGuards::evaluateMode); //$NON-NLS-1$

		// Method name pattern guard — checks if method name matches a regex pattern
		guards.put("methodNameMatches", BuiltInGuards::evaluateMethodNameMatches); //$NON-NLS-1$

		// Type hierarchy guard — checks if enclosing class extends a given type
		guards.put("enclosingClassExtends", BuiltInGuards::evaluateEnclosingClassExtends); //$NON-NLS-1$

		// SuppressWarnings guard — checks if enclosing element has @SuppressWarnings with given key
		guards.put("hasSuppressWarnings", BuiltInGuards::evaluateHasSuppressWarnings); //$NON-NLS-1$

		// Field guard — checks if enclosing class has a field with a given name
		guards.put("hasField", BuiltInGuards::evaluateHasField); //$NON-NLS-1$

		// Loop guard — checks if the matched node is inside a loop
		guards.put("isInLoop", BuiltInGuards::evaluateIsInLoop); //$NON-NLS-1$

		// Parameter count guard — checks if enclosing method has expected param count
		guards.put("paramCount", BuiltInGuards::evaluateParamCount); //$NON-NLS-1$

		// Return type guard — checks if enclosing method's return type matches
		guards.put("hasReturnType", BuiltInGuards::evaluateHasReturnType); //$NON-NLS-1$

		// String literal guard — checks if a placeholder is a StringLiteral node
		guards.put("isStringLiteral", BuiltInGuards::evaluateIsStringLiteral); //$NON-NLS-1$
	}

	/**
	 * Checks if the bound node's type matches a given type name via ITypeBinding.
	 * Supports array types (e.g., {@code Type[]}).
	 *
	 * <p><b>Graceful degradation:</b> If the type binding cannot be resolved
	 * (e.g., because {@code ASTParser.setResolveBindings(false)} was used),
	 * this guard returns {@code true} to allow the rule to match.
	 * This ensures that rules with type guards still work in environments
	 * where binding resolution is not available, though disambiguation
	 * between ambiguous rules will not be possible.</p>
	 *
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
			// Graceful degradation: if bindings are not available,
			// assume the guard matches (allows the rule to apply).
			return true;
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
	 * Checks if the bound node's type is a subtype of a given fully qualified type name.
	 * Walks the type hierarchy via {@link ITypeBinding#getSuperclass()} and
	 * {@link ITypeBinding#getInterfaces()}.
	 *
	 * <p><b>Graceful degradation:</b> If the type binding cannot be resolved,
	 * this guard returns {@code true} to allow the rule to match.</p>
	 *
	 * Args: [placeholderName, fullyQualifiedTypeName]
	 * @since 1.4.0
	 */
	private static boolean evaluateSubtypeOf(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String targetFqn = stripQuotes(args[1].toString());

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		ITypeBinding typeBinding = resolveTypeBinding(node);
		if (typeBinding == null) {
			// Graceful degradation: assume match if bindings not available
			return true;
		}

		return isSubtypeOf(typeBinding, targetFqn, new java.util.HashSet<>());
	}

	/**
	 * Recursively checks if a type binding is a subtype of the given FQN,
	 * walking the superclass chain and all interfaces.
	 */
	private static boolean isSubtypeOf(ITypeBinding typeBinding, String targetFqn, java.util.Set<String> visited) {
		if (typeBinding == null || typeBinding.isRecovered()) {
			return false;
		}
		String qualifiedName = typeBinding.getQualifiedName();
		if (!visited.add(qualifiedName)) {
			return false; // Already visited — break potential cycle
		}
		if (targetFqn.equals(qualifiedName)) {
			return true;
		}
		// Check superclass
		if (isSubtypeOf(typeBinding.getSuperclass(), targetFqn, visited)) {
			return true;
		}
		// Check interfaces
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (isSubtypeOf(iface, targetFqn, visited)) {
				return true;
			}
		}
		return false;
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

	/**
	 * Checks if a placeholder's expression is potentially nullable.
	 *
	 * <p>With one argument: returns true if the expression is not provably NON_NULL.</p>
	 * <p>With two arguments: computes a nullability score (0-10) and returns true
	 * only if score >= minScore. Score mapping:</p>
	 * <ul>
	 *   <li>NON_NULL → 0 (definitely safe, no change needed)</li>
	 *   <li>UNKNOWN → 5 (undetermined)</li>
	 *   <li>POTENTIALLY_NULLABLE → 7 (there are null-checks nearby)</li>
	 *   <li>NULLABLE → 10 (high risk, SpotBugs-style: null-check found after usage)</li>
	 * </ul>
	 *
	 * Args: [placeholderName] or [placeholderName, minScore]
	 */
	private static boolean evaluateIsNullable(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		// Cast to Expression, return false if not
		if (!(node instanceof Expression expression)) {
			return false;
		}

		// Analyze nullability
		NullabilityResult result = NULLABILITY_GUARD.analyze(expression);

		// Map NullStatus to score
		int score = mapNullStatusToScore(result.status());

		// With one argument: return true if NOT NON_NULL
		if (args.length == 1) {
			return result.status() != NullStatus.NON_NULL;
		}

		// With two arguments: compare score with minScore
		try {
			int minScore = Integer.parseInt(args[1].toString());
			return score >= minScore;
		} catch (NumberFormatException e) {
			// Invalid minScore, fall back to single-argument behavior
			return result.status() != NullStatus.NON_NULL;
		}
	}

	/**
	 * Checks if a placeholder's expression is provably non-null.
	 *
	 * <p>Returns true if the {@code NullabilityGuard.analyze()} determines
	 * the expression is {@code NON_NULL}.</p>
	 *
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsNonNull(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		// Cast to Expression, return false if not
		if (!(node instanceof Expression expression)) {
			return false;
		}

		// Analyze nullability
		NullabilityResult result = NULLABILITY_GUARD.analyze(expression);

		return result.status() == NullStatus.NON_NULL;
	}

	/**
	 * Maps a NullStatus to a numeric score for comparison.
	 * 
	 * @param status the null status
	 * @return score from 0 (definitely non-null) to 10 (definitely nullable)
	 */
	private static int mapNullStatusToScore(NullStatus status) {
		return switch (status) {
			case NON_NULL -> 0;
			case UNKNOWN -> 5;
			case POTENTIALLY_NULLABLE -> 7;
			case NULLABLE -> 10;
		};
	}

	// ---- New NetBeans-compatible guard implementations ----

	/**
	 * Standard charsets supported by {@link java.nio.charset.StandardCharsets}.
	 */
	private static final Set<String> STANDARD_CHARSETS = Set.of(
			"UTF-8", "UTF-16", "UTF-16BE", "UTF-16LE", "ISO-8859-1", "US-ASCII"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$

	/**
	 * Characters that indicate a regex pattern (not a plain literal).
	 */
	private static final String REGEX_META_CHARS = ".\\+*^$?|[](){}-"; //$NON-NLS-1$

	/**
	 * Checks if the bound node is any literal AST node type.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsLiteral(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		if (node == null) {
			return false;
		}
		return node instanceof StringLiteral
				|| node instanceof NumberLiteral
				|| node instanceof CharacterLiteral
				|| node instanceof BooleanLiteral
				|| node instanceof NullLiteral;
	}

	/**
	 * Checks if the bound node is a {@link NullLiteral}.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsNullLiteral(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		return node instanceof NullLiteral;
	}

	/**
	 * Checks if the bound node is a {@link StringLiteral} whose value is a
	 * standard charset name (UTF-8, UTF-16, UTF-16BE, UTF-16LE, ISO-8859-1, US-ASCII).
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsCharsetString(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		if (!(node instanceof StringLiteral stringLiteral)) {
			return false;
		}
		return STANDARD_CHARSETS.contains(stringLiteral.getLiteralValue());
	}

	/**
	 * Checks if the bound node is a {@link StringLiteral} with exactly one character.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsSingleCharacter(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		if (!(node instanceof StringLiteral stringLiteral)) {
			return false;
		}
		return stringLiteral.getLiteralValue().length() == 1;
	}

	/**
	 * Checks if the bound node is a {@link StringLiteral} containing regex metacharacters.
	 * Args: [placeholderName]
	 */
	private static boolean evaluateIsRegexp(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		if (!(node instanceof StringLiteral stringLiteral)) {
			return false;
		}
		String value = stringLiteral.getLiteralValue();
		for (int i = 0; i < value.length(); i++) {
			if (REGEX_META_CHARS.indexOf(value.charAt(i)) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if the matched node is inside a try-with-resources block.
	 * Args: [] or [placeholderName]
	 */
	@SuppressWarnings("unchecked")
	private static boolean evaluateIsInTryWithResourceBlock(GuardContext ctx, Object... args) {
		ASTNode node;
		if (args.length >= 1) {
			node = ctx.getBinding(args[0].toString());
		} else {
			node = ctx.getMatchedNode();
		}
		if (node == null) {
			return false;
		}
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof TryStatement tryStmt) {
				List<Expression> resources = tryStmt.resources();
				for (Expression resource : resources) {
					if (isAncestorOrSelf(resource, node)) {
						return true;
					}
				}
			}
			current = current.getParent();
		}
		return false;
	}

	/**
	 * Checks if the matched node is passed as an argument to a method invocation
	 * or constructor call.
	 * Args: [] or [placeholderName]
	 */
	@SuppressWarnings("unchecked")
	private static boolean evaluateIsPassedToMethod(GuardContext ctx, Object... args) {
		ASTNode node;
		if (args.length >= 1) {
			node = ctx.getBinding(args[0].toString());
		} else {
			node = ctx.getMatchedNode();
		}
		if (node == null) {
			return false;
		}
		ASTNode parent = node.getParent();
		if (parent instanceof MethodInvocation mi) {
			return mi.arguments().contains(node);
		}
		if (parent instanceof ClassInstanceCreation cic) {
			return cic.arguments().contains(node);
		}
		return false;
	}

	/**
	 * Checks if the matched node is inside a class that implements {@code java.io.Serializable}.
	 * Args: [] or [placeholderName]
	 */
	private static boolean evaluateInSerializableClass(GuardContext ctx, Object... args) {
		ASTNode node;
		if (args.length >= 1) {
			node = ctx.getBinding(args[0].toString());
		} else {
			node = ctx.getMatchedNode();
		}
		if (node == null) {
			return false;
		}
		TypeDeclaration typeDecl = findEnclosingTypeDeclaration(node);
		if (typeDecl == null) {
			return false;
		}
		ITypeBinding typeBinding = typeDecl.resolveBinding();
		if (typeBinding == null) {
			return false;
		}
		return implementsSerializable(typeBinding);
	}

	/**
	 * Checks if a modifiers list or body declaration contains a specific annotation.
	 * Args: [placeholderName, annotationFqn]
	 */
	@SuppressWarnings("unchecked")
	private static boolean evaluateContainsAnnotation(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String annotationName = stripQuotes(args[1].toString());

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		// If the node is a BodyDeclaration, check its modifiers
		BodyDeclaration bodyDecl;
		if (node instanceof BodyDeclaration bd) {
			bodyDecl = bd;
		} else {
			bodyDecl = findEnclosingBodyDeclaration(node);
		}
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
	 * Checks if the parent of the matched node matches a given expression pattern string.
	 * 
	 * <p><b>Limitations:</b> This performs a simple string-contains check on the
	 * parent's {@code toString()} representation. It may produce false positives
	 * for partial name matches. The {@code $_} placeholder in the pattern is
	 * stripped before matching. For more precise matching, consider using
	 * the pattern matching engine directly.</p>
	 * 
	 * Args: [pattern] or [placeholderName, pattern]
	 */
	private static boolean evaluateParentMatches(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node;
		String pattern;
		if (args.length >= 2) {
			node = ctx.getBinding(args[0].toString());
			pattern = stripQuotes(args[1].toString());
		} else {
			node = ctx.getMatchedNode();
			pattern = stripQuotes(args[0].toString());
		}
		if (node == null) {
			return false;
		}
		ASTNode parent = node.getParent();
		if (parent == null) {
			return false;
		}
		// Simple string-contains check on the parent's source representation
		// Replace $_ with .* for wildcard matching concept
		String parentText = parent.toString().trim();
		String simplePattern = pattern.replace("$_", ""); //$NON-NLS-1$ //$NON-NLS-2$
		return parentText.contains(simplePattern);
	}

	/**
	 * Checks if the matched node is inside a class with the given fully qualified name.
	 * Args: [fqn]
	 */
	private static boolean evaluateInClass(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String classFqn = stripQuotes(args[0].toString());
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		TypeDeclaration typeDecl = findEnclosingTypeDeclaration(node);
		if (typeDecl == null) {
			return false;
		}
		ITypeBinding typeBinding = typeDecl.resolveBinding();
		if (typeBinding != null) {
			return classFqn.equals(typeBinding.getQualifiedName());
		}
		// Fallback: compare simple name
		return classFqn.equals(typeDecl.getName().getIdentifier())
				|| classFqn.endsWith("." + typeDecl.getName().getIdentifier()); //$NON-NLS-1$
	}

	/**
	 * Checks if the matched node is inside a package with the given name.
	 * Args: [packageName]
	 */
	private static boolean evaluateInPackage(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String packageName = stripQuotes(args[0].toString());
		CompilationUnit cu = ctx.getCompilationUnit();
		if (cu == null) {
			return false;
		}
		PackageDeclaration packageDecl = cu.getPackage();
		if (packageDecl == null) {
			return packageName.isEmpty();
		}
		return packageName.equals(packageDecl.getName().getFullyQualifiedName());
	}

	/**
	 * Checks if a binding has a specific modifier.
	 * Supports: PUBLIC, PRIVATE, PROTECTED, ABSTRACT, STATIC, FINAL, SYNCHRONIZED, VOLATILE, TRANSIENT, NATIVE, STRICTFP.
	 * Args: [placeholderName, modifierName]
	 */
	private static boolean evaluateHasModifier(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String modifierName = args[1].toString().toUpperCase(Locale.ROOT);

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}
		int modifiers = resolveModifiers(node);
		return matchesModifierName(modifiers, modifierName);
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
	 * 
	 * <p>When binding resolution is unavailable (e.g., standalone ASTParser
	 * without a project), this method falls back to navigating from a
	 * {@link SimpleName} to its parent {@link BodyDeclaration} to read
	 * modifiers directly from the AST.</p>
	 */
	private static int resolveModifiers(ASTNode node) {
		IBinding binding = resolveBinding(node);
		if (binding != null) {
			return binding.getModifiers();
		}
		if (node instanceof BodyDeclaration bodyDecl) {
			return bodyDecl.getModifiers();
		}
		// Fallback: navigate from SimpleName to parent BodyDeclaration
		// This handles METHOD_DECLARATION patterns where $name binds to the
		// method's SimpleName but binding resolution is unavailable.
		if (node instanceof SimpleName) {
			ASTNode parent = node.getParent();
			if (parent instanceof BodyDeclaration parentDecl) {
				return parentDecl.getModifiers();
			}
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
	 *
	 * <p>Old-style Java version strings like "1.5", "1.6", "1.7", "1.8" are
	 * converted to their major version equivalents (5, 6, 7, 8) so that
	 * guards like {@code sourceVersionGE(7)} work correctly for Java 8
	 * (source version "1.8").</p>
	 */
	private static double parseVersion(String version) {
		if (version == null || version.isEmpty()) {
			return 0;
		}
		try {
			double v = Double.parseDouble(version);
			// Convert old-style "1.x" versions (1.5, 1.6, 1.7, 1.8) to major versions (5, 6, 7, 8)
			if (v > 1.0 && v < 2.0) {
				return Math.round(v * 10) - 10; // 1.5→5, 1.6→6, 1.7→7, 1.8→8
			}
			return v;
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

	/**
	 * Finds the nearest enclosing TypeDeclaration for an AST node.
	 */
	private static TypeDeclaration findEnclosingTypeDeclaration(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof TypeDeclaration typeDecl) {
				return typeDecl;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Checks if a type binding implements {@code java.io.Serializable} (directly or transitively).
	 * Uses a visited set to prevent infinite recursion in case of cycles in the type hierarchy.
	 */
	private static boolean implementsSerializable(ITypeBinding typeBinding) {
		return implementsSerializable(typeBinding, new java.util.HashSet<>());
	}

	private static boolean implementsSerializable(ITypeBinding typeBinding, java.util.Set<String> visited) {
		String qualifiedName = typeBinding.getQualifiedName();
		if (!visited.add(qualifiedName)) {
			return false; // Already visited — break potential cycle
		}
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if ("java.io.Serializable".equals(iface.getQualifiedName())) { //$NON-NLS-1$
				return true;
			}
			if (implementsSerializable(iface, visited)) {
				return true;
			}
		}
		ITypeBinding superclass = typeBinding.getSuperclass();
		if (superclass != null) {
			return implementsSerializable(superclass, visited);
		}
		return false;
	}

	/**
	 * Checks if {@code ancestor} is an ancestor of {@code node} (or the same node).
	 */
	private static boolean isAncestorOrSelf(ASTNode ancestor, ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current == ancestor) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	/**
	 * Checks if the given modifier flags contain the named modifier.
	 */
	private static boolean matchesModifierName(int modifiers, String modifierName) {
		return switch (modifierName) {
			case "PUBLIC" -> Modifier.isPublic(modifiers); //$NON-NLS-1$
			case "PRIVATE" -> Modifier.isPrivate(modifiers); //$NON-NLS-1$
			case "PROTECTED" -> Modifier.isProtected(modifiers); //$NON-NLS-1$
			case "ABSTRACT" -> Modifier.isAbstract(modifiers); //$NON-NLS-1$
			case "STATIC" -> Modifier.isStatic(modifiers); //$NON-NLS-1$
			case "FINAL" -> Modifier.isFinal(modifiers); //$NON-NLS-1$
			case "SYNCHRONIZED" -> Modifier.isSynchronized(modifiers); //$NON-NLS-1$
			case "VOLATILE" -> Modifier.isVolatile(modifiers); //$NON-NLS-1$
			case "TRANSIENT" -> Modifier.isTransient(modifiers); //$NON-NLS-1$
			case "NATIVE" -> Modifier.isNative(modifiers); //$NON-NLS-1$
			case "STRICTFP" -> Modifier.isStrict(modifiers); //$NON-NLS-1$
			default -> false;
		};
	}

	/**
	 * Checks if the cleanup mode matches a given mode name.
	 * The mode is passed via the {@code sandbox.cleanup.mode} compiler option.
	 *
	 * <p>This guard enables mode-dependent DSL rules, e.g.:</p>
	 * <pre>
	 * $s.getBytes("${CHARSET}") :: sourceVersionGE(7), mode(ENFORCE_UTF8)
	 * =&gt; $s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
	 * ;;
	 * </pre>
	 *
	 * <p>Supported modes: {@code KEEP_BEHAVIOR}, {@code ENFORCE_UTF8},
	 * {@code ENFORCE_UTF8_AGGREGATE}.</p>
	 *
	 * Args: [modeName]
	 */
	private static boolean evaluateMode(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String requiredMode = args[0].toString().trim();
		String currentMode = ctx.getCompilerOptions().get("sandbox.cleanup.mode"); //$NON-NLS-1$
		if (currentMode == null) {
			return false;
		}
		return requiredMode.equalsIgnoreCase(currentMode.trim());
	}

	/**
	 * Checks if a method name (bound to a placeholder) matches a given regex pattern.
	 *
	 * <p>This guard is typically used with {@code METHOD_DECLARATION} patterns to
	 * filter methods by name. The placeholder must be bound to a {@link SimpleName}
	 * (the method name).</p>
	 *
	 * <p>Example DSL usage:</p>
	 * <pre>
	 * void $name($params$) :: methodNameMatches($name, "test.*")
	 * =&gt; addAnnotation @org.junit.jupiter.api.Test
	 * ;;
	 * </pre>
	 *
	 * Args: [placeholderName, regexPattern]
	 * @since 1.3.9
	 */
	private static boolean evaluateMethodNameMatches(GuardContext ctx, Object... args) {
		if (args.length < 2) {
			return false;
		}
		String placeholderName = args[0].toString();
		String regexPattern = stripQuotes(args[1].toString());

		ASTNode node = ctx.getBinding(placeholderName);
		if (node == null) {
			return false;
		}

		String methodName;
		if (node instanceof SimpleName simpleName) {
			methodName = simpleName.getIdentifier();
		} else if (node instanceof MethodDeclaration methodDecl) {
			methodName = methodDecl.getName().getIdentifier();
		} else {
			methodName = node.toString().trim();
		}

		try {
			return methodName.matches(regexPattern);
		} catch (java.util.regex.PatternSyntaxException e) {
			return false;
		}
	}

	/**
	 * Checks if the enclosing class extends a given type (directly or transitively).
	 *
	 * <p>This guard walks the superclass chain of the enclosing class to determine
	 * if it extends the specified type. This is essential for migration rules that
	 * should only apply to classes inheriting from a specific base class (e.g.,
	 * JUnit 3 test classes extending {@code junit.framework.TestCase}).</p>
	 *
	 * <p><b>Graceful degradation:</b> If type bindings cannot be resolved, falls
	 * back to a textual comparison of the {@code extends} clause's simple name
	 * against the last segment of the given FQN. This provides partial matching
	 * in environments where binding resolution is not available, but cannot detect
	 * transitive inheritance without bindings.</p>
	 *
	 * <p>Example DSL usage:</p>
	 * <pre>
	 * void $name($params$) :: methodNameMatches($name, "test.*") &amp;&amp; enclosingClassExtends("junit.framework.TestCase")
	 * =&gt; @org.junit.jupiter.api.Test void $name($params$)
	 * ;;
	 * </pre>
	 *
	 * Args: [fullyQualifiedTypeName]
	 * @since 1.3.10
	 */
	private static boolean evaluateEnclosingClassExtends(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String targetFqn = stripQuotes(args[0].toString());
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		TypeDeclaration typeDecl = findEnclosingTypeDeclaration(node);
		if (typeDecl == null) {
			return false;
		}
		ITypeBinding typeBinding = typeDecl.resolveBinding();
		if (typeBinding != null && !typeBinding.isRecovered()) {
			ITypeBinding superclass = typeBinding.getSuperclass();
			if (superclass != null && !superclass.isRecovered()) {
				return extendsType(superclass, targetFqn, new java.util.HashSet<>());
			}
		}
		// Fallback without reliable bindings: check the extends clause textually (direct superclass only)
		org.eclipse.jdt.core.dom.Type superclassType = typeDecl.getSuperclassType();
		if (superclassType == null) {
			return false;
		}
		String superclassText = superclassType.toString().trim();
		// Match if the extends clause is the FQN or the simple name part of it
		int lastDot = targetFqn.lastIndexOf('.');
		String simpleName = (lastDot >= 0) ? targetFqn.substring(lastDot + 1) : targetFqn;
		return targetFqn.equals(superclassText) || simpleName.equals(superclassText);
	}

	/**
	 * Walks the superclass chain of a type binding to check if it extends the target type.
	 * Uses a visited set to prevent infinite recursion in case of cycles.
	 * Stops at recovered bindings since they are unreliable.
	 */
	private static boolean extendsType(ITypeBinding typeBinding, String targetFqn, java.util.Set<String> visited) {
		if (typeBinding == null || typeBinding.isRecovered()) {
			return false;
		}
		String qualifiedName = typeBinding.getQualifiedName();
		if (!visited.add(qualifiedName)) {
			return false; // Already visited — break potential cycle
		}
		if (targetFqn.equals(qualifiedName)) {
			return true;
		}
		ITypeBinding superclass = typeBinding.getSuperclass();
		return extendsType(superclass, targetFqn, visited);
	}

	/**
	 * Checks if the matched node's enclosing method, field, or type declaration has a
	 * {@code @SuppressWarnings} annotation containing the specified key.
	 *
	 * <p>Walks up the AST from the matched node checking each enclosing
	 * {@link BodyDeclaration} for a {@code @SuppressWarnings} annotation
	 * whose value contains the given key.</p>
	 *
	 * Args: [suppressWarningsKey]
	 * @since 1.4.0
	 */
	private static boolean evaluateHasSuppressWarnings(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String key = stripQuotes(args[0].toString());
		ASTNode node = ctx.getMatchedNode();
		return SuppressWarningsChecker.isSuppressed(node, key);
	}

	/**
	 * Checks if the enclosing class has a field with the given name.
	 *
	 * <p>Walks to the enclosing {@link TypeDeclaration} and iterates its
	 * {@code bodyDeclarations()} looking for a {@link FieldDeclaration}
	 * containing a {@link VariableDeclarationFragment} with the matching name.</p>
	 *
	 * Args: [fieldName]
	 * @since 1.4.1
	 */
	@SuppressWarnings("unchecked")
	private static boolean evaluateHasField(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String fieldName = stripQuotes(args[0].toString());
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		TypeDeclaration typeDecl = findEnclosingTypeDeclaration(node);
		if (typeDecl == null) {
			return false;
		}
		for (Object bodyDecl : typeDecl.bodyDeclarations()) {
			if (bodyDecl instanceof FieldDeclaration fieldDecl) {
				for (Object frag : fieldDecl.fragments()) {
					if (frag instanceof VariableDeclarationFragment vdf
							&& fieldName.equals(vdf.getName().getIdentifier())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Checks if the matched node is inside a loop construct.
	 *
	 * <p>Walks up from the matched node checking if any parent is a
	 * {@code ForStatement}, {@code WhileStatement}, {@code DoStatement},
	 * or {@code EnhancedForStatement}.</p>
	 *
	 * Args: none
	 * @since 1.4.1
	 */
	private static boolean evaluateIsInLoop(GuardContext ctx, Object... args) {
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		ASTNode current = node.getParent();
		while (current != null) {
			int nodeType = current.getNodeType();
			if (nodeType == ASTNode.FOR_STATEMENT
					|| nodeType == ASTNode.WHILE_STATEMENT
					|| nodeType == ASTNode.DO_STATEMENT
					|| nodeType == ASTNode.ENHANCED_FOR_STATEMENT) {
				return true;
			}
			current = current.getParent();
		}
		return false;
	}

	/**
	 * Checks if the enclosing method's parameter count matches the expected value.
	 *
	 * <p>Finds the enclosing {@link MethodDeclaration} and compares its
	 * parameter count with the expected value.</p>
	 *
	 * Args: [expectedCount]
	 * @since 1.4.1
	 */
	private static boolean evaluateParamCount(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		int expectedCount;
		try {
			expectedCount = Integer.parseInt(args[0].toString().trim());
		} catch (NumberFormatException e) {
			return false;
		}
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		MethodDeclaration methodDecl = findEnclosingMethodDeclaration(node);
		if (methodDecl == null) {
			return false;
		}
		return methodDecl.parameters().size() == expectedCount;
	}

	/**
	 * Checks if the enclosing method's return type matches the given type name.
	 *
	 * <p>With one argument, checks the enclosing method's return type.
	 * With two arguments, the first is a placeholder name (ignored for now)
	 * and the second is the type name to match.</p>
	 *
	 * Args: [typeName] or [placeholderName, typeName]
	 * @since 1.4.1
	 */
	private static boolean evaluateHasReturnType(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String typeName;
		if (args.length >= 2) {
			typeName = stripQuotes(args[1].toString());
		} else {
			typeName = stripQuotes(args[0].toString());
		}
		ASTNode node = ctx.getMatchedNode();
		if (node == null) {
			return false;
		}
		MethodDeclaration methodDecl = findEnclosingMethodDeclaration(node);
		if (methodDecl == null) {
			return false;
		}
		org.eclipse.jdt.core.dom.Type returnType = methodDecl.getReturnType2();
		if (returnType == null) {
			return "void".equals(typeName); //$NON-NLS-1$
		}
		String returnTypeStr = returnType.toString().trim();
		return typeName.equals(returnTypeStr);
	}

	/**
	 * Checks if the bound placeholder is a {@link StringLiteral} node.
	 *
	 * Args: [placeholderName]
	 * @since 1.4.1
	 */
	private static boolean evaluateIsStringLiteral(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		ASTNode node = ctx.getBinding(args[0].toString());
		return node instanceof StringLiteral;
	}

	/**
	 * Finds the nearest enclosing MethodDeclaration for an AST node.
	 */
	private static MethodDeclaration findEnclosingMethodDeclaration(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof MethodDeclaration methodDecl) {
				return methodDecl;
			}
			current = current.getParent();
		}
		return null;
	}
}
