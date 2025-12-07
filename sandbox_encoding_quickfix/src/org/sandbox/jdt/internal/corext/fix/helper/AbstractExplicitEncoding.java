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

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
 * Abstract base class for encoding-related quick fixes. Provides common functionality
 * for finding and rewriting code patterns that use implicit or string-based encoding
 * specifications.
 *
 * <p>Subclasses must implement:
 * <ul>
 *   <li>{@link #find} - to locate code patterns that need to be fixed</li>
 *   <li>{@link #rewrite} - to apply the encoding-related transformation</li>
 *   <li>{@link #getPreview} - to generate preview text for the fix</li>
 * </ul>
 *
 * @param <T> The type of AST node that this encoding handler processes
 */
public abstract class AbstractExplicitEncoding<T extends ASTNode> {

	/** Fully qualified name of java.io.UnsupportedEncodingException. */
	private static final String JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION = "java.io.UnsupportedEncodingException"; //$NON-NLS-1$

	/** Simple name of UnsupportedEncodingException for matching in exception types. */
	private static final String UNSUPPORTED_ENCODING_EXCEPTION = "UnsupportedEncodingException"; //$NON-NLS-1$

	/**
	 * Internal map of standard charset names (e.g., "UTF-8") to their corresponding
	 * StandardCharsets constant names (e.g., "UTF_8").
	 */
	private static final Map<String, String> encodingMapInternal = Map.of(
			"UTF-8", "UTF_8", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16", "UTF_16", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16BE", "UTF_16BE", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16LE", "UTF_16LE", //$NON-NLS-1$ //$NON-NLS-2$
			"ISO-8859-1", "ISO_8859_1", //$NON-NLS-1$ //$NON-NLS-2$
			"US-ASCII", "US_ASCII" //$NON-NLS-1$ //$NON-NLS-2$
	);

	/**
	 * Immutable map of encoding names to their StandardCharsets constant names.
	 */
	protected static final Map<String, String> ENCODING_MAP = Collections.unmodifiableMap(encodingMapInternal);

	/**
	 * Immutable set of supported encoding names that can be converted to StandardCharsets constants.
	 */
	protected static final Set<String> ENCODINGS = Collections.unmodifiableSet(encodingMapInternal.keySet());

	/**
	 * Immutable record to hold node data for encoding transformations.
	 * Replaces the mutable Nodedata class for better thread safety and immutability.
	 * 
	 * @param replace Whether to replace an existing encoding parameter (true) or appended (false)
	 * @param visited The AST node that was visited and needs modification
	 * @param encoding The encoding constant name (e.g., "UTF_8"), or null for default charset
	 */
	protected static record NodeData(boolean replace, ASTNode visited, String encoding) {
	}

	/**
	 * Thread-safe map to cache charset constant references during aggregation.
	 * Used to avoid creating duplicate QualifiedName instances.
	 */
	private static final Map<String, QualifiedName> CHARSET_CONSTANTS = new ConcurrentHashMap<>();

	/**
	 * Returns the charset constants map for use in encoding transformations.
	 * 
	 * @return thread-safe map of charset constants
	 */
	protected static Map<String, QualifiedName> getCharsetConstants() {
		return CHARSET_CONSTANTS;
	}

	/** Key used for storing encoding information in data holders. */
	protected static final String KEY_ENCODING = "encoding"; //$NON-NLS-1$

	/** Key used for storing replace flag in data holders. */
	protected static final String KEY_REPLACE = "replace"; //$NON-NLS-1$

	/**
	 * @deprecated Use {@link #ENCODING_MAP} instead. This field will be removed in a future version.
	 */
	@Deprecated(forRemoval = true)
	static Map<String, String> encodingmap = ENCODING_MAP;

	/**
	 * @deprecated Use {@link #ENCODINGS} instead. This field will be removed in a future version.
	 */
	@Deprecated(forRemoval = true)
	static Set<String> encodings = ENCODINGS;

	/**
	 * Finds all occurrences of the encoding pattern that this handler processes
	 * and adds corresponding rewrite operations.
	 *
	 * @param fixcore the fix core instance, must not be null
	 * @param compilationUnit the compilation unit to search in, must not be null
	 * @param operations the set to add rewrite operations to, must not be null
	 * @param nodesprocessed the set of already processed nodes (to avoid duplicates), must not be null
	 * @param cb the change behavior configuration, must not be null
	 */
	public abstract void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit, Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb);

	/**
	 * Rewrites the visited AST node to use explicit encoding.
	 *
	 * @param useExplicitEncodingFixCore the fix core instance, must not be null
	 * @param visited the AST node to rewrite, must not be null
	 * @param cuRewrite the compilation unit rewrite context, must not be null
	 * @param group the text edit group for grouping changes, must not be null
	 * @param cb the change behavior configuration, must not be null
	 * @param data the reference holder containing node-specific data, must not be null
	 */
	public abstract void rewrite(UseExplicitEncodingFixCore useExplicitEncodingFixCore, T visited, CompilationUnitRewrite cuRewrite,
			TextEditGroup group, ChangeBehavior cb, ReferenceHolder<ASTNode, Object> data);

	/**
	 * Adds an import to the class. This method should be used for every class reference added to
	 * the generated code.
	 *
	 * @param typeName a fully qualified name of a type, must not be null
	 * @param cuRewrite CompilationUnitRewrite, must not be null
	 * @param ast AST, must not be null
	 * @return simple name of a class if the import was added and fully qualified name if there was
	 *         a conflict; never null
	 */
	protected static Name addImport(String typeName, final CompilationUnitRewrite cuRewrite, AST ast) {
		String importedName = cuRewrite.getImportRewrite().addImport(typeName);
		return ast.newName(importedName);
	}

	/**
	 * Finds the value of a variable by searching through the enclosing method or type declaration.
	 * Searches for variable declarations and returns the string literal value if found.
	 *
	 * @param variable the variable name to search for, must not be null
	 * @param context the AST node context to start the search from, must not be null
	 * @return the uppercase string literal value of the variable, or null if not found
	 */
	protected static String findVariableValue(SimpleName variable, ASTNode context) {
		ASTNode current= context.getParent();
		while (current != null && !(current instanceof MethodDeclaration) && !(current instanceof TypeDeclaration)) {
			current= current.getParent();
		}

		if (current instanceof MethodDeclaration) {
			MethodDeclaration method= (MethodDeclaration) current;
			Block body= method.getBody();
			if (body == null) {
				return null;
			}
			List<?> statements= body.statements();

			for (Object stmt : statements) {
				if (stmt instanceof VariableDeclarationStatement) {
					VariableDeclarationStatement varDeclStmt= (VariableDeclarationStatement) stmt;
					for (Object frag : varDeclStmt.fragments()) {
						VariableDeclarationFragment fragment= (VariableDeclarationFragment) frag;
						if (fragment.getName().getIdentifier().equals(variable.getIdentifier())) {
							Expression initializer= fragment.getInitializer();
							if (initializer instanceof StringLiteral) {
								return ((StringLiteral) initializer).getLiteralValue().toUpperCase(Locale.ROOT);
							}
						}
					}
				}
			}
		} else if (current instanceof TypeDeclaration) {
			TypeDeclaration type= (TypeDeclaration) current;
			FieldDeclaration[] fields= type.getFields();

			for (FieldDeclaration field : fields) {
				for (Object frag : field.fragments()) {
					VariableDeclarationFragment fragment= (VariableDeclarationFragment) frag;
					if (fragment.getName().getIdentifier().equals(variable.getIdentifier())) {
						Expression initializer= fragment.getInitializer();
						if (initializer instanceof StringLiteral) {
							return ((StringLiteral) initializer).getLiteralValue().toUpperCase(Locale.ROOT);
						}
					}
				}
			}
		}
		return null;
	}

	/**
	 * Generates preview text showing the code before and after the encoding fix is applied.
	 *
	 * @param afterRefactoring true to show the code after applying the fix, false to show before
	 * @param cb the change behavior configuration, must not be null
	 * @return preview text showing the encoding pattern, never null
	 */
	public abstract String getPreview(boolean afterRefactoring, ChangeBehavior cb);

	/**
	 * Removes UnsupportedEncodingException from method throws clauses or catch blocks
	 * when the encoding is changed from a string to a Charset constant.
	 *
	 * @param visited the AST node being processed, must not be null
	 * @param group the text edit group for grouping changes, must not be null
	 * @param rewrite the AST rewrite instance, must not be null
	 * @param importRewriter the import rewrite instance, must not be null
	 */
	protected void removeUnsupportedEncodingException(final ASTNode visited, TextEditGroup group, ASTRewrite rewrite, ImportRewrite importRewriter) {
		ASTNode parent= visited.getParent();
		while (parent != null && !(parent instanceof MethodDeclaration) && !(parent instanceof TryStatement)) {
			parent= parent.getParent();
		}

		if (parent instanceof MethodDeclaration) {
			MethodDeclaration method= (MethodDeclaration) parent;
			ListRewrite throwsRewrite= rewrite.getListRewrite(method, MethodDeclaration.THROWN_EXCEPTION_TYPES_PROPERTY);
			List<Type> thrownExceptions= method.thrownExceptionTypes();
			for (Type exceptionType : thrownExceptions) {
				if (isUnsupportedEncodingException(exceptionType)) {
					throwsRewrite.remove(exceptionType, group);
					importRewriter.removeImport(JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION);
				}
			}
		} else if (parent instanceof TryStatement) {
			TryStatement tryStatement= (TryStatement) parent;

			List<CatchClause> catchClauses= tryStatement.catchClauses();
			for (CatchClause catchClause : catchClauses) {
				SingleVariableDeclaration exception= catchClause.getException();
				Type exceptionType= exception.getType();

				if (exceptionType instanceof UnionType) {
					UnionType unionType= (UnionType) exceptionType;
					ListRewrite unionRewrite= rewrite.getListRewrite(unionType, UnionType.TYPES_PROPERTY);

					List<Type> types= unionType.types();
					// Two-phase approach: collect targets then remove to avoid concurrent modification
					List<Type> typesToRemove= types.stream()
							.filter(type -> isUnsupportedEncodingException(type))
							.toList();
					
					for (Type type : typesToRemove) {
						unionRewrite.remove(type, group);
					}

					if (types.size() == 1) {
						rewrite.replace(unionType, types.get(0), group);
					} else if (types.isEmpty()) {
						rewrite.remove(catchClause, group);
					}
				} else if (isUnsupportedEncodingException(exceptionType)) {
					rewrite.remove(catchClause, group);
					importRewriter.removeImport(JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION);
				}
			}

			// Cache the result of catchClauses() to avoid repeated calls
			List<CatchClause> catchClausesAfter= tryStatement.catchClauses();
			if (catchClausesAfter.isEmpty() && tryStatement.getFinally() == null) {
				Block tryBlock= tryStatement.getBody();

				if (tryStatement.resources().isEmpty() && tryBlock.statements().isEmpty()) {
					rewrite.remove(tryStatement, group);
				} else if (tryStatement.resources().isEmpty()) {
					rewrite.replace(tryStatement, tryBlock, group);
				}
			}
		}
	}

	/**
	 * Checks if a type represents UnsupportedEncodingException.
	 * Uses type binding when available for accurate type resolution, falls back to string matching.
	 * 
	 * @param type The type to check, must not be null
	 * @return true if the type is UnsupportedEncodingException, false otherwise
	 */
	private boolean isUnsupportedEncodingException(Type type) {
		// Try to use binding for more robust type checking
		if (type.resolveBinding() != null) {
			String qualifiedName= type.resolveBinding().getQualifiedName();
			return JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION.equals(qualifiedName);
		}
		// Fallback to string matching
		String typeString= type.toString();
		return UNSUPPORTED_ENCODING_EXCEPTION.equals(typeString);
	}

}
