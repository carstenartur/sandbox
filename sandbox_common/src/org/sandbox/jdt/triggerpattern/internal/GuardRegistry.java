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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.sandbox.jdt.triggerpattern.api.GuardContext;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;

/**
 * Registry for guard functions with built-in guards.
 * 
 * <p>The registry is a singleton that manages guard functions by name. It comes
 * pre-loaded with built-in guards for common checks such as type testing,
 * modifier inspection, source version comparison, and annotation checking.</p>
 * 
 * <h2>Built-in Guards</h2>
 * <table>
 *   <caption>Available built-in guard functions</caption>
 *   <tr><th>Name</th><th>Description</th></tr>
 *   <tr><td>{@code instanceof}</td><td>Checks if a binding's type matches a given type name</td></tr>
 *   <tr><td>{@code matchesAny}</td><td>Returns {@code true} if a binding exists and is non-null</td></tr>
 *   <tr><td>{@code matchesNone}</td><td>Returns {@code true} if a binding does not exist or is null</td></tr>
 *   <tr><td>{@code hasNoSideEffect}</td><td>Checks if an expression has no side effects</td></tr>
 *   <tr><td>{@code sourceVersionGE}</td><td>Checks if the source version is greater than or equal to a given version</td></tr>
 *   <tr><td>{@code sourceVersionLE}</td><td>Checks if the source version is less than or equal to a given version</td></tr>
 *   <tr><td>{@code sourceVersionBetween}</td><td>Checks if the source version is within a given range</td></tr>
 *   <tr><td>{@code isStatic}</td><td>Checks if a binding has the static modifier</td></tr>
 *   <tr><td>{@code isFinal}</td><td>Checks if a binding has the final modifier</td></tr>
 *   <tr><td>{@code hasAnnotation}</td><td>Checks if a binding has a specific annotation</td></tr>
 *   <tr><td>{@code isDeprecated}</td><td>Checks if a binding is deprecated</td></tr>
 * </table>
 * 
 * @since 1.3.2
 */
public final class GuardRegistry {
	
	private static final GuardRegistry INSTANCE = new GuardRegistry();
	
	private final Map<String, GuardFunction> guards = new ConcurrentHashMap<>();
	
	private GuardRegistry() {
		registerBuiltins();
	}
	
	/**
	 * Returns the singleton instance.
	 * 
	 * @return the guard registry instance
	 */
	public static GuardRegistry getInstance() {
		return INSTANCE;
	}
	
	/**
	 * Registers a guard function with the given name.
	 * 
	 * @param name the guard function name
	 * @param fn the guard function
	 */
	public void register(String name, GuardFunction fn) {
		guards.put(name, fn);
	}
	
	/**
	 * Returns the guard function registered under the given name.
	 * 
	 * @param name the guard function name
	 * @return the guard function, or {@code null} if not found
	 */
	public GuardFunction get(String name) {
		return guards.get(name);
	}
	
	/**
	 * Registers all built-in guard functions.
	 */
	private void registerBuiltins() {
		// Type guards
		register("instanceof", this::evaluateInstanceOf); //$NON-NLS-1$
		
		// Structural guards
		register("matchesAny", this::evaluateMatchesAny); //$NON-NLS-1$
		register("matchesNone", this::evaluateMatchesNone); //$NON-NLS-1$
		register("hasNoSideEffect", this::evaluateHasNoSideEffect); //$NON-NLS-1$
		
		// Java version guards
		register("sourceVersionGE", this::evaluateSourceVersionGE); //$NON-NLS-1$
		register("sourceVersionLE", this::evaluateSourceVersionLE); //$NON-NLS-1$
		register("sourceVersionBetween", this::evaluateSourceVersionBetween); //$NON-NLS-1$
		
		// Element kind guards
		register("isStatic", this::evaluateIsStatic); //$NON-NLS-1$
		register("isFinal", this::evaluateIsFinal); //$NON-NLS-1$
		
		// Annotation guards
		register("hasAnnotation", this::evaluateHasAnnotation); //$NON-NLS-1$
		register("isDeprecated", this::evaluateIsDeprecated); //$NON-NLS-1$
	}
	
	/**
	 * Checks if the bound node's type matches a given type name via ITypeBinding.
	 * Args: [placeholderName, typeName]
	 */
	private boolean evaluateInstanceOf(GuardContext ctx, Object... args) {
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
		
		return matchesTypeName(typeBinding, typeName);
	}
	
	/**
	 * Returns true if a binding exists and is non-null.
	 * Args: [placeholderName]
	 */
	private boolean evaluateMatchesAny(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return false;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node != null) {
			return true;
		}
		List<ASTNode> listBinding = ctx.getListBinding(placeholderName);
		return !listBinding.isEmpty();
	}
	
	/**
	 * Returns true if a binding does not exist or is null.
	 * Args: [placeholderName]
	 */
	private boolean evaluateMatchesNone(GuardContext ctx, Object... args) {
		if (args.length < 1) {
			return true;
		}
		String placeholderName = args[0].toString();
		ASTNode node = ctx.getBinding(placeholderName);
		if (node != null) {
			return false;
		}
		List<ASTNode> listBinding = ctx.getListBinding(placeholderName);
		return listBinding.isEmpty();
	}
	
	/**
	 * Checks if an expression has no side effects.
	 * Currently checks that the node is not a method invocation (conservative check).
	 * Args: [placeholderName]
	 */
	private boolean evaluateHasNoSideEffect(GuardContext ctx, Object... args) {
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
	private boolean evaluateSourceVersionGE(GuardContext ctx, Object... args) {
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
	private boolean evaluateSourceVersionLE(GuardContext ctx, Object... args) {
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
	private boolean evaluateSourceVersionBetween(GuardContext ctx, Object... args) {
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
	private boolean evaluateIsStatic(GuardContext ctx, Object... args) {
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
	private boolean evaluateIsFinal(GuardContext ctx, Object... args) {
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
	private boolean evaluateHasAnnotation(GuardContext ctx, Object... args) {
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
	private boolean evaluateIsDeprecated(GuardContext ctx, Object... args) {
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
	 * Finds the nearest enclosing BodyDeclaration for an AST node.
	 */
	private BodyDeclaration findEnclosingBodyDeclaration(ASTNode node) {
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
	 * Resolves the type binding for an AST node.
	 */
	private ITypeBinding resolveTypeBinding(ASTNode node) {
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
		if (node instanceof org.eclipse.jdt.core.dom.Expression expr) {
			return expr.resolveTypeBinding();
		}
		return null;
	}
	
	/**
	 * Checks if a type binding matches a given type name (simple or qualified).
	 */
	private boolean matchesTypeName(ITypeBinding typeBinding, String typeName) {
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
	private IBinding resolveBinding(ASTNode node) {
		if (node instanceof SimpleName simpleName) {
			return simpleName.resolveBinding();
		}
		if (node instanceof MethodDeclaration methodDecl) {
			return methodDecl.resolveBinding();
		}
		return null;
	}
	
	/**
	 * Resolves the modifiers for an AST node via its binding.
	 */
	private int resolveModifiers(ASTNode node) {
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
	private String getAnnotationName(Annotation annotation) {
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
	private double parseVersion(String version) {
		if (version == null || version.isEmpty()) {
			return 0;
		}
		try {
			return Double.parseDouble(version);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
