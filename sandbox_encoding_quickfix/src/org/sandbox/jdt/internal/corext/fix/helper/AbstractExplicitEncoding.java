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

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.refactoring.structure.ImportRemover;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.UseExplicitEncodingFixCore;
import org.sandbox.jdt.internal.corext.util.ImportUtils;
import org.sandbox.jdt.triggerpattern.cleanup.ExceptionCleanupHelper;


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
	 * Immutable map of standard charset names (e.g., "UTF-8") to their corresponding
	 * StandardCharsets constant names (e.g., "UTF_8").
	 * <p>
	 * This mapping covers the six charsets guaranteed to be available on every Java platform.
	 * </p>
	 * @since 1.3
	 */
	public static final Map<String, String> ENCODING_MAP = Map.of(
			"UTF-8", "UTF_8", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16", "UTF_16", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16BE", "UTF_16BE", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16LE", "UTF_16LE", //$NON-NLS-1$ //$NON-NLS-2$
			"ISO-8859-1", "ISO_8859_1", //$NON-NLS-1$ //$NON-NLS-2$
			"US-ASCII", "US_ASCII" //$NON-NLS-1$ //$NON-NLS-2$
	);

	/**
	 * Immutable set of supported encoding names that can be converted to StandardCharsets constants.
	 * @since 1.3
	 */
	public static final Set<String> ENCODINGS = ENCODING_MAP.keySet();

	/**
	 * Maps standard charset names (e.g., "UTF-8") to their corresponding
	 * StandardCharsets constant names (e.g., "UTF_8").
	 * @deprecated Use {@link #ENCODING_MAP} instead. This field is maintained for backward
	 *             compatibility but is immutable and will throw UnsupportedOperationException
	 *             if modification is attempted.
	 */
	@Deprecated
	static final Map<String, String> encodingmap = ENCODING_MAP;

	/**
	 * Set of supported encoding names that can be converted to StandardCharsets constants.
	 * @deprecated Use {@link #ENCODINGS} instead. This field is maintained for backward
	 *             compatibility but is immutable and will throw UnsupportedOperationException
	 *             if modification is attempted.
	 */
	@Deprecated
	static final Set<String> encodings = ENCODINGS;

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
	 * @deprecated Use {@link #KEY_ENCODING} instead. This field will be removed in a future version.
	 */
	@Deprecated(forRemoval = true)
	protected static final String ENCODING = KEY_ENCODING;

	/**
	 * @deprecated Use {@link #KEY_REPLACE} instead. This field will be removed in a future version.
	 */
	@Deprecated(forRemoval = true)
	protected static final String REPLACE = KEY_REPLACE;

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
		return ImportUtils.addImport(typeName, cuRewrite.getImportRewrite(), ast);
	}

	/**
	 * Checks if a string literal contains a known encoding that can be converted
	 * to a StandardCharsets constant.
	 *
	 * @param literal the string literal to check, may be null
	 * @return true if the literal contains a known encoding, false otherwise
	 */
	protected static boolean isKnownEncoding(StringLiteral literal) {
		if (literal == null) {
			return false;
		}
		return ENCODINGS.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Gets the StandardCharsets constant name for a given encoding string literal.
	 *
	 * @param literal the string literal containing the encoding name, may be null
	 * @return the StandardCharsets constant name (e.g., "UTF_8"), or null if the literal
	 *         is null or contains an unknown encoding
	 */
	protected static String getEncodingConstantName(StringLiteral literal) {
		if (literal == null) {
			return null;
		}
		return ENCODING_MAP.get(literal.getLiteralValue().toUpperCase(Locale.ROOT));
	}

	/**
	 * Resolves the encoding value from various AST node types representing a charset argument.
	 * Handles string literals, variable references, qualified names (e.g., StandardCharsets.UTF_8),
	 * and field access expressions.
	 *
	 * @param encodingArg the AST node representing the charset argument
	 * @param context the method invocation context for variable resolution
	 * @return the uppercase encoding string (e.g., "UTF-8"), or null if not determinable
	 */
	protected static String getEncodingValue(ASTNode encodingArg, MethodInvocation context) {
		if (encodingArg instanceof StringLiteral literal) {
			return literal.getLiteralValue().toUpperCase(Locale.ROOT);
		} else if (encodingArg instanceof SimpleName simpleName) {
			return findVariableValue(simpleName, context);
		} else if (encodingArg instanceof QualifiedName qualifiedName) {
			return extractStandardCharsetName(qualifiedName);
		} else if (encodingArg instanceof FieldAccess fieldAccess) {
			return extractStandardCharsetName(fieldAccess);
		}
		return null;
	}

	/**
	 * Extracts charset name from QualifiedName like StandardCharsets.UTF_8.
	 *
	 * @param qualifiedName the qualified name to extract from
	 * @return the charset name (e.g., "UTF-8"), or null if not a StandardCharsets reference
	 */
	protected static String extractStandardCharsetName(QualifiedName qualifiedName) {
		String qualifier = qualifiedName.getQualifier().toString();
		if ("StandardCharsets".equals(qualifier) || qualifier.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
			String fieldName = qualifiedName.getName().getIdentifier();
			return fieldName.replace('_', '-');
		}
		return null;
	}

	/**
	 * Extracts charset name from FieldAccess like StandardCharsets.UTF_8.
	 *
	 * @param fieldAccess the field access to extract from
	 * @return the charset name (e.g., "UTF-8"), or null if not a StandardCharsets reference
	 */
	protected static String extractStandardCharsetName(FieldAccess fieldAccess) {
		String expression = fieldAccess.getExpression().toString();
		if ("StandardCharsets".equals(expression) || expression.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
			String fieldName = fieldAccess.getName().getIdentifier();
			return fieldName.replace('_', '-');
		}
		return null;
	}

	/**
	 * Finds the enclosing MethodDeclaration or TypeDeclaration for a given AST node.
	 *
	 * @param node the starting node for the search, may be null
	 * @return the enclosing MethodDeclaration or TypeDeclaration, or null if not found
	 */
	private static ASTNode findEnclosingMethodOrType(ASTNode node) {
		if (node == null) {
			return null;
		}
		ASTNode methodDecl = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
		ASTNode typeDecl = ASTNodes.getFirstAncestorOrNull(node, TypeDeclaration.class);
		
		// Return the closest ancestor. In Java, methods are always declared inside types,
		// so if a MethodDeclaration exists, it is guaranteed to be closer than any TypeDeclaration.
		// getFirstAncestorOrNull returns the nearest ancestor of each type, so we just need to
		// prefer the more specific (nested) one.
		if (methodDecl != null) {
			return methodDecl;
		}
		return typeDecl;
	}

	/**
	 * Extracts the string literal value from a variable declaration fragment if its initializer
	 * is a string literal.
	 *
	 * @param fragment the variable declaration fragment to check, must not be null
	 * @param variableIdentifier the identifier of the variable to match, must not be null
	 * @return the uppercase string literal value if found, null otherwise
	 */
	private static String extractStringLiteralValue(VariableDeclarationFragment fragment, String variableIdentifier) {
		if (!fragment.getName().getIdentifier().equals(variableIdentifier)) {
			return null;
		}
		Expression initializer = fragment.getInitializer();
		if (initializer instanceof StringLiteral) {
			return ((StringLiteral) initializer).getLiteralValue().toUpperCase(Locale.ROOT);
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value within a list of variable declaration fragments.
	 *
	 * @param fragments the list of fragments to search in, must not be null
	 * @param variableIdentifier the identifier of the variable to find, must not be null
	 * @return the uppercase string literal value if found, null otherwise
	 */
	private static String findValueInFragments(List<?> fragments, String variableIdentifier) {
		for (Object frag : fragments) {
			VariableDeclarationFragment fragment = (VariableDeclarationFragment) frag;
			String value = extractStringLiteralValue(fragment, variableIdentifier);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value within method body statements.
	 *
	 * @param method the method declaration to search in, must not be null
	 * @param variableIdentifier the identifier of the variable to find, must not be null
	 * @return the uppercase string literal value if found, null otherwise
	 */
	private static String findVariableValueInMethod(MethodDeclaration method, String variableIdentifier) {
		Block body = method.getBody();
		if (body == null) {
			return null;
		}
		List<?> statements = body.statements();
		for (Object stmt : statements) {
			if (stmt instanceof VariableDeclarationStatement) {
				VariableDeclarationStatement varDeclStmt = (VariableDeclarationStatement) stmt;
				String value = findValueInFragments(varDeclStmt.fragments(), variableIdentifier);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	/**
	 * Searches for a variable's string literal value within type field declarations.
	 *
	 * @param type the type declaration to search in, must not be null
	 * @param variableIdentifier the identifier of the variable to find, must not be null
	 * @return the uppercase string literal value if found, null otherwise
	 */
	private static String findVariableValueInType(TypeDeclaration type, String variableIdentifier) {
		FieldDeclaration[] fields = type.getFields();
		for (FieldDeclaration field : fields) {
			String value = findValueInFragments(field.fragments(), variableIdentifier);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	/**
	 * Finds the value of a variable by searching for its declaration in the enclosing
	 * method or type. This is used to resolve variable references to their string literal
	 * initializer values.
	 *
	 * @param variable the SimpleName representing the variable reference, may be null
	 * @param context the AST node providing context for the search, may be null
	 * @return the uppercase string literal value of the variable's initializer,
	 *         or null if the variable is null, context is null, or the value cannot be found
	 */
	protected static String findVariableValue(SimpleName variable, ASTNode context) {
		if (variable == null || context == null) {
			return null;
		}

		ASTNode enclosing = findEnclosingMethodOrType(context);
		if (enclosing == null) {
			return null;
		}

		String variableIdentifier = variable.getIdentifier();
		if (enclosing instanceof MethodDeclaration) {
			return findVariableValueInMethod((MethodDeclaration) enclosing, variableIdentifier);
		} else if (enclosing instanceof TypeDeclaration) {
			return findVariableValueInType((TypeDeclaration) enclosing, variableIdentifier);
		}
		return null;
	}

	/**
	 * Generates a preview string showing the code before or after the refactoring.
	 *
	 * @param afterRefactoring true to show the code after refactoring, false for before
	 * @param cb the change behavior configuration
	 * @return the preview string, never null
	 */
	public abstract String getPreview(boolean afterRefactoring, ChangeBehavior cb);

	/**
	 * Pattern matching the LAST NLS comment on a line (the one corresponding
	 * to the last/highest-numbered string literal). When a string literal is
	 * replaced by a non-string expression, we must remove the last NLS comment
	 * (not the first) because string literals are numbered left-to-right.
	 * Uses a negative lookahead to match only the final NLS comment.
	 * Adapted from {@code org.eclipse.jdt.internal.corext.dom.ASTNodes}.
	 */
	private static final Pattern LAST_NLS_COMMENT = Pattern.compile("[ ]*\\/\\/\\$NON-NLS-[0-9]+\\$(?!.*\\/\\/\\$NON-NLS-)"); //$NON-NLS-1$

	/**
	 * Replaces a string literal argument with a replacement node and removes the
	 * associated {@code //$NON-NLS-n$} comment.
	 *
	 * <p>When the statement is inside a try body that will be unwrapped by
	 * {@code removeUnsupportedEncodingException}, this method handles both the
	 * argument replacement AND the try-catch unwrapping in a single text-based
	 * operation to avoid conflicts between {@code rewrite.replace()} and
	 * {@code createMoveTarget()}.
	 *
	 * <p>When the statement is NOT inside such a try body, this method replaces
	 * the enclosing statement with a string placeholder that has the text
	 * substitution and NLS removal already applied.
	 *
	 * @param rewrite the AST rewrite
	 * @param visited the string literal node to replace
	 * @param replacement the replacement node (e.g., StandardCharsets.UTF_8)
	 * @param group the text edit group
	 * @param cuRewrite the compilation unit rewrite
	 * @return {@code true} if the enclosing try-catch was already unwrapped by
	 *         this method (caller should skip {@code removeUnsupportedEncodingException}),
	 *         {@code false} otherwise
	 */
	protected static boolean replaceArgumentAndRemoveNLS(ASTRewrite rewrite, ASTNode visited,
			ASTNode replacement, TextEditGroup group, CompilationUnitRewrite cuRewrite) {
		ASTNode st = ASTNodes.getFirstAncestorOrNull(visited, Statement.class, FieldDeclaration.class);
		if (st != null && isInsideTryBodyWithOnlyUnsupportedEncodingCatch(st)) {
			// Statement is in a try body that will be unwrapped.
			// Handle BOTH the argument replacement AND the try-catch unwrapping
			// in a single text-based operation to avoid conflicts between
			// rewrite.replace() and createMoveTarget().
			return replaceTryBodyAndUnwrap(rewrite, visited, replacement, st, group, cuRewrite);
		}
		if (st == null) {
			rewrite.replace(visited, replacement, group);
			return false;
		}
		// Safe to use the full statement replacement with NLS removal
		// (same approach as ASTNodes.replaceAndRemoveNLS)
		try {
			String buffer = cuRewrite.getCu().getBuffer().getContents();
			CompilationUnit cu = (CompilationUnit) st.getRoot();
			int origStart = cu.getExtendedStartPosition(st);
			int origLength = cu.getExtendedLength(st);
			String original = buffer.substring(origStart, origStart + origLength);
			// Remove last NLS comment (the one for the replaced encoding string literal)
			original = LAST_NLS_COMMENT.matcher(original).replaceFirst(""); //$NON-NLS-1$
			// Remove leading whitespace
			original = Pattern.compile("^[ \\t]*").matcher(original).replaceAll(""); //$NON-NLS-1$ //$NON-NLS-2$
			original = Pattern.compile("\n[ \\t]*").matcher(original).replaceAll("\n"); //$NON-NLS-1$ //$NON-NLS-2$
			// Replace visited text with replacement text
			String visitedString = buffer.substring(visited.getStartPosition(),
					visited.getStartPosition() + visited.getLength());
			String replacementString = replacement.toString().replaceAll(",", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			String modified = original.replace(visitedString, replacementString);
			ASTNode placeholder = rewrite.createStringPlaceholder(modified, st.getNodeType());
			rewrite.replace(st, placeholder, group);
		} catch (JavaModelException e) {
			// Fall back to simple replacement without NLS removal
			rewrite.replace(visited, replacement, group);
		}
		return false;
	}

	/**
	 * Handles the case where a string literal replacement is needed inside a try body
	 * that will be unwrapped (single catch clause for UnsupportedEncodingException only).
	 *
	 * <p>This method creates string placeholders for each statement in the try body
	 * with the argument replacement already baked in, then replaces the entire try
	 * statement with these inlined statements. This avoids the conflict between
	 * {@code rewrite.replace()} on child nodes and {@code createMoveTarget()} on
	 * parent statements.
	 *
	 * @return {@code true} if the try-catch was successfully unwrapped
	 */
	private static boolean replaceTryBodyAndUnwrap(ASTRewrite rewrite, ASTNode visited,
			ASTNode replacement, ASTNode statement, TextEditGroup group, CompilationUnitRewrite cuRewrite) {
		Block block = (Block) statement.getParent();
		org.eclipse.jdt.core.dom.TryStatement tryStatement = (org.eclipse.jdt.core.dom.TryStatement) block.getParent();
		ASTNode tryParent = tryStatement.getParent();
		if (!(tryParent instanceof Block parentBlock)) {
			// Cannot inline statements if the try's parent is not a block
			rewrite.replace(visited, replacement, group);
			return false;
		}
		try {
			String buffer = cuRewrite.getCu().getBuffer().getContents();
			CompilationUnit cu = (CompilationUnit) statement.getRoot();
			String visitedString = buffer.substring(visited.getStartPosition(),
					visited.getStartPosition() + visited.getLength());
			String replacementString = replacement.toString().replaceAll(",", ", "); //$NON-NLS-1$ //$NON-NLS-2$

			// Create string placeholders for each statement in the try body
			ListRewrite parentListRewrite = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			List<?> tryStatements = block.statements();
			for (int i = tryStatements.size() - 1; i >= 0; i--) {
				ASTNode stmt = (ASTNode) tryStatements.get(i);
				int stmtStart = cu.getExtendedStartPosition(stmt);
				int stmtLength = cu.getExtendedLength(stmt);
				String stmtSource = buffer.substring(stmtStart, stmtStart + stmtLength);
				// Remove leading whitespace
				stmtSource = Pattern.compile("^[ \\t]*").matcher(stmtSource).replaceAll(""); //$NON-NLS-1$ //$NON-NLS-2$
				stmtSource = Pattern.compile("\n[ \\t]*").matcher(stmtSource).replaceAll("\n"); //$NON-NLS-1$ //$NON-NLS-2$
				// Apply the argument replacement if this statement contains the visited node
				if (stmt == statement) {
					// Remove last NLS comment (the one for the replaced encoding string literal)
					stmtSource = LAST_NLS_COMMENT.matcher(stmtSource).replaceFirst(""); //$NON-NLS-1$
					stmtSource = stmtSource.replace(visitedString, replacementString);
				}
				ASTNode placeholder = rewrite.createStringPlaceholder(stmtSource, stmt.getNodeType());
				parentListRewrite.insertAfter(placeholder, tryStatement, group);
			}
			rewrite.remove(tryStatement, group);
			return true;
		} catch (JavaModelException e) {
			// Fall back to simple replacement without NLS removal
			rewrite.replace(visited, replacement, group);
			return false;
		}
	}

	/**
	 * Checks whether the given statement is directly inside the body of a try statement
	 * that will be fully unwrapped by removeUnsupportedEncodingException. This happens when:
	 * <ul>
	 *   <li>The statement is in the try body (not a catch/finally block)</li>
	 *   <li>The try has no resources (not try-with-resources)</li>
	 *   <li>The try has no finally block</li>
	 *   <li>The try has exactly one catch clause catching only UnsupportedEncodingException</li>
	 * </ul>
	 * In this case, simplifyEmptyTryStatement will use createMoveTarget to move
	 * statements out of the try body, so we must not use statement-level replacement.
	 */
	private static boolean isInsideTryBodyWithOnlyUnsupportedEncodingCatch(ASTNode statement) {
		ASTNode parent = statement.getParent();
		if (!(parent instanceof Block block)) {
			return false;
		}
		ASTNode grandParent = block.getParent();
		if (!(grandParent instanceof org.eclipse.jdt.core.dom.TryStatement tryStatement)) {
			return false;
		}
		// Check if the block is the try body (not a catch or finally block)
		if (tryStatement.getBody() != block) {
			return false;
		}
		// Try-with-resources won't be unwrapped even if catch is removed
		if (!tryStatement.resources().isEmpty()) {
			return false;
		}
		if (tryStatement.getFinally() != null) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<org.eclipse.jdt.core.dom.CatchClause> catchClauses = tryStatement.catchClauses();
		if (catchClauses.size() != 1) {
			return false;
		}
		org.eclipse.jdt.core.dom.CatchClause catchClause = catchClauses.get(0);
		org.eclipse.jdt.core.dom.Type exType = catchClause.getException().getType();
		if (exType instanceof org.eclipse.jdt.core.dom.SimpleType simpleType) {
			return UNSUPPORTED_ENCODING_EXCEPTION.equals(simpleType.getName().toString());
		}
		// Union type (e.g., FileNotFoundException | UnsupportedEncodingException):
		// After removing UnsupportedEncodingException, another catch type remains,
		// so the try won't be unwrapped → no conflict with createMoveTarget.
		return false;
	}

	/**
	 * Removes UnsupportedEncodingException from the enclosing method's throws clause
	 * or from catch clauses in a try statement. This is called after converting string-based
	 * encoding to StandardCharsets, since StandardCharsets methods don't throw
	 * UnsupportedEncodingException.
	 *
	 * <p>Delegates to {@link ExceptionCleanupHelper#removeCheckedException} for the
	 * actual work.
	 *
	 * @param visited the AST node that was modified, must not be null
	 * @param group the text edit group for tracking changes, must not be null
	 * @param rewrite the AST rewrite context, must not be null
	 * @param importRemover the import remover for tracking removed nodes, must not be null
	 */
	protected void removeUnsupportedEncodingException(final ASTNode visited, TextEditGroup group, ASTRewrite rewrite, ImportRemover importRemover) {
		ExceptionCleanupHelper.removeCheckedException(
				visited,
				JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION,
				UNSUPPORTED_ENCODING_EXCEPTION,
				group, rewrite, importRemover);
	}

}
