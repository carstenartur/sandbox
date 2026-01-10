/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer and others.
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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.IAnnotationBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/**
 * Utility class for resolving variable types in AST nodes.
 * 
 * <p>
 * This class provides centralized type resolution functionality for the
 * functional converter. It walks the AST tree to find variable declarations
 * and extract type information, which is critical for generating type-aware
 * stream operations.
 * </p>
 * 
 * <p><b>Key Functionality:</b></p>
 * <ul>
 * <li>Variable type resolution across scopes</li>
 * <li>Type binding extraction</li>
 * <li>Annotation checking (@NotNull, @NonNull)</li>
 * <li>Support for primitive and reference types</li>
 * </ul>
 * 
 * @see StreamPipelineBuilder
 * @see ReducerPatternDetector
 */
public final class TypeResolver {

	/**
	 * Private constructor to prevent instantiation of this utility class.
	 */
	private TypeResolver() {
		// Utility class - no instances allowed
	}

	/**
	 * Attempts to determine the type name of a variable by searching for its
	 * declaration in the AST tree.
	 * 
	 * <p>
	 * This method walks up the AST tree starting from the given node, searching
	 * through all parent scopes (blocks, methods, initializers, lambdas) to find
	 * the variable declaration. It returns the simple type name (e.g., "int",
	 * "String", "double") if found.
	 * </p>
	 * 
	 * <p><b>Supported Scopes:</b></p>
	 * <ul>
	 * <li>Block statements</li>
	 * <li>Method declarations</li>
	 * <li>Initializer blocks (instance or static)</li>
	 * <li>Lambda expressions</li>
	 * </ul>
	 * 
	 * <p><b>Examples:</b></p>
	 * <pre>{@code
	 * // Finds variable in method scope
	 * public void method() {
	 *     int count = 0;  // getVariableType("count") → "int"
	 *     for (Item item : items) {
	 *         count++;
	 *     }
	 * }
	 * 
	 * // Finds variable in block scope
	 * {
	 *     double sum = 0.0;  // getVariableType("sum") → "double"
	 *     for (Number n : numbers) {
	 *         sum += n.doubleValue();
	 *     }
	 * }
	 * }</pre>
	 * 
	 * @param startNode the starting node for the search (typically a loop node)
	 *                  (must not be null)
	 * @param varName   the variable name to look up (must not be null)
	 * @return the simple type name (e.g., "double", "int", "String") or null if not
	 *         found
	 * @throws IllegalArgumentException if startNode or varName is null
	 */
	public static String getVariableType(ASTNode startNode, String varName) {
		if (startNode == null) {
			throw new IllegalArgumentException("startNode cannot be null");
		}
		if (varName == null) {
			throw new IllegalArgumentException("varName cannot be null");
		}

		// Walk up the AST tree searching for the variable in each scope
		ASTNode currentNode = startNode.getParent();

		while (currentNode != null) {
			// Search in blocks
			if (currentNode instanceof Block) {
				Block block = (Block) currentNode;
				String type = searchBlockForVariableType(block, varName);
				if (type != null) {
					return type;
				}
			}
			// Search in method bodies
			else if (currentNode instanceof org.eclipse.jdt.core.dom.MethodDeclaration) {
				org.eclipse.jdt.core.dom.MethodDeclaration method = (org.eclipse.jdt.core.dom.MethodDeclaration) currentNode;
				if (method.getBody() != null) {
					String type = searchBlockForVariableType(method.getBody(), varName);
					if (type != null) {
						return type;
					}
				}
			}
			// Search in initializer blocks (instance or static)
			else if (currentNode instanceof org.eclipse.jdt.core.dom.Initializer) {
				org.eclipse.jdt.core.dom.Initializer initializer = (org.eclipse.jdt.core.dom.Initializer) currentNode;
				if (initializer.getBody() != null) {
					String type = searchBlockForVariableType(initializer.getBody(), varName);
					if (type != null) {
						return type;
					}
				}
			}
			// Search in lambda expressions
			else if (currentNode instanceof org.eclipse.jdt.core.dom.LambdaExpression) {
				org.eclipse.jdt.core.dom.LambdaExpression lambda = (org.eclipse.jdt.core.dom.LambdaExpression) currentNode;
				if (lambda.getBody() instanceof Block) {
					String type = searchBlockForVariableType((Block) lambda.getBody(), varName);
					if (type != null) {
						return type;
					}
				}
			}

			// Move up to parent scope
			currentNode = currentNode.getParent();
		}

		return null;
	}

	/**
	 * Searches a block for a variable declaration and returns its type.
	 * 
	 * <p>
	 * This method iterates through all statements in the block looking for
	 * {@link VariableDeclarationStatement}s that declare the specified variable. It
	 * handles primitive types, simple types, and array types, using bindings when
	 * available for accurate type resolution.
	 * </p>
	 * 
	 * <p><b>Type Resolution Strategy:</b></p>
	 * <ol>
	 * <li>Primitive types: Returns the primitive type code (e.g., "int", "double")</li>
	 * <li>Simple types with bindings: Returns the binding's simple name</li>
	 * <li>Simple types without bindings: Returns the fully qualified name</li>
	 * <li>Array types: Recursively resolves element type and appends "[]"</li>
	 * <li>Other types: Returns the string representation of the type</li>
	 * </ol>
	 * 
	 * @param block   the block to search (may be null)
	 * @param varName the variable name to find (must not be null)
	 * @return the simple type name or null if not found
	 * @throws IllegalArgumentException if varName is null
	 */
	public static String searchBlockForVariableType(Block block, String varName) {
		if (varName == null) {
			throw new IllegalArgumentException("varName cannot be null");
		}

		if (block == null) {
			return null;
		}

		for (Object stmtObj : block.statements()) {
			if (stmtObj instanceof VariableDeclarationStatement) {
				VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmtObj;
				for (Object fragObj : varDecl.fragments()) {
					if (fragObj instanceof VariableDeclarationFragment) {
						VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
						if (frag.getName().getIdentifier().equals(varName)) {
							org.eclipse.jdt.core.dom.Type type = varDecl.getType();
							// Robustly extract the simple type name
							if (type.isPrimitiveType()) {
								// For primitive types: int, double, etc.
								return ((org.eclipse.jdt.core.dom.PrimitiveType) type).getPrimitiveTypeCode().toString();
							} else if (type.isSimpleType()) {
								// For reference types: get the simple name
								org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) type;
								// Try to use binding if available
								ITypeBinding binding = simpleType.resolveBinding();
								if (binding != null) {
									return binding.getName();
								} else {
									return simpleType.getName().getFullyQualifiedName();
								}
							} else if (type.isArrayType()) {
								// For array types, get the element type recursively and append "[]"
								org.eclipse.jdt.core.dom.ArrayType arrayType = (org.eclipse.jdt.core.dom.ArrayType) type;
								org.eclipse.jdt.core.dom.Type elementType = arrayType.getElementType();
								String elementTypeName;
								if (elementType.isPrimitiveType()) {
									elementTypeName = ((org.eclipse.jdt.core.dom.PrimitiveType) elementType)
											.getPrimitiveTypeCode().toString();
								} else if (elementType.isSimpleType()) {
									org.eclipse.jdt.core.dom.SimpleType simpleType = (org.eclipse.jdt.core.dom.SimpleType) elementType;
									ITypeBinding binding = simpleType.resolveBinding();
									if (binding != null) {
										elementTypeName = binding.getName();
									} else {
										elementTypeName = simpleType.getName().getFullyQualifiedName();
									}
								} else {
									// Fallback for other types
									elementTypeName = elementType.toString();
								}
								return elementTypeName + "[]";
							} else {
								// Fallback for other types (e.g., parameterized, qualified, etc.)
								return type.toString();
							}
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Gets the type binding for a variable name.
	 * 
	 * <p>
	 * This method finds the variable declaration in the AST tree and returns its
	 * {@link ITypeBinding}. Type bindings provide access to full type information
	 * including qualified names, which is useful for distinguishing between
	 * different types with the same simple name.
	 * </p>
	 * 
	 * <p><b>Use Cases:</b></p>
	 * <ul>
	 * <li>Distinguishing {@code String} concatenation from numeric addition</li>
	 * <li>Checking if a type is a specific class (e.g., {@code java.lang.String})</li>
	 * <li>Accessing type hierarchy information</li>
	 * </ul>
	 * 
	 * @param startNode the starting node for the search (must not be null)
	 * @param varName   the variable name (must not be null)
	 * @return the type binding, or null if not found
	 * @throws IllegalArgumentException if startNode or varName is null
	 */
	public static ITypeBinding getTypeBinding(ASTNode startNode, String varName) {
		if (startNode == null) {
			throw new IllegalArgumentException("startNode cannot be null");
		}
		if (varName == null) {
			throw new IllegalArgumentException("varName cannot be null");
		}

		VariableDeclarationFragment frag = findVariableDeclaration(startNode, varName);
		if (frag != null) {
			IVariableBinding binding = frag.resolveBinding();
			if (binding != null) {
				return binding.getType();
			}
		}
		return null;
	}

	/**
	 * Finds the variable declaration fragment for a given variable name.
	 * 
	 * <p>
	 * This method searches up the AST tree starting from the given node to find the
	 * {@link VariableDeclarationFragment} that declares the specified variable. It
	 * searches through all blocks in parent scopes.
	 * </p>
	 * 
	 * <p><b>Search Strategy:</b></p>
	 * <ol>
	 * <li>Start from the given node and walk up to parent nodes</li>
	 * <li>For each Block encountered, search its statements</li>
	 * <li>Look for VariableDeclarationStatements containing the variable</li>
	 * <li>Return the first matching VariableDeclarationFragment found</li>
	 * </ol>
	 * 
	 * @param startNode the starting node for the search (must not be null)
	 * @param varName   the variable name to find (must not be null)
	 * @return the VariableDeclarationFragment, or null if not found
	 * @throws IllegalArgumentException if startNode or varName is null
	 */
	public static VariableDeclarationFragment findVariableDeclaration(ASTNode startNode, String varName) {
		if (startNode == null) {
			throw new IllegalArgumentException("startNode cannot be null");
		}
		if (varName == null) {
			throw new IllegalArgumentException("varName cannot be null");
		}

		// Try to find the variable declaration in the AST
		// Start from the given node and walk up to find variable declarations
		ASTNode current = startNode;
		while (current != null) {
			if (current instanceof Block) {
				Block block = (Block) current;
				for (Object stmtObj : block.statements()) {
					if (stmtObj instanceof VariableDeclarationStatement) {
						VariableDeclarationStatement varDecl = (VariableDeclarationStatement) stmtObj;
						for (Object fragObj : varDecl.fragments()) {
							if (fragObj instanceof VariableDeclarationFragment) {
								VariableDeclarationFragment frag = (VariableDeclarationFragment) fragObj;
								if (varName.equals(frag.getName().getIdentifier())) {
									return frag;
								}
							}
						}
					}
				}
			}
			current = current.getParent();
		}

		return null;
	}

	/**
	 * Checks if a variable has a @NotNull or @NonNull annotation.
	 * 
	 * <p>
	 * This is used to determine if certain stream operations can be safely used.
	 * For example, String::concat can be safely used instead of a null-safe lambda
	 * (a, b) -> a + b when the accumulator variable is guaranteed non-null.
	 * </p>
	 * 
	 * <p><b>Supported Annotations:</b></p>
	 * <ul>
	 * <li>{@code @NotNull} (various packages)</li>
	 * <li>{@code @NonNull} (various packages)</li>
	 * </ul>
	 * 
	 * <p><b>Example:</b></p>
	 * <pre>{@code
	 * @NotNull String result = "";
	 * for (String s : strings) {
	 *     result += s;  // Can use String::concat safely
	 * }
	 * }</pre>
	 * 
	 * @param startNode the starting node for the search (must not be null)
	 * @param varName   the variable name to check (must not be null)
	 * @return true if the variable has a @NotNull or @NonNull annotation
	 * @throws IllegalArgumentException if startNode or varName is null
	 */
	public static boolean hasNotNullAnnotation(ASTNode startNode, String varName) {
		if (startNode == null) {
			throw new IllegalArgumentException("startNode cannot be null");
		}
		if (varName == null) {
			throw new IllegalArgumentException("varName cannot be null");
		}

		VariableDeclarationFragment frag = findVariableDeclaration(startNode, varName);
		if (frag != null) {
			IVariableBinding binding = frag.resolveBinding();
			if (binding != null) {
				return hasNotNullAnnotationOnBinding(binding);
			}
		}
		return false;
	}

	/**
	 * Checks if a binding has @NotNull or @NonNull annotation.
	 * 
	 * <p>
	 * This is a helper method that examines the annotations on a variable binding
	 * to determine if it has a non-null annotation from any package. The check is
	 * done by examining the qualified name of each annotation type and looking for
	 * names ending with ".NotNull" or ".NonNull".
	 * </p>
	 * 
	 * @param binding the variable binding to check (may be null)
	 * @return true if the binding has a @NotNull or @NonNull annotation
	 */
	public static boolean hasNotNullAnnotationOnBinding(IVariableBinding binding) {
		if (binding == null) {
			return false;
		}

		IAnnotationBinding[] annotations = binding.getAnnotations();
		if (annotations != null) {
			for (IAnnotationBinding annotation : annotations) {
				// Check for qualified names (handles different null-safety annotation packages)
				ITypeBinding annotationType = annotation.getAnnotationType();
				if (annotationType != null) {
					String qualifiedName = annotationType.getQualifiedName();
					if (qualifiedName != null
							&& (qualifiedName.endsWith(".NotNull") || qualifiedName.endsWith(".NonNull"))) {
						return true;
					}
				}
			}
		}

		return false;
	}
}
