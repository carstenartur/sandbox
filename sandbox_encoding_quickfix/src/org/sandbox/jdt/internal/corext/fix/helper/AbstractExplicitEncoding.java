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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CatchClause;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
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
 * Shared support for cleanups that replace implicit or string-based encodings.
 *
 * @param <T> AST node handled by the concrete cleanup
 */
public abstract class AbstractExplicitEncoding<T extends ASTNode> {

	private static final String JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION = "java.io.UnsupportedEncodingException"; //$NON-NLS-1$
	private static final String UNSUPPORTED_ENCODING_EXCEPTION = "UnsupportedEncodingException"; //$NON-NLS-1$
	private static final String REMOVED_UNSUPPORTED_ENCODING_CATCHES_PROPERTY =
			AbstractExplicitEncoding.class.getName() + ".removedUnsupportedEncodingCatches"; //$NON-NLS-1$

	public static final Map<String, String> ENCODING_MAP = Map.of(
			"UTF-8", "UTF_8", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16", "UTF_16", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16BE", "UTF_16BE", //$NON-NLS-1$ //$NON-NLS-2$
			"UTF-16LE", "UTF_16LE", //$NON-NLS-1$ //$NON-NLS-2$
			"ISO-8859-1", "ISO_8859_1", //$NON-NLS-1$ //$NON-NLS-2$
			"US-ASCII", "US_ASCII"); //$NON-NLS-1$ //$NON-NLS-2$

	public static final Set<String> ENCODINGS = ENCODING_MAP.keySet();

	@Deprecated
	static final Map<String, String> encodingmap = ENCODING_MAP;

	@Deprecated
	static final Set<String> encodings = ENCODINGS;

	protected static record NodeData(boolean replace, ASTNode visited, String encoding) {
	}

	private static final Map<String, QualifiedName> CHARSET_CONSTANTS = new ConcurrentHashMap<>();

	protected static Map<String, QualifiedName> getCharsetConstants() {
		return CHARSET_CONSTANTS;
	}

	protected static final String KEY_ENCODING = "encoding"; //$NON-NLS-1$
	protected static final String KEY_REPLACE = "replace"; //$NON-NLS-1$

	@Deprecated(forRemoval = true)
	protected static final String ENCODING = KEY_ENCODING;

	@Deprecated(forRemoval = true)
	protected static final String REPLACE = KEY_REPLACE;

	public abstract void find(UseExplicitEncodingFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperation> operations, Set<ASTNode> nodesprocessed, ChangeBehavior cb);

	public abstract void rewrite(UseExplicitEncodingFixCore useExplicitEncodingFixCore, T visited,
			CompilationUnitRewrite cuRewrite, TextEditGroup group, ChangeBehavior cb,
			ReferenceHolder<ASTNode, Object> data);

	public abstract String getPreview(boolean afterRefactoring, ChangeBehavior cb);

	protected static Name addImport(String typeName, CompilationUnitRewrite cuRewrite, AST ast) {
		return ImportUtils.addImport(typeName, cuRewrite.getImportRewrite(), ast);
	}

	protected static boolean isKnownEncoding(StringLiteral literal) {
		return literal != null && ENCODINGS.contains(literal.getLiteralValue().toUpperCase(Locale.ROOT));
	}

	protected static String getEncodingConstantName(StringLiteral literal) {
		return literal == null ? null : ENCODING_MAP.get(literal.getLiteralValue().toUpperCase(Locale.ROOT));
	}

	protected static String getEncodingValue(ASTNode encodingArg, MethodInvocation context) {
		if (encodingArg instanceof StringLiteral literal) {
			return literal.getLiteralValue().toUpperCase(Locale.ROOT);
		}
		if (encodingArg instanceof SimpleName simpleName) {
			return findVariableValue(simpleName, context);
		}
		if (encodingArg instanceof QualifiedName qualifiedName) {
			return extractStandardCharsetName(qualifiedName);
		}
		if (encodingArg instanceof FieldAccess fieldAccess) {
			return extractStandardCharsetName(fieldAccess);
		}
		return null;
	}

	protected static String extractStandardCharsetName(QualifiedName qualifiedName) {
		String qualifier = qualifiedName.getQualifier().toString();
		if ("StandardCharsets".equals(qualifier) || qualifier.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
			return qualifiedName.getName().getIdentifier().replace('_', '-');
		}
		return null;
	}

	protected static String extractStandardCharsetName(FieldAccess fieldAccess) {
		String expression = fieldAccess.getExpression().toString();
		if ("StandardCharsets".equals(expression) || expression.endsWith(".StandardCharsets")) { //$NON-NLS-1$ //$NON-NLS-2$
			return fieldAccess.getName().getIdentifier().replace('_', '-');
		}
		return null;
	}

	private static ASTNode findEnclosingMethodOrType(ASTNode node) {
		if (node == null) {
			return null;
		}
		MethodDeclaration method = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
		return method != null ? method : ASTNodes.getFirstAncestorOrNull(node, TypeDeclaration.class);
	}

	private static String extractStringLiteralValue(VariableDeclarationFragment fragment,
			String variableIdentifier) {
		if (!fragment.getName().getIdentifier().equals(variableIdentifier)) {
			return null;
		}
		Expression initializer = fragment.getInitializer();
		return initializer instanceof StringLiteral literal
				? literal.getLiteralValue().toUpperCase(Locale.ROOT)
				: null;
	}

	private static String findValueInFragments(List<?> fragments, String variableIdentifier) {
		for (Object fragmentObject : fragments) {
			String value = extractStringLiteralValue((VariableDeclarationFragment) fragmentObject,
					variableIdentifier);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String findVariableValueInMethod(MethodDeclaration method, String variableIdentifier) {
		Block body = method.getBody();
		if (body == null) {
			return null;
		}
		for (Object statement : body.statements()) {
			if (statement instanceof VariableDeclarationStatement declaration) {
				String value = findValueInFragments(declaration.fragments(), variableIdentifier);
				if (value != null) {
					return value;
				}
			}
		}
		return null;
	}

	private static String findVariableValueInType(TypeDeclaration type, String variableIdentifier) {
		for (FieldDeclaration field : type.getFields()) {
			String value = findValueInFragments(field.fragments(), variableIdentifier);
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	protected static String findVariableValue(SimpleName variable, ASTNode context) {
		if (variable == null || context == null) {
			return null;
		}
		ASTNode enclosing = findEnclosingMethodOrType(context);
		if (enclosing instanceof MethodDeclaration method) {
			return findVariableValueInMethod(method, variable.getIdentifier());
		}
		if (enclosing instanceof TypeDeclaration type) {
			return findVariableValueInType(type, variable.getIdentifier());
		}
		return null;
	}

	private static final Pattern LAST_NLS_COMMENT = Pattern.compile(
			"[ ]*\\/\\/\\$NON-NLS-[0-9]+\\$(?!.*\\/\\/\\$NON-NLS-)"); //$NON-NLS-1$

	protected static boolean replaceArgumentAndRemoveNLS(ASTRewrite rewrite, ASTNode visited,
			ASTNode replacement, TextEditGroup group, CompilationUnitRewrite cuRewrite) {
		ASTNode statement = ASTNodes.getFirstAncestorOrNull(visited, Statement.class, FieldDeclaration.class);
		if (statement != null && isInsideTryBodyWithOnlyUnsupportedEncodingCatch(statement)) {
			return replaceTryBodyAndUnwrap(rewrite, visited, replacement, statement, group, cuRewrite);
		}
		if (statement == null) {
			rewrite.replace(visited, replacement, group);
			return false;
		}
		try {
			String buffer = cuRewrite.getCu().getBuffer().getContents();
			CompilationUnit root = (CompilationUnit) statement.getRoot();
			int start = root.getExtendedStartPosition(statement);
			String source = buffer.substring(start, start + root.getExtendedLength(statement));
			source = normalizeReplacementSource(source);
			String visitedSource = buffer.substring(visited.getStartPosition(),
					visited.getStartPosition() + visited.getLength());
			String replacementSource = replacement.toString().replaceAll(",", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			ASTNode placeholder = rewrite.createStringPlaceholder(
					source.replace(visitedSource, replacementSource), statement.getNodeType());
			rewrite.replace(statement, placeholder, group);
		} catch (JavaModelException exception) {
			rewrite.replace(visited, replacement, group);
		}
		return false;
	}

	private static boolean replaceTryBodyAndUnwrap(ASTRewrite rewrite, ASTNode visited,
			ASTNode replacement, ASTNode statement, TextEditGroup group, CompilationUnitRewrite cuRewrite) {
		Block tryBody = (Block) statement.getParent();
		TryStatement tryStatement = (TryStatement) tryBody.getParent();
		if (!(tryStatement.getParent() instanceof Block parentBlock)) {
			rewrite.replace(visited, replacement, group);
			return false;
		}
		try {
			String buffer = cuRewrite.getCu().getBuffer().getContents();
			CompilationUnit root = (CompilationUnit) statement.getRoot();
			String visitedSource = buffer.substring(visited.getStartPosition(),
					visited.getStartPosition() + visited.getLength());
			String replacementSource = replacement.toString().replaceAll(",", ", "); //$NON-NLS-1$ //$NON-NLS-2$
			ListRewrite parentStatements = rewrite.getListRewrite(parentBlock, Block.STATEMENTS_PROPERTY);
			List<?> bodyStatements = tryBody.statements();

			for (int index = bodyStatements.size() - 1; index >= 0; index--) {
				ASTNode bodyStatement = (ASTNode) bodyStatements.get(index);
				ASTNode inlinedStatement;
				if (bodyStatement == statement) {
					int start = root.getExtendedStartPosition(bodyStatement);
					String source = buffer.substring(start, start + root.getExtendedLength(bodyStatement));
					source = normalizeReplacementSource(source).replace(visitedSource, replacementSource);
					inlinedStatement = rewrite.createStringPlaceholder(source, bodyStatement.getNodeType());
				} else {
					inlinedStatement = rewrite.createMoveTarget(bodyStatement);
				}
				parentStatements.insertAfter(inlinedStatement, tryStatement, group);
			}

			CatchClause removedCatch = (CatchClause) tryStatement.catchClauses().get(0);
			Set<CatchClause> removedCatches = removedUnsupportedEncodingCatches(root);
			removedCatches.add(removedCatch);
			cuRewrite.getImportRemover().registerRemovedNode(removedCatch);
			if (!hasSurvivingUnsupportedEncodingExceptionReference(root, removedCatches)) {
				cuRewrite.getImportRewrite().removeImport(JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION);
			}
			rewrite.remove(tryStatement, group);
			return true;
		} catch (JavaModelException exception) {
			rewrite.replace(visited, replacement, group);
			return false;
		}
	}

	private static String normalizeReplacementSource(String source) {
		String normalized = LAST_NLS_COMMENT.matcher(source).replaceFirst(""); //$NON-NLS-1$
		normalized = Pattern.compile("^[ \\t]*").matcher(normalized).replaceAll(""); //$NON-NLS-1$ //$NON-NLS-2$
		return Pattern.compile("\n[ \\t]*").matcher(normalized).replaceAll("\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@SuppressWarnings("unchecked")
	private static Set<CatchClause> removedUnsupportedEncodingCatches(CompilationUnit root) {
		Object stored = root.getProperty(REMOVED_UNSUPPORTED_ENCODING_CATCHES_PROPERTY);
		if (stored instanceof Set<?>) {
			return (Set<CatchClause>) stored;
		}
		Set<CatchClause> removedCatches = Collections.newSetFromMap(new IdentityHashMap<>());
		root.setProperty(REMOVED_UNSUPPORTED_ENCODING_CATCHES_PROPERTY, removedCatches);
		return removedCatches;
	}

	private static boolean hasSurvivingUnsupportedEncodingExceptionReference(CompilationUnit root,
			Set<CatchClause> removedCatches) {
		boolean[] found = { false };
		root.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				if (found[0]) {
					return false;
				}
				if (!UNSUPPORTED_ENCODING_EXCEPTION.equals(node.getIdentifier())
						|| hasAncestor(node, ImportDeclaration.class)
						|| isDescendantOfAny(node, removedCatches)) {
					return true;
				}
				found[0] = true;
				return false;
			}
		});
		return found[0];
	}

	private static boolean hasAncestor(ASTNode node, Class<? extends ASTNode> ancestorType) {
		for (ASTNode current = node.getParent(); current != null; current = current.getParent()) {
			if (ancestorType.isInstance(current)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isDescendantOfAny(ASTNode node, Set<CatchClause> ancestors) {
		for (ASTNode current = node; current != null; current = current.getParent()) {
			if (ancestors.contains(current)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isInsideTryBodyWithOnlyUnsupportedEncodingCatch(ASTNode statement) {
		if (!(statement.getParent() instanceof Block block)
				|| !(block.getParent() instanceof TryStatement tryStatement)
				|| tryStatement.getBody() != block
				|| !tryStatement.resources().isEmpty()
				|| tryStatement.getFinally() != null) {
			return false;
		}
		@SuppressWarnings("unchecked")
		List<CatchClause> catchClauses = tryStatement.catchClauses();
		if (catchClauses.size() != 1) {
			return false;
		}
		Type exceptionType = catchClauses.get(0).getException().getType();
		ITypeBinding binding = exceptionType.resolveBinding();
		if (binding != null) {
			return JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION.equals(binding.getErasure().getQualifiedName());
		}
		String source = exceptionType.toString();
		return UNSUPPORTED_ENCODING_EXCEPTION.equals(source)
				|| JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION.equals(source)
				|| source.endsWith("." + UNSUPPORTED_ENCODING_EXCEPTION); //$NON-NLS-1$
	}

	protected void removeUnsupportedEncodingException(ASTNode visited, TextEditGroup group,
			ASTRewrite rewrite, ImportRemover importRemover) {
		ExceptionCleanupHelper.removeCheckedException(visited,
				JAVA_IO_UNSUPPORTED_ENCODING_EXCEPTION,
				UNSUPPORTED_ENCODING_EXCEPTION,
				group, rewrite, importRemover);
	}
}
