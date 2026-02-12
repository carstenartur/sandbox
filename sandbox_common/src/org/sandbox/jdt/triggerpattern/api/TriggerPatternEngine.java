/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.triggerpattern.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;
import org.sandbox.jdt.triggerpattern.internal.PlaceholderAstMatcher;

/**
 * Engine for finding pattern matches in Java code.
 * 
 * <p>This engine traverses a compilation unit and identifies all occurrences
 * that match a given pattern. It supports expression, statement, annotation,
 * method call, import, field, constructor, and method declaration patterns
 * with placeholder binding.</p>
 * 
 * <p><b>Supported Pattern Kinds:</b></p>
 * <ul>
 *   <li>{@link PatternKind#EXPRESSION} - Expressions like {@code $x + 1}</li>
 *   <li>{@link PatternKind#STATEMENT} - Statements like {@code return $x;}</li>
 *   <li>{@link PatternKind#ANNOTATION} - Annotations like {@code @Before}</li>
 *   <li>{@link PatternKind#METHOD_CALL} - Method invocations like {@code assertEquals($a, $b)}</li>
 *   <li>{@link PatternKind#IMPORT} - Import declarations</li>
 *   <li>{@link PatternKind#FIELD} - Field declarations</li>
 *   <li>{@link PatternKind#CONSTRUCTOR} - Constructor invocations like {@code new String($bytes)}</li>
 *   <li>{@link PatternKind#METHOD_DECLARATION} - Method declarations like {@code void dispose()}</li>
 * </ul>
 * 
 * @since 1.2.2
 */
public class TriggerPatternEngine {
	
	private final PatternParser parser = new PatternParser();
	
	/**
	 * Finds all matches of the given pattern in the compilation unit.
	 * 
	 * @param cu the compilation unit to search
	 * @param pattern the pattern to match
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatches(CompilationUnit cu, Pattern pattern) {
		if (cu == null || pattern == null) {
			return List.of();
		}
		
		ASTNode patternNode = parser.parse(pattern);
		if (patternNode == null) {
			return List.of();
		}
		
		List<Match> matches = new ArrayList<>();
		
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				// Only check nodes of compatible types
				if (pattern.getKind() == PatternKind.EXPRESSION && node instanceof Expression) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.STATEMENT && node instanceof Statement) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.ANNOTATION && node instanceof Annotation) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.METHOD_CALL && node instanceof MethodInvocation) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.IMPORT && node instanceof ImportDeclaration) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.FIELD && node instanceof FieldDeclaration) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.CONSTRUCTOR && node instanceof ClassInstanceCreation) {
					checkMatch(node, patternNode, matches);
				} else if (pattern.getKind() == PatternKind.METHOD_DECLARATION && node instanceof MethodDeclaration) {
					checkMethodDeclarationMatch((MethodDeclaration) node, patternNode, pattern, matches);
				}
			}
		});
		
		return matches;
	}
	
	/**
	 * Finds all matches of the given pattern in the compilation unit.
	 * 
	 * <p>When the pattern specifies an overrides constraint, binding resolution
	 * is enabled to support override detection via {@link IMethodBinding}.</p>
	 * 
	 * @param icu the ICompilationUnit to search
	 * @param pattern the pattern to match
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatches(ICompilationUnit icu, Pattern pattern) {
		if (icu == null || pattern == null) {
			return List.of();
		}
		
		boolean needsBindings = pattern.getOverridesType() != null;
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(icu);
		astParser.setResolveBindings(needsBindings);
		if (needsBindings) {
			astParser.setProject(icu.getJavaProject());
		}
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		
		return findMatches(cu, pattern);
	}
	
	/**
	 * Finds all AST nodes of the given types in the compilation unit.
	 * 
	 * <p>This method is used for @TriggerTreeKind hints that match based on
	 * AST node types rather than patterns.</p>
	 * 
	 * @param cu the compilation unit to search
	 * @param nodeTypes array of AST node type constants (e.g., ASTNode.METHOD_DECLARATION)
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatchesByNodeType(CompilationUnit cu, int... nodeTypes) {
		if (cu == null || nodeTypes == null || nodeTypes.length == 0) {
			return List.of();
		}
		
		List<Match> matches = new ArrayList<>();
		
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				int nodeType = node.getNodeType();
				for (int targetType : nodeTypes) {
					if (nodeType == targetType) {
						createMatchWithAutoBindings(node, matches);
						break;
					}
				}
			}
		});
		
		return matches;
	}
	
	/**
	 * Finds all AST nodes of the given types in the compilation unit.
	 * 
	 * <p>This method is used for @TriggerTreeKind hints that match based on
	 * AST node types rather than patterns.</p>
	 * 
	 * @param icu the ICompilationUnit to search
	 * @param nodeTypes array of AST node type constants (e.g., ASTNode.METHOD_DECLARATION)
	 * @return a list of all matches (may be empty)
	 */
	public List<Match> findMatchesByNodeType(ICompilationUnit icu, int... nodeTypes) {
		if (icu == null || nodeTypes == null || nodeTypes.length == 0) {
			return List.of();
		}
		
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(icu);
		astParser.setResolveBindings(false);
		CompilationUnit cu = (CompilationUnit) astParser.createAST(null);
		
		return findMatchesByNodeType(cu, nodeTypes);
	}
	
	/**
	 * Finds all matches of the given pattern in the compilation unit, applying
	 * body constraints to filter the results.
	 * 
	 * <p>This method extends {@link #findMatches(CompilationUnit, Pattern)} by also
	 * checking the body of matched METHOD_DECLARATION nodes. The body constraint
	 * specifies a pattern that must (or must not, when negated) be present in the
	 * method body.</p>
	 * 
	 * @param cu the compilation unit to search
	 * @param pattern the pattern to match
	 * @param bodyConstraintPattern the pattern to look for in the method body
	 * @param bodyConstraintKind the kind of the body constraint pattern
	 * @param negate if {@code true}, the constraint triggers when the pattern is NOT found
	 * @return a list of all matches that satisfy both the pattern and body constraint (may be empty)
	 * @since 1.2.6
	 */
	public List<Match> findMatchesWithConstraints(CompilationUnit cu, Pattern pattern,
			String bodyConstraintPattern, PatternKind bodyConstraintKind, boolean negate) {
		if (cu == null || pattern == null || bodyConstraintPattern == null) {
			return List.of();
		}

		List<Match> signatureMatches = findMatches(cu, pattern);

		if (pattern.getKind() != PatternKind.METHOD_DECLARATION || bodyConstraintPattern.isEmpty()) {
			return signatureMatches;
		}

		Pattern bodyPattern = new Pattern(bodyConstraintPattern, bodyConstraintKind);
		ASTNode bodyPatternNode = parser.parse(bodyPattern);
		if (bodyPatternNode == null) {
			return signatureMatches;
		}

		List<Match> result = new ArrayList<>();
		for (Match match : signatureMatches) {
			ASTNode matchedNode = match.getMatchedNode();
			if (matchedNode instanceof MethodDeclaration methodDecl) {
				Block body = methodDecl.getBody();
				if (body != null) {
					boolean found = containsPattern(body, bodyPatternNode);
					// negate=true means trigger when pattern NOT found; negate=false means trigger when found
					if (found != negate) {
						result.add(match);
					}
				}
			}
		}
		return result;
	}

	/**
	 * Checks if a method declaration matches the pattern and satisfies the override constraint.
	 * 
	 * <p>When the pattern specifies an overrides type, this method checks whether the
	 * candidate method overrides a method from the specified type using binding resolution.
	 * If binding resolution is not available (e.g., when parsing from source strings without
	 * a project context), the override check is skipped.</p>
	 * 
	 * @param candidate the candidate method declaration
	 * @param patternNode the parsed pattern AST node
	 * @param pattern the pattern with potential overrides constraint
	 * @param matches the list to add the match to
	 */
	private void checkMethodDeclarationMatch(MethodDeclaration candidate, ASTNode patternNode,
			Pattern pattern, List<Match> matches) {
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();

		if (!patternNode.subtreeMatch(matcher, candidate)) {
			return;
		}

		// Check override constraint if specified
		String overridesType = pattern.getOverridesType();
		if (overridesType != null && !overridesType.isEmpty()) {
			if (!checkOverrides(candidate, overridesType)) {
				return;
			}
		}

		Map<String, Object> bindings = new HashMap<>(matcher.getBindings());
		createMatchWithAutoBindings(candidate, bindings, matches);
	}

	/**
	 * Checks if a method declaration overrides a method from the specified type.
	 * 
	 * <p>Uses binding resolution to traverse the type hierarchy and find overridden methods.
	 * Returns {@code false} if bindings are not available (e.g., when parsing from raw source).</p>
	 * 
	 * @param method the method declaration to check
	 * @param overridesType the fully qualified type name that must declare the overridden method
	 * @return {@code true} if the method overrides a method from the specified type
	 */
	private boolean checkOverrides(MethodDeclaration method, String overridesType) {
		IMethodBinding methodBinding = method.resolveBinding();
		if (methodBinding == null) {
			return false;
		}

		ITypeBinding declaringClass = methodBinding.getDeclaringClass();
		if (declaringClass == null) {
			return false;
		}

		// Walk the superclass chain looking for the overridden method
		ITypeBinding superClass = declaringClass.getSuperclass();
		while (superClass != null) {
			if (overridesType.equals(superClass.getQualifiedName())) {
				// Check if the super type declares a method with this signature
				for (IMethodBinding superMethod : superClass.getDeclaredMethods()) {
					if (methodBinding.overrides(superMethod)) {
						return true;
					}
				}
			}
			superClass = superClass.getSuperclass();
		}

		// Also check interfaces
		return checkOverridesInInterfaces(methodBinding, declaringClass, overridesType);
	}

	/**
	 * Checks if a method overrides a method declared in an interface matching the specified type.
	 * 
	 * @param methodBinding the method binding to check
	 * @param typeBinding the declaring type
	 * @param overridesType the target interface type
	 * @return {@code true} if the method overrides a method from a matching interface
	 */
	private boolean checkOverridesInInterfaces(IMethodBinding methodBinding, ITypeBinding typeBinding,
			String overridesType) {
		for (ITypeBinding iface : typeBinding.getInterfaces()) {
			if (overridesType.equals(iface.getQualifiedName())) {
				for (IMethodBinding ifaceMethod : iface.getDeclaredMethods()) {
					if (methodBinding.overrides(ifaceMethod)) {
						return true;
					}
				}
			}
			// Recursively check super-interfaces
			if (checkOverridesInInterfaces(methodBinding, iface, overridesType)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks if a method body contains a match for the given pattern.
	 * 
	 * <p>Searches the entire body subtree, including nested blocks and
	 * control flow structures.</p>
	 * 
	 * @param body the method body to search
	 * @param bodyPatternNode the pattern AST node to look for
	 * @return {@code true} if the pattern is found in the body
	 */
	private boolean containsPattern(Block body, ASTNode bodyPatternNode) {
		boolean[] found = {false};
		body.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (found[0]) {
					return;
				}
				// Try matching against statements
				if (node instanceof Statement || node instanceof Expression) {
					PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
					if (bodyPatternNode.subtreeMatch(matcher, node)) {
						found[0] = true;
					}
				}
			}
		});
		return found[0];
	}

	/**
	 * Checks if a candidate node matches the pattern node and adds a Match if it does.
	 * 
	 * <p>Auto-bindings are added:</p>
	 * <ul>
	 *   <li>{@code $_} - the matched node itself</li>
	 *   <li>{@code $this} - the enclosing AbstractTypeDeclaration</li>
	 * </ul>
	 */
	private void checkMatch(ASTNode candidate, ASTNode patternNode, List<Match> matches) {
		PlaceholderAstMatcher matcher = new PlaceholderAstMatcher();
		
		if (patternNode.subtreeMatch(matcher, candidate)) {
			// We have a match! Create a Match object with pattern bindings and auto-bindings
			Map<String, Object> bindings = new HashMap<>(matcher.getBindings());
			createMatchWithAutoBindings(candidate, bindings, matches);
		}
	}
	
	/**
	 * Creates a Match with auto-bindings for a matched node.
	 * 
	 * @param node the matched node
	 * @param matches the list to add the match to
	 */
	private void createMatchWithAutoBindings(ASTNode node, List<Match> matches) {
		createMatchWithAutoBindings(node, new HashMap<>(), matches);
	}
	
	/**
	 * Creates a Match with auto-bindings for a matched node.
	 * 
	 * @param node the matched node
	 * @param bindings existing bindings (e.g., from pattern matching)
	 * @param matches the list to add the match to
	 */
	private void createMatchWithAutoBindings(ASTNode node, Map<String, Object> bindings, List<Match> matches) {
		int offset = node.getStartPosition();
		int length = node.getLength();
		
		// Add auto-bindings
		bindings.put("$_", node); //$NON-NLS-1$
		
		// Find enclosing type declaration
		ASTNode enclosingType = findEnclosingType(node);
		if (enclosingType != null) {
			bindings.put("$this", enclosingType); //$NON-NLS-1$
		}
		
		Match match = new Match(node, bindings, offset, length);
		matches.add(match);
	}
	
	/**
	 * Finds the enclosing type declaration for a given node.
	 * 
	 * <p>This includes classes, interfaces, enums, annotation types, and records.</p>
	 * 
	 * @param node the node to start from
	 * @return the enclosing AbstractTypeDeclaration, or null if none found
	 */
	private ASTNode findEnclosingType(ASTNode node) {
		ASTNode current = node;
		while (current != null) {
			if (current instanceof AbstractTypeDeclaration) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}
}
