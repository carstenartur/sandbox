/*
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.LineComment;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSLine;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite;

/** Keeps a tagged message's evaluation and NLS comments together before the assertion. */
final class JUnit4AssertionNls {

	private static final String MOVED= JUnit4AssertionNls.class.getName() + ".moved"; //$NON-NLS-1$
	private static final String NAMES= JUnit4AssertionNls.class.getName() + ".names"; //$NON-NLS-1$
	private record Capture(Expression message, Statement declaration, TextEditGroup group) { }
	private record Names(ASTRewrite rewrite, Set<String> names) { }

	private JUnit4AssertionNls() {
	}

	static Expression capture(MethodInvocation invocation, ASTRewrite rewrite, TextEditGroup group) {
		CompilationUnit root= (CompilationUnit) invocation.getRoot();
		Expression message= (Expression) invocation.arguments().getFirst();
		try {
			String source= ((ICompilationUnit) root.getJavaElement()).getBuffer().getContents();
			NLSLine[] lines= NLSScanner.scan(source);
			if (!needsCapture(invocation, lines)) return null;
			String name= uniqueName(invocation, rewrite);
			StringBuilder text= new StringBuilder(source.substring(message.getStartPosition(),
					message.getStartPosition() + message.getLength()));
			var changes= new ArrayList<Runnable>();
			StringBuilder lastTags= new StringBuilder();
			int lastLine= root.getLineNumber(message.getStartPosition() + message.getLength() - 1) - 1;
			for (NLSLine line : lines) {
				int index= 0;
				for (NLSElement literal : line.getElements()) {
					boolean included= contains(message, literal.getPosition().getOffset());
					if (included) index++;
					if (!literal.hasTag()) continue;
					int start= literal.getTagPosition().getOffset();
					if (contains(message, start)) {
						String tag= included ? NLSElement.createTagText(index) : "//"; //$NON-NLS-1$
						int relative= start - message.getStartPosition();
						changes.add(() -> text.replace(relative, relative + literal.getTagPosition().getLength(), tag));
					} else if (included && line.getLineNumber() == lastLine) {
						lastTags.append(' ').append(NLSElement.createTagText(index));
					}
				}
			}
			for (Runnable change : changes.reversed()) change.run();
			String note= trailingNote(root, invocation, message, source, lines);
			Statement declaration= (Statement) rewrite.createStringPlaceholder(
					"java.lang.String " + name + " = " + text + ";" + lastTags + note, ASTNode.VARIABLE_DECLARATION_STATEMENT); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			invocation.setProperty(MOVED, new Capture(message, declaration, group));
			return root.getAST().newSimpleName(name);
		} catch (JavaModelException | InvalidInputException | BadLocationException exception) {
			throw new IllegalArgumentException("Cannot preserve assertion NLS tags", exception); //$NON-NLS-1$
		}
	}

	/** Retains calls without a safe evaluation boundary or an unambiguous, independent NLS line. */
	static Set<MethodInvocation> unsupportedCaptures(CompilationUnit root, String owner, String target, Set<String> methods) {
		if (!(root.getJavaElement() instanceof ICompilationUnit unit)) return Set.of();
		try {
			String source= unit.getBuffer().getContents();
			if (!source.contains(NLSElement.TAG_PREFIX)) return Set.of();
			NLSLine[] lines= NLSScanner.scan(source);
			var literals= new ArrayList<StringLiteral>();
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(StringLiteral literal) {
					literals.add(literal);
					return false;
				}
			});
			Set<StringLiteral> safe= new HashSet<>(EncodingSourceRewrite.literalsWithUnambiguousTags(root, unit, literals));
			Set<MethodInvocation> unsupported= new HashSet<>();
			var candidates= new ArrayList<MethodInvocation>();
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(MethodInvocation invocation) {
					var binding= invocation.resolveMethodBinding();
					if (binding == null || binding.isRecovered() || !owner.equals(binding.getDeclaringClass().getQualifiedName())
							|| !methods.contains(invocation.getName().getIdentifier())) return true;
					candidates.add(invocation);
					Expression receiver= invocation.getExpression();
					if (receiver != null && !(receiver instanceof Name name && name.resolveBinding() instanceof ITypeBinding)) {
						// A type qualifier is unevaluated; an expression qualifier must retain its evaluation.
						unsupported.add(invocation);
						return true;
					}
					if (invocation.arguments().size() >= 2
							&& "java.lang.String".equals(binding.getParameterTypes()[0].getQualifiedName()) //$NON-NLS-1$
							&& needsCapture(invocation, lines)) {
						boolean boundary= invocation.getParent() instanceof ExpressionStatement
								|| invocation.getParent() instanceof LambdaExpression lambda && lambda.getBody() == invocation;
						boolean ambiguous= literals.stream().anyMatch(literal -> contains(invocation, literal.getStartPosition()) && !safe.contains(literal));
						int first= root.getLineNumber(invocation.getStartPosition()) - 1;
						int last= root.getLineNumber(invocation.getStartPosition() + invocation.getLength() - 1) - 1;
						for (NLSLine line : lines) {
							if (line.getLineNumber() < first || line.getLineNumber() > last) continue;
							for (NLSElement literal : line.getElements()) {
								ambiguous|= literal.hasTag() && !contains(invocation, literal.getPosition().getOffset());
							}
						}
						if (!boundary || ambiguous) unsupported.add(invocation);
					}
					return true;
				}
			});
			if (!unsupported.isEmpty()) {
				String firstSegment= target.substring(0, target.indexOf('.'));
				ScopeAnalyzer scope= new ScopeAnalyzer(root);
				for (MethodInvocation invocation : candidates) {
					for (var binding : scope.getDeclarationsInScope(invocation.getStartPosition(), ScopeAnalyzer.VARIABLES)) {
						if (firstSegment.equals(binding.getName())) unsupported.add(invocation);
					}
				}
			}
			return unsupported;
		} catch (JavaModelException | InvalidInputException | BadLocationException exception) {
			throw new IllegalArgumentException("Cannot inspect assertion NLS tags", exception); //$NON-NLS-1$
		}
	}

	private static boolean needsCapture(MethodInvocation invocation, NLSLine[] lines) {
		for (NLSLine line : lines) {
			for (NLSElement literal : line.getElements()) {
				int offset= literal.getPosition().getOffset();
				if (contains(invocation, offset) && literal.hasTag()) return true;
			}
		}
		return false;
	}

	private static String trailingNote(CompilationUnit root, MethodInvocation invocation, Expression message,
			String source, NLSLine[] lines) {
		int end= message.getStartPosition() + message.getLength();
		int next= ((ASTNode) invocation.arguments().get(1)).getStartPosition();
		for (Object node : root.getCommentList()) {
			if (!(node instanceof LineComment comment) || comment.getStartPosition() < end
					|| comment.getStartPosition() >= next || root.getLineNumber(comment.getStartPosition()) != root.getLineNumber(end - 1)) continue;
			int start= comment.getStartPosition();
			StringBuilder text= new StringBuilder(source.substring(start, start + comment.getLength()));
			var tags= new ArrayList<NLSElement>();
			for (NLSLine line : lines) for (NLSElement literal : line.getElements()) {
				if (literal.hasTag() && contains(comment, literal.getTagPosition().getOffset())) tags.add(literal);
			}
			for (NLSElement tag : tags.reversed()) {
				int offset= tag.getTagPosition().getOffset() - start;
				text.delete(offset, offset + tag.getTagPosition().getLength());
			}
			String note= text.toString().strip();
			return note.isEmpty() || "//".equals(note) ? "" : " " + (note.startsWith("//") ? note : "// " + note); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
		}
		return ""; //$NON-NLS-1$
	}

	private static boolean contains(ASTNode node, int offset) {
		return offset >= node.getStartPosition() && offset < node.getStartPosition() + node.getLength();
	}

	private static String uniqueName(MethodInvocation invocation, ASTRewrite rewrite) {
		CompilationUnit root= (CompilationUnit) invocation.getRoot();
		Set<String> names;
		if (root.getProperty(NAMES) instanceof Names previous && previous.rewrite() == rewrite) {
			names= previous.names();
		} else {
			names= new HashSet<>();
			Set<String> collected= names;
			root.accept(new ASTVisitor() {
				@Override
				public boolean visit(SimpleName node) {
					collected.add(node.getIdentifier());
					return true;
				}
			});
			root.setProperty(NAMES, new Names(rewrite, names));
		}
		var scope= new ScopeAnalyzer(root);
		for (var binding : scope.getDeclarationsInScope(invocation.getStartPosition(), ScopeAnalyzer.VARIABLES)) {
			names.add(binding.getName());
		}
		String name= "junitMessage"; //$NON-NLS-1$
		for (int suffix= 2; !names.add(name); suffix++) name= "junitMessage" + suffix; //$NON-NLS-1$
		return name;
	}

	private static void insertBefore(MethodInvocation invocation, Statement declaration, ASTRewrite rewrite, TextEditGroup group) {
		if (invocation.getParent() instanceof ExpressionStatement statement) {
			if (statement.getParent() instanceof Block block) {
				rewrite.getListRewrite(block, Block.STATEMENTS_PROPERTY).insertBefore(declaration, statement, group);
			} else {
				Block block= invocation.getAST().newBlock();
				ASTNodes.getChildListProperty(block, Block.STATEMENTS_PROPERTY).add(declaration);
				ASTNodes.getChildListProperty(block, Block.STATEMENTS_PROPERTY).add(rewrite.createMoveTarget(statement));
				rewrite.replace(statement, block, group);
			}
		} else if (invocation.getParent() instanceof LambdaExpression lambda && lambda.getBody() == invocation) {
			Block block= invocation.getAST().newBlock();
			ASTNodes.getChildListProperty(block, Block.STATEMENTS_PROPERTY).add(declaration);
			ASTNodes.getChildListProperty(block, Block.STATEMENTS_PROPERTY)
					.add(invocation.getAST().newExpressionStatement((Expression) rewrite.createMoveTarget(invocation)));
			rewrite.set(lambda, LambdaExpression.BODY_PROPERTY, block, group);
		} else {
			throw new IllegalArgumentException("Cannot capture an assertion message at this evaluation position"); //$NON-NLS-1$
		}
	}

	static void recordMoved(ASTNode node, CompilationUnitRewrite rewrite) {
		if (node instanceof MethodInvocation invocation && node.getProperty(MOVED) instanceof Capture capture) {
			insertBefore(invocation, capture.declaration(), rewrite.getASTRewrite(), capture.group());
			EncodingSourceRewrite.record(rewrite, capture.message());
			node.setProperty(MOVED, null);
		}
	}
}
