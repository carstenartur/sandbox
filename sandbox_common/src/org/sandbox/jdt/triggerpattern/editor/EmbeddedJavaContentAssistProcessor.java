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
package org.sandbox.jdt.triggerpattern.editor;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.contentassist.CompletionProposal;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContentAssistProcessor;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationValidator;

/**
 * Content assist processor for embedded Java code ({@code <? ?>}) regions
 * in {@code .sandbox-hint} files.
 *
 * <p>Provides completion proposals for:</p>
 * <ul>
 *   <li>Java keywords ({@code public}, {@code boolean}, {@code return}, etc.)</li>
 *   <li>Common JDT AST types used in guard/fix functions</li>
 *   <li>Guard method signature templates</li>
 * </ul>
 *
 * @since 1.5.0
 */
public class EmbeddedJavaContentAssistProcessor implements IContentAssistProcessor {

	/**
	 * Java keywords commonly used in embedded guard/fix code.
	 */
	private static final String[] JAVA_KEYWORDS = {
		"abstract", //$NON-NLS-1$
		"boolean", //$NON-NLS-1$
		"break", //$NON-NLS-1$
		"case", //$NON-NLS-1$
		"catch", //$NON-NLS-1$
		"class", //$NON-NLS-1$
		"continue", //$NON-NLS-1$
		"default", //$NON-NLS-1$
		"do", //$NON-NLS-1$
		"else", //$NON-NLS-1$
		"extends", //$NON-NLS-1$
		"false", //$NON-NLS-1$
		"final", //$NON-NLS-1$
		"finally", //$NON-NLS-1$
		"for", //$NON-NLS-1$
		"if", //$NON-NLS-1$
		"implements", //$NON-NLS-1$
		"import", //$NON-NLS-1$
		"instanceof", //$NON-NLS-1$
		"int", //$NON-NLS-1$
		"interface", //$NON-NLS-1$
		"new", //$NON-NLS-1$
		"null", //$NON-NLS-1$
		"package", //$NON-NLS-1$
		"private", //$NON-NLS-1$
		"protected", //$NON-NLS-1$
		"public", //$NON-NLS-1$
		"return", //$NON-NLS-1$
		"static", //$NON-NLS-1$
		"super", //$NON-NLS-1$
		"switch", //$NON-NLS-1$
		"this", //$NON-NLS-1$
		"throw", //$NON-NLS-1$
		"throws", //$NON-NLS-1$
		"true", //$NON-NLS-1$
		"try", //$NON-NLS-1$
		"void", //$NON-NLS-1$
		"while", //$NON-NLS-1$
	};

	/**
	 * Common JDT AST node types used in guard/fix code.
	 */
	private static final String[][] AST_TYPE_PROPOSALS = {
		{ "ASTNode", "org.eclipse.jdt.core.dom.ASTNode" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "CompilationUnit", "org.eclipse.jdt.core.dom.CompilationUnit" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "MethodDeclaration", "org.eclipse.jdt.core.dom.MethodDeclaration" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "MethodInvocation", "org.eclipse.jdt.core.dom.MethodInvocation" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "TypeDeclaration", "org.eclipse.jdt.core.dom.TypeDeclaration" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "FieldDeclaration", "org.eclipse.jdt.core.dom.FieldDeclaration" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "VariableDeclarationFragment", "org.eclipse.jdt.core.dom.VariableDeclarationFragment" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "SimpleName", "org.eclipse.jdt.core.dom.SimpleName" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "QualifiedName", "org.eclipse.jdt.core.dom.QualifiedName" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "Expression", "org.eclipse.jdt.core.dom.Expression" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "Statement", "org.eclipse.jdt.core.dom.Statement" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "Block", "org.eclipse.jdt.core.dom.Block" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "ITypeBinding", "org.eclipse.jdt.core.dom.ITypeBinding" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "IMethodBinding", "org.eclipse.jdt.core.dom.IMethodBinding" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "IVariableBinding", "org.eclipse.jdt.core.dom.IVariableBinding" }, //$NON-NLS-1$ //$NON-NLS-2$
	};

	/**
	 * Template proposals for guard/fix method signatures.
	 */
	private static final String[][] TEMPLATE_PROPOSALS = {
		{ "guard method", "public boolean ${name}(ASTNode node) {\n    return true;\n}" }, //$NON-NLS-1$ //$NON-NLS-2$
		{ "fix method", "public void ${name}(ASTNode node, ASTRewrite rewrite) {\n    // rewrite logic\n}" }, //$NON-NLS-1$ //$NON-NLS-2$
	};

	@Override
	public ICompletionProposal[] computeCompletionProposals(ITextViewer viewer, int offset) {
		IDocument document = viewer.getDocument();
		String prefix = extractPrefix(document, offset);
		String lowerPrefix = prefix.toLowerCase();

		List<ICompletionProposal> proposals = new ArrayList<>();

		// Java keyword proposals
		for (String keyword : JAVA_KEYWORDS) {
			if (keyword.startsWith(lowerPrefix)) {
				proposals.add(createProposal(keyword, keyword, offset, prefix));
			}
		}

		// AST type proposals
		for (String[] entry : AST_TYPE_PROPOSALS) {
			String typeName = entry[0];
			String fqn = entry[1];
			if (typeName.toLowerCase().startsWith(lowerPrefix)) {
				proposals.add(createProposal(typeName, fqn, offset, prefix));
			}
		}

		// Template proposals (only when prefix is empty or starts with matching text)
		if (prefix.isEmpty()) {
			for (String[] entry : TEMPLATE_PROPOSALS) {
				String label = entry[0];
				String template = entry[1];
				proposals.add(new CompletionProposal(
						template, offset, 0, template.length(),
						null, label, null, label));
			}
		}

		return proposals.toArray(new ICompletionProposal[0]);
	}

	@Override
	public IContextInformation[] computeContextInformation(ITextViewer viewer, int offset) {
		return new IContextInformation[0];
	}

	@Override
	public char[] getCompletionProposalAutoActivationCharacters() {
		return new char[] { '.' };
	}

	@Override
	public char[] getContextInformationAutoActivationCharacters() {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public IContextInformationValidator getContextInformationValidator() {
		return null;
	}

	private ICompletionProposal createProposal(String replacement, String displayString,
			int offset, String prefix) {
		int replacementOffset = offset - prefix.length();
		int replacementLength = prefix.length();
		return new CompletionProposal(
				replacement, replacementOffset, replacementLength,
				replacement.length(), null, displayString, null, null);
	}

	/**
	 * Extracts the word prefix before the cursor position.
	 */
	private String extractPrefix(IDocument document, int offset) {
		try {
			int start = offset;
			while (start > 0) {
				char c = document.getChar(start - 1);
				if (!Character.isLetterOrDigit(c) && c != '_') {
					break;
				}
				start--;
			}
			return document.get(start, offset - start);
		} catch (BadLocationException e) {
			return ""; //$NON-NLS-1$
		}
	}
}
