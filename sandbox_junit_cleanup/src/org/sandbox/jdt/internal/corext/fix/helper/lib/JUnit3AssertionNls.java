/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper.lib;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSElement;
import org.eclipse.jdt.internal.corext.refactoring.nls.NLSScanner;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jface.text.BadLocationException;
import org.sandbox.jdt.triggerpattern.cleanup.EncodingSourceRewrite;

/** Tracks scanner-proven NLS tags when an assertion is split into several physical lines. */
record JUnit3AssertionNls(int lineStart, String line, List<NLSElement> literals) {
	static final JUnit3AssertionNls NONE= new JUnit3AssertionNls(0, "", List.of()); //$NON-NLS-1$
	static final JUnit3AssertionNls UNSAFE= new JUnit3AssertionNls(-1, "", List.of()); //$NON-NLS-1$
	private static final String MOVED= JUnit3AssertionNls.class.getName();
	private static final String ANALYSIS= MOVED + ".analysis"; //$NON-NLS-1$

	static JUnit3AssertionNls inspect(MethodInvocation invocation, boolean splitsStatement) {
		if (invocation.getProperty(ANALYSIS) instanceof JUnit3AssertionNls cached) return cached;
		JUnit3AssertionNls analysis= analyze(invocation, splitsStatement);
		invocation.setProperty(ANALYSIS, analysis);
		return analysis;
	}

	private static JUnit3AssertionNls analyze(MethodInvocation invocation, boolean splitsStatement) {
		CompilationUnit root= (CompilationUnit) invocation.getRoot();
		if (!(root.getJavaElement() instanceof ICompilationUnit unit)) return NONE;
		int first= root.getLineNumber(invocation.getStartPosition());
		int last= root.getLineNumber(invocation.getStartPosition() + invocation.getLength() - 1);
		try {
			IBuffer buffer= unit.getBuffer();
			int start= root.getPosition(first, 0);
			int end= root.getPosition(last + 1, 0);
			String text= buffer.getText(start, (end < 0 ? buffer.getLength() : end) - start);
			if (!text.contains(NLSElement.TAG_PREFIX)) return NONE;
			var lines= NLSScanner.scan(text);
			List<NLSElement> literals= new ArrayList<>();
			for (var scanned : lines) literals.addAll(List.of(scanned.getElements()));
			if (literals.stream().noneMatch(NLSElement::hasTag)) return NONE;
			// Tags on separate lines of retained arguments do not move with the prefix.
			Expression receiver= invocation.getExpression() instanceof MethodInvocation method ? method : null;
			Expression message= JUnit3SemanticSupport.hasLeadingMessage(invocation, invocation.resolveMethodBinding())
					? (Expression) invocation.arguments().getFirst() : null;
			boolean affectedLine= false;
			for (var scanned : lines) {
				int number= first + scanned.getLineNumber();
				if (List.of(scanned.getElements()).stream().anyMatch(NLSElement::hasTag)
						&& (onLine(root, receiver, number) || onLine(root, message, number))) affectedLine= true;
			}
			if (!affectedLine) return NONE;
			if (!splitsStatement && message != null) {
				var arguments= literals.stream().filter(e -> contains(invocation, start + e.getPosition().getOffset())).toList();
				int leading= (int) arguments.stream().filter(e -> contains(message, start + e.getPosition().getOffset())).count();
				if (leading == 0 || first == last && rotationPreservesTags(arguments, leading)) return NONE;
			}
			// Other strings on this physical line can change owners when the statement splits.
			if (first != last || literals.stream().anyMatch(e -> !contains(invocation, start + e.getPosition().getOffset()))) {
				return UNSAFE;
			}
			List<StringLiteral> nodes= new ArrayList<>();
			boolean[] nestedBody= { false };
			invocation.accept(new ASTVisitor() {
				@Override public boolean visit(StringLiteral literal) { nodes.add(literal); return false; }
				@Override public boolean visit(LambdaExpression lambda) { nestedBody[0]= true; return false; }
				@Override public boolean visit(AnonymousClassDeclaration type) { nestedBody[0]= true; return false; }
			});
			if (nestedBody[0] || nodes.size() != literals.size()
					|| EncodingSourceRewrite.literalsWithUnambiguousTags(root, unit, nodes).size() != nodes.size()) return UNSAFE;
			JUnit3AssertionNls analysis= new JUnit3AssertionNls(start, text, List.copyOf(literals));
			return analysis.canCopyPrefix(root, receiver) && analysis.canCopyPrefix(root, message) ? analysis : UNSAFE;
		} catch (JavaModelException | InvalidInputException | BadLocationException e) {
			return UNSAFE;
		}
	}

	private boolean canCopyPrefix(CompilationUnit root, Expression prefix) {
		if (prefix == null || literals.stream().noneMatch(e -> e.hasTag() && contains(prefix, lineStart + e.getPosition().getOffset()))) {
			return true;
		}
		int start= root.getExtendedStartPosition(prefix);
		return start >= lineStart && start + root.getExtendedLength(prefix) <= lineStart + line.length();
	}

	/** Moving the leading message to the end rotates the literal ordinals in the invocation. */
	private static boolean rotationPreservesTags(List<NLSElement> arguments, int leading) {
		for (int index= 0; index < arguments.size(); index++) {
			if (arguments.get(index).hasTag() != arguments.get((index + leading) % arguments.size()).hasTag()) return false;
		}
		return true;
	}

	private static boolean onLine(CompilationUnit root, Expression expression, int number) {
		return expression != null && root.getLineNumber(expression.getStartPosition()) <= number
				&& number <= root.getLineNumber(expression.getStartPosition() + expression.getLength() - 1);
	}

	private static boolean contains(ASTNode node, int offset) {
		return offset >= node.getStartPosition() && offset < node.getStartPosition() + node.getLength();
	}

	/**
	 * Return a tagged statement only where it needs a line-end comment. A move target would
	 * leave the comment on the original line. Record untagged literals too for renumbering.
	 */
	Statement taggedStatement(Expression expression, String prefix, int kind, ASTRewrite rewrite) {
		int count= 0;
		StringBuilder tags= new StringBuilder();
		for (var literal : literals) {
			if (contains(expression, lineStart + literal.getPosition().getOffset())) {
				count++;
				if (literal.hasTag()) tags.append(' ').append(NLSElement.createTagText(count));
			}
		}
		if (count > 0) expression.setProperty(MOVED, rewrite);
		if (tags.isEmpty()) return null;
		var range= rewrite.getExtendedSourceRangeComputer().computeSourceRange(expression);
		int offset= range.getStartPosition() - lineStart;
		return (Statement) rewrite.createStringPlaceholder(prefix + line.substring(offset, offset + range.getLength())
				+ ";" + tags, kind); //$NON-NLS-1$
	}

	static void recordMoved(Expression expression, CompilationUnitRewrite rewrite) {
		if (expression != null && expression.getProperty(MOVED) == rewrite.getASTRewrite()) {
			EncodingSourceRewrite.record(rewrite, expression);
			expression.setProperty(MOVED, null);
		}
	}
}
