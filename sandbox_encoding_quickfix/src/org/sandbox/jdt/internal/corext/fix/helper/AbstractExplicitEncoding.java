/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.UnionType;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;


/**
 * Abstract base class for explicit encoding refactoring operations.
 * <p>
 * This class provides the foundation for finding and rewriting code patterns
 * that use implicit or string-based encoding specifications to use explicit
 * {@code Charset} constants from {@code java.nio.charset.StandardCharsets}.
 * </p>
 * <p>
 * Subclasses must implement {@link #find} to locate relevant AST nodes and
 * {@link #rewrite} to perform the actual code transformation.
 * </p>
 *
 * @param <T> The type of AST node this encoding handler processes
 */
public abstract class AbstractExplicitEncoding<T extends ASTNode> {

	// Exception type constants for UnsupportedEncodingException handling
	private static final String JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION = "java.io.UnsupportedEncodingException"; //$NON-NLS-1$
	private static final String UNSUPPORTED_ENCODING_EXCEPTION = "UnsupportedEncodingException"; //$NON-NLS-1$

	/**
	 * Maps encoding names (e.g., "UTF-8") to their StandardCharsets field names (e.g., "UTF_8").
	 * <p>
	 * This mapping covers the six charsets guaranteed to be available on every Java platform.
	 * </p>
	 */
	static final Map<String, String> encodingmap = Map.of(
			"UTF-8", "UTF_8", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16", "UTF_16", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16BE", "UTF_16BE", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16LE", "UTF_16LE", //$NON-NLS-1$ //$NON-NLS-2$
			"ISO-8859-1", "ISO_8859_1", //$NON-NLS-1$ //$NON-NLS-2$
			"US-ASCII", "US_ASCII" //$NON-NLS-1$ //$NON-NLS-2$
	);

	/**
	 * Set of supported encoding names for quick lookup.
	 */
	static final Set<String> encodings = encodingmap.keySet();

	/**
	 * Data holder class for storing information about nodes found during AST traversal.
	 * <p>
	 * This class encapsulates all the information needed to perform a rewrite operation
	 * on a found encoding-related code pattern.
	 * </p>
	 */
	static class Nodedata {
		/** Whether the existing encoding argument should be replaced (true) or added (false). */
		public boolean replace;

		/** The AST node that was visited and should be transformed. */
		public ASTNode visited;

		/** The encoding constant name to use (e.g., "UTF_8"), or null for default charset. */
		public String encoding;

		/** Cache of charset constants created during aggregate mode transformation. */
		public static Map<String, QualifiedName> charsetConstants = new HashMap<>();

		/**
		 * Creates a Nodedata instance for replacing an existing encoding string.
		 *
		 * @param visited the AST node containing the encoding to replace
		 * @param encoding the StandardCharsets constant name to use
		 * @return a new Nodedata configured for replacement
		 */
		public static Nodedata createForReplacement(ASTNode visited, String encoding) {
			Nodedata nd = new Nodedata();
			nd.replace = true;
			nd.visited = visited;
			nd.encoding = encoding;
			return nd;
		}

		/**
		 * Creates a Nodedata instance for adding an encoding argument.
		 *
		 * @param visited the AST node to add encoding to
		 * @param encoding the StandardCharsets constant name to use, or null for default charset
		 * @return a new Nodedata configured for addition
		 */
		public static Nodedata createForAddition(ASTNode visited, String encoding) {
			Nodedata nd = new Nodedata();
			nd.replace = false;
			nd.visited = visited;
			nd.encoding = encoding;
			return nd;
		}
	}

	/** Property key for encoding data stored in reference holders. */
	protected static final String ENCODING = "encoding"; //$NON-NLS-1$

	/** Property key for replace flag stored in reference holders. */
	protected static final String REPLACE = "replace"; //$NON-NLS-1$

	/**
	 * Finds AST nodes that should be refactored to use explicit encoding.
	 * <p>
	 * Implementations should traverse the compilation unit looking for code patterns
	 * that use implicit encoding (platform default) or string-based encoding literals,
	 * and add corresponding rewrite operations to the operations set.
	 * </p>
	 *
	 * @param fixcore the fix core coordinating the refactoring
	 * @param compilationUnit the compilation unit to search
	 * @param operations the set to add discovered rewrite operations to
	 * @param nodesprocessed set of already processed nodes to avoid duplicates
	 * @param cb the behavior mode (keep behavior, enforce UTF-8, etc.)
	 */
	public abstract void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb);

	/**
	 * Rewrites the specified AST node to use explicit encoding.
	 * <p>
	 * Implementations should transform the visited node to use StandardCharsets
	 * constants or Charset.defaultCharset() instead of implicit or string-based encoding.
	 * </p>
	 *
	 * @param useExplicitEncodingFixCore the fix core coordinating the refactoring
	 * @param visited the AST node to rewrite
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for undo/redo support
	 * @param cb the behavior mode (keep behavior, enforce UTF-8, etc.)
	 * @param data reference holder containing node-specific transformation data
	 */
	public abstract void rewrite(UseExplicitEncodingFixCore useExplicitEncodingFixCore, T visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ChangeBehavior cb,
			ReferenceHolder<ASTNode, Object> data);

	/**
	 * Adds an import to the class and returns a name reference to the imported type.
	 * <p>
	 * This method should be used for every class reference added to the generated code.
	 * The import rewriter will handle conflicts and return the appropriate name form.
	 * </p>
	 *
	 * @param typeName a fully qualified name of a type (e.g., "java.nio.charset.StandardCharsets")
	 * @param cuRewrite the compilation unit rewrite context
	 * @param ast the AST to create the name node in
	 * @return simple name of a class if the import was added successfully, 
	 *         or fully qualified name if there was a naming conflict
	 * @throws IllegalArgumentException if typeName is null or empty
	 */
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		if (typeName == null || typeName.isEmpty()) {
			throw new IllegalArgumentException("typeName must not be null or empty"); //$NON-NLS-1$
		}
		String importedName = cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	/**
	 * Finds the string literal value assigned to a variable within its declaration scope.
	 * <p>
	 * This method searches for the variable's declaration in the enclosing method or type,
	 * and extracts the string literal value if the variable is initialized with one.
	 * The returned value is normalized to uppercase for encoding comparison.
	 * </p>
	 *
	 * @param variable the simple name of the variable to look up
	 * @param context the AST node providing the search context
	 * @return the uppercase string literal value if found, or null if the variable
	 *         declaration cannot be found or is not initialized with a string literal
	 */
	protected static String findVariableValue(SimpleName variable, ASTNode context) {
		if (variable == null || context == null) {
			return null;
		}

		ASTNode enclosingScope = findEnclosingScope(context);
		if (enclosingScope == null) {
			return null;
		}

		String variableName = variable.getIdentifier();
		if (enclosingScope instanceof MethodDeclaration) {
			return findVariableInMethod((MethodDeclaration) enclosingScope, variableName);
		}
		if (enclosingScope instanceof TypeDeclaration) {
			return findVariableInType((TypeDeclaration) enclosingScope, variableName);
		}
		return null;
	}

	/**
	 * Traverses up the AST to find the enclosing method or type declaration.
	 *
	 * @param node the starting node
	 * @return the enclosing MethodDeclaration or TypeDeclaration, or null if none found
	 */
	private static ASTNode findEnclosingScope(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration || current instanceof TypeDeclaration) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value within a method's local declarations.
	 *
	 * @param method the method to search in
	 * @param variableName the name of the variable to find
	 * @return the uppercase string value or null if not found
	 */
	private static String findVariableInMethod(MethodDeclaration method, String variableName) {
		Block body = method.getBody();
		if (body == null) {
			return null;
		}

		for (Object stmt : body.statements()) {
			if (stmt instanceof VariableDeclarationStatement) {
				String value = findValueInDeclaration((VariableDeclarationStatement) stmt, variableName);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value in a variable declaration statement.
	 *
	 * @param varDeclStmt the variable declaration statement
	 * @param variableName the name of the variable to find
	 * @return the uppercase string value or null if not found
	 */
	private static String findValueInDeclaration(VariableDeclarationStatement varDeclStmt, String variableName) {
		for (Object frag : varDeclStmt.fragments()) {
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) frag;
			if (fragment.getName().getIdentifier().equals(variableName)) {
				return extractStringLiteralValue(fragment.getInitializer());
			}
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value within a type's field declarations.
	 *
	 * @param type the type declaration to search in
	 * @param variableName the name of the variable to find
	 * @return the uppercase string value or null if not found
	 */
	private static String findVariableInType(TypeDeclaration type, String variableName) {
		for (FieldDeclaration field : type.getFields()) {
			for (Object frag : field.fragments()) {
				VariableDeclarationFragment fragment = (VariableDeclarationFragment) frag;
				if (fragment.getName().getIdentifier().equals(variableName)) {
					return extractStringLiteralValue(fragment.getInitializer());
				}
			}
		}
		return null;
	}

	/**
	 * Extracts and normalizes the value from a string literal expression.
	 *
	 * @param expression the expression to extract from
	 * @return the uppercase string value, or null if the expression is not a StringLiteral
	 */
	private static String extractStringLiteralValue(Expression expression) {
		if (expression instanceof StringLiteral) {
			return ((StringLiteral) expression).getLiteralValue().toUpperCase();
		}
		return null;
	}

	/**
	 * Returns a preview string showing the code transformation.
	 * <p>
	 * Implementations should return a representative code snippet showing
	 * either the before or after state of the refactoring.
	 * </p>
	 *
	 * @param afterRefactoring true to show the refactored code, false to show the original
	 * @param cb the behavior mode affecting the preview content
	 * @return a code snippet string for display in the UI
	 */
	public abstract String getPreview(boolean afterRefactoring, ChangeBehavior cb);

	/**
	 * Removes UnsupportedEncodingException from method signatures and catch clauses
	 * when it is no longer needed after converting to Charset-based encoding.
	 * <p>
	 * This method handles two scenarios:
	 * <ul>
	 *   <li>Method declarations: removes UnsupportedEncodingException from the throws clause</li>
	 *   <li>Try statements: removes UnsupportedEncodingException from catch clauses,
	 *       handling both simple and union (multi-catch) exception types</li>
	 * </ul>
	 * </p>
	 *
	 * @param visited the AST node that was refactored
	 * @param group the text edit group for undo/redo support
	 * @param rewrite the AST rewrite instance
	 * @param importRewriter the import rewrite for removing unused imports
	 */
	protected void removeUnsupportedEncodingException(final ASTNode visited, TextEditGroup group,
			ASTRewrite rewrite, ImportRewrite importRewriter) {
		ASTNode exceptionContainer = findExceptionContainer(visited);
		if (exceptionContainer == null) {
			return;
		}

		if (exceptionContainer instanceof MethodDeclaration) {
			removeExceptionFromMethodDeclaration((MethodDeclaration) exceptionContainer, group, rewrite, importRewriter);
		} else if (exceptionContainer instanceof TryStatement) {
			removeExceptionFromTryStatement((TryStatement) exceptionContainer, group, rewrite, importRewriter);
		}
	}

	/**
	 * Finds the enclosing method declaration or try statement that may contain
	 * UnsupportedEncodingException handling.
	 *
	 * @param node the starting node
	 * @return the enclosing MethodDeclaration or TryStatement, or null if none found
	 */
	private static ASTNode findExceptionContainer(ASTNode node) {
		ASTNode current = node.getParent();
		while (current != null) {
			if (current instanceof MethodDeclaration || current instanceof TryStatement) {
				return current;
			}
			current = current.getParent();
		}
		return null;
	}

	/**
	 * Removes UnsupportedEncodingException from a method's throws clause.
	 *
	 * @param method the method declaration
	 * @param group the text edit group
	 * @param rewrite the AST rewrite
	 * @param importRewriter the import rewrite
	 */
	private void removeExceptionFromMethodDeclaration(MethodDeclaration method, TextEditGroup group,
			ASTRewrite rewrite, ImportRewrite importRewriter) {
		ListRewrite throwsRewrite = rewrite.getListRewrite(method, MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
		List<Type> thrownExceptions = method.thrownExceptionTypes();

		for (Type exceptionType : thrownExceptions) {
			if (isUnsupportedEncodingException(exceptionType)) {
				throwsRewrite.remove(exceptionType, group);
				importRewriter.removeImport(JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION);
			}
		}
	}

	/**
	 * Removes UnsupportedEncodingException from a try statement's catch clauses.
	 *
	 * @param tryStatement the try statement
	 * @param group the text edit group
	 * @param rewrite the AST rewrite
	 * @param importRewriter the import rewrite
	 */
	private void removeExceptionFromTryStatement(TryStatement tryStatement, TextEditGroup group,
			ASTRewrite rewrite, ImportRewrite importRewriter) {
		List<CatchClause> catchClauses = tryStatement.catchClauses();

		for (CatchClause catchClause : catchClauses) {
			SingleVariableDeclaration exception = catchClause.getException();
			Type exceptionType = exception.getType();

			if (exceptionType instanceof UnionType) {
				handleUnionTypeException((UnionType) exceptionType, catchClause, group, rewrite);
			} else if (isUnsupportedEncodingException(exceptionType)) {
				rewrite.remove(catchClause, group);
				importRewriter.removeImport(JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION);
			}
		}

		cleanupEmptyTryStatement(tryStatement, group, rewrite);
	}

	/**
	 * Handles removal of UnsupportedEncodingException from a union (multi-catch) type.
	 *
	 * @param unionType the union type containing multiple exception types
	 * @param catchClause the catch clause containing the union type
	 * @param group the text edit group
	 * @param rewrite the AST rewrite
	 */
	private void handleUnionTypeException(UnionType unionType, CatchClause catchClause,
			TextEditGroup group, ASTRewrite rewrite) {
		ListRewrite unionRewrite = rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);
		List<Type> types = unionType.types();

		types.stream()
				.filter(this::isUnsupportedEncodingException)
				.forEach(type -> unionRewrite.remove(type, group));

		if (types.size() == 1) {
			rewrite.replace(unionType, types.get(0), group);
		} else if (types.isEmpty()) {
			rewrite.remove(catchClause, group);
		}
	}

	/**
	 * Checks if the given type represents UnsupportedEncodingException.
	 *
	 * @param type the type to check
	 * @return true if this is UnsupportedEncodingException
	 */
	private boolean isUnsupportedEncodingException(Type type) {
		return type.toString().equals(UNSUPPORTED_ENCODING_EXCEPTION);
	}

	/**
	 * Cleans up an empty try statement after exception handling removal.
	 * <p>
	 * If the try statement has no catch clauses and no finally block:
	 * <ul>
	 *   <li>If it has no resources and an empty body, remove the entire try statement</li>
	 *   <li>If it has no resources but has statements, replace try with just the body block</li>
	 * </ul>
	 * </p>
	 *
	 * @param tryStatement the try statement to potentially clean up
	 * @param group the text edit group
	 * @param rewrite the AST rewrite
	 */
	private void cleanupEmptyTryStatement(TryStatement tryStatement, TextEditGroup group, ASTRewrite rewrite) {
		if (!tryStatement.catchClauses().isEmpty() || tryStatement.getFinally() != null) {
			return;
		}

		Block tryBlock = tryStatement.getBody();
		boolean hasResources = !tryStatement.resources().isEmpty();
		boolean hasStatements = !tryBlock.statements().isEmpty();

		if (!hasResources && !hasStatements) {
			rewrite.remove(tryStatement, group);
		} else if (!hasResources) {
			rewrite.replace(tryStatement, tryBlock, group);
		}
	}
}
