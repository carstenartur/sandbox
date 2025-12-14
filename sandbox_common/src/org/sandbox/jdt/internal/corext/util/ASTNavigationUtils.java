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
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.util;

import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

/**
 * Utility class for navigating and finding nodes in the AST tree.
 * Provides common operations for locating type declarations, compilation units,
 * and other AST elements needed during JUnit migration.
 */
public final class ASTNavigationUtils {

	private ASTNavigationUtils() {
		// Utility class - prevent instantiation
	}

	/**
	 * Finds the CompilationUnit that contains the given AST node.
	 * 
	 * @param node the AST node to start from
	 * @return the containing CompilationUnit, or null if not found
	 */
	public static CompilationUnit findCompilationUnit(ASTNode node) {
		return ASTNodes.getTypedAncestor(node, CompilationUnit.class);
	}

	/**
	 * Gets the parent TypeDeclaration for the given AST node.
	 * 
	 * @param node the AST node to start from
	 * @return the enclosing TypeDeclaration, or null if none found
	 */
	public static TypeDeclaration getParentTypeDeclaration(ASTNode node) {
		return ASTNodes.getTypedAncestor(node, TypeDeclaration.class);
	}

	/**
	 * Finds the enclosing TypeDeclaration for the given AST node.
	 * This is an alias for {@link #getParentTypeDeclaration(ASTNode)}.
	 * 
	 * @param node the AST node to start from
	 * @return the enclosing TypeDeclaration, or null if not found
	 */
	public static TypeDeclaration findEnclosingTypeDeclaration(ASTNode node) {
		return getParentTypeDeclaration(node);
	}

	/**
	 * Finds a type declaration by its fully qualified name within a Java project.
	 * 
	 * @param javaProject the Java project to search in
	 * @param fullyQualifiedTypeName the fully qualified type name
	 * @return the TypeDeclaration if found, or null otherwise
	 * @throws RuntimeException if there is an error accessing the Java model
	 */
	public static TypeDeclaration findTypeDeclaration(IJavaProject javaProject, String fullyQualifiedTypeName) {
		try {
			IType type = javaProject.findType(fullyQualifiedTypeName);
			if (type != null && type.exists()) {
				CompilationUnit unit = parseCompilationUnit(type.getCompilationUnit());
				return findTypeDeclarationInCompilationUnit(unit, fullyQualifiedTypeName);
			}
		} catch (JavaModelException e) {
			throw new RuntimeException("Failed to find type declaration for: " + fullyQualifiedTypeName, e); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * Finds a type declaration within a compilation unit by name.
	 * 
	 * @param unit the compilation unit to search
	 * @param fullyQualifiedTypeName the fully qualified type name
	 * @return the TypeDeclaration if found, or null otherwise
	 */
	public static TypeDeclaration findTypeDeclarationInCompilationUnit(CompilationUnit unit, String fullyQualifiedTypeName) {
		for (Object obj : unit.types()) {
			if (obj instanceof TypeDeclaration) {
				TypeDeclaration typeDecl = (TypeDeclaration) obj;
				TypeDeclaration result = findTypeDeclarationInType(typeDecl, fullyQualifiedTypeName);
				if (result != null) {
					return result;
				}
			}
		}
		return null;
	}

	/**
	 * Finds a type declaration by type binding within a compilation unit.
	 * 
	 * @param typeBinding the type binding to match
	 * @param cu the compilation unit to search
	 * @return the TypeDeclaration if found, or null otherwise
	 */
	public static TypeDeclaration findTypeDeclarationInCompilationUnit(ITypeBinding typeBinding, CompilationUnit cu) {
		final TypeDeclaration[] result = { null };

		cu.accept(new ASTVisitor() {
			private boolean checkAndMatchBinding(AbstractTypeDeclaration node, ITypeBinding typeBinding) {
				ITypeBinding binding = node.resolveBinding();
				if (binding != null && ASTNodes.areBindingsEqual(binding, typeBinding)) {
					result[0] = (TypeDeclaration) node;
					return false;
				}
				return true;
			}

			@Override
			public boolean visit(AnnotationTypeDeclaration node) {
				return checkAndMatchBinding(node, typeBinding);
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				return checkAndMatchBinding(node, typeBinding);
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				return checkAndMatchBinding(node, typeBinding);
			}
		});

		return result[0];
	}

	/**
	 * Finds a type declaration within the project for the given type binding.
	 * 
	 * @param typeBinding the type binding to find
	 * @return the TypeDeclaration if found, or null otherwise
	 */
	public static TypeDeclaration findTypeDeclarationInProject(ITypeBinding typeBinding) {
		IType type = (IType) typeBinding.getJavaElement();
		return type != null ? findTypeDeclaration(type.getJavaProject(), type.getFullyQualifiedName()) : null;
	}

	/**
	 * Recursively searches for a type declaration within a type hierarchy.
	 * 
	 * @param typeDecl the type declaration to start from
	 * @param qualifiedTypeName the qualified type name to find
	 * @return the TypeDeclaration if found, or null otherwise
	 */
	public static TypeDeclaration findTypeDeclarationInType(TypeDeclaration typeDecl, String qualifiedTypeName) {
		if (getQualifiedName(typeDecl).equals(qualifiedTypeName)) {
			return typeDecl;
		}

		for (TypeDeclaration nestedType : typeDecl.getTypes()) {
			TypeDeclaration result = findTypeDeclarationInType(nestedType, qualifiedTypeName);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Finds the type declaration for a given type binding, searching first in the
	 * compilation unit and then in the project.
	 * 
	 * @param typeBinding the type binding to find
	 * @param cu the compilation unit to search first
	 * @return the type declaration if found, or null otherwise
	 */
	public static ASTNode findTypeDeclarationForBinding(ITypeBinding typeBinding, CompilationUnit cu) {
		if (typeBinding == null)
			return null;

		TypeDeclaration typeDecl = findTypeDeclarationInCompilationUnit(typeBinding, cu);
		return typeDecl != null ? typeDecl : findTypeDeclarationInProject(typeBinding);
	}

	/**
	 * Determines the fully qualified name of a TypeDeclaration.
	 * 
	 * @param typeDecl the type declaration
	 * @return the fully qualified name including package and nested class separators
	 */
	public static String getQualifiedName(TypeDeclaration typeDecl) {
		StringBuilder qualifiedName = new StringBuilder(typeDecl.getName().getIdentifier());
		ASTNode parent = typeDecl.getParent();

		// Process nested classes
		while (parent instanceof TypeDeclaration) {
			TypeDeclaration parentType = (TypeDeclaration) parent;
			qualifiedName.insert(0, parentType.getName().getIdentifier() + "$"); // $ for nested classes //$NON-NLS-1$
			parent = parent.getParent();
		}

		// Add package name
		CompilationUnit compilationUnit = ASTNodes.getTypedAncestor(typeDecl, CompilationUnit.class);
		if (compilationUnit != null && compilationUnit.getPackage() != null) {
			String packageName = compilationUnit.getPackage().getName().getFullyQualifiedName();
			qualifiedName.insert(0, packageName + "."); //$NON-NLS-1$
		}

		return qualifiedName.toString();
	}

	/**
	 * Parses a compilation unit from an ICompilationUnit.
	 * 
	 * @param iCompilationUnit the compilation unit to parse
	 * @return the parsed CompilationUnit
	 */
	public static CompilationUnit parseCompilationUnit(org.eclipse.jdt.core.ICompilationUnit iCompilationUnit) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(iCompilationUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
	}
}
