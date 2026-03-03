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
 *     Carsten Hammer
 *******************************************************************************/
package org.eclipse.jgit.storage.hibernate.search;

import java.io.IOException;
import java.io.Reader;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalToken;

/**
 * A Lucene {@link Tokenizer} that uses ECJ's {@link Scanner} to produce
 * lexically correct Java tokens.
 * <p>
 * Each emitted token carries a {@link TypeAttribute} distinguishing keywords,
 * identifiers, string literals, number literals, comments, annotations, and
 * operators. This enables downstream filters to apply different processing
 * strategies per token type.
 * </p>
 */
public final class EcjTokenizer extends Tokenizer {

	/** Type attribute value for Java keywords. */
	public static final String TYPE_KEYWORD = "KEYWORD"; //$NON-NLS-1$

	/** Type attribute value for Java identifiers. */
	public static final String TYPE_IDENTIFIER = "IDENTIFIER"; //$NON-NLS-1$

	/** Type attribute value for string literals. */
	public static final String TYPE_STRING_LITERAL = "STRING_LITERAL"; //$NON-NLS-1$

	/** Type attribute value for numeric literals. */
	public static final String TYPE_NUMBER_LITERAL = "NUMBER_LITERAL"; //$NON-NLS-1$

	/** Type attribute value for comments. */
	public static final String TYPE_COMMENT = "COMMENT"; //$NON-NLS-1$

	/** Type attribute value for annotations. */
	public static final String TYPE_ANNOTATION = "ANNOTATION"; //$NON-NLS-1$

	/** Type attribute value for operators and punctuation. */
	public static final String TYPE_OPERATOR = "OPERATOR"; //$NON-NLS-1$

	private final CharTermAttribute termAttr = addAttribute(
			CharTermAttribute.class);

	private final OffsetAttribute offsetAttr = addAttribute(
			OffsetAttribute.class);

	private final TypeAttribute typeAttr = addAttribute(TypeAttribute.class);

	private Scanner scanner;

	private char[] sourceChars;

	/**
	 * Create a new ECJ-based tokenizer.
	 */
	public EcjTokenizer() {
		super();
	}

	@Override
	public void reset() throws IOException {
		super.reset();
		sourceChars = readFully(input);
		scanner = new Scanner();
		scanner.tokenizeComments = true;
		scanner.recordLineSeparator = false;
		scanner.setSource(sourceChars);
	}

	@Override
	public boolean incrementToken() throws IOException {
		clearAttributes();
		if (scanner == null) {
			return false;
		}
		while (true) {
			TerminalToken token;
			try {
				token = scanner.getNextToken();
			} catch (InvalidInputException e) {
				// Graceful degradation on syntax errors: skip the bad token
				continue;
			}
			if (token == TerminalToken.TokenNameEOF) {
				return false;
			}
			String type = classifyToken(token);
			if (type == null) {
				// Skip whitespace and tokens with no search value
				continue;
			}
			int start = scanner.getCurrentTokenStartPosition();
			int end = scanner.getCurrentTokenEndPosition() + 1;
			String text = tokenText(token, start, end);
			if (text.isEmpty()) {
				continue;
			}
			termAttr.setEmpty().append(text);
			offsetAttr.setOffset(correctOffset(start),
					correctOffset(Math.min(end, sourceChars.length)));
			typeAttr.setType(type);
			return true;
		}
	}

	private String tokenText(TerminalToken token, int start, int end) {
		if (isStringLiteral(token)) {
			// Strip surrounding quotes from string/char literals
			if (end - start >= 2) {
				return new String(sourceChars, start + 1, end - start - 2);
			}
		}
		if (isAnnotation(token)) {
			// For annotations, skip the '@' prefix
			if (start + 1 < end && start + 1 < sourceChars.length) {
				return scanner.getCurrentTokenString();
			}
		}
		return new String(sourceChars, start, Math.min(end - start,
				sourceChars.length - start));
	}

	private static String classifyToken(TerminalToken token) {
		if (token == TerminalToken.TokenNameWHITESPACE) {
			return null;
		}
		if (token == TerminalToken.TokenNameIdentifier) {
			return TYPE_IDENTIFIER;
		}
		if (isKeyword(token)) {
			return TYPE_KEYWORD;
		}
		if (isStringLiteral(token)) {
			return TYPE_STRING_LITERAL;
		}
		if (isNumberLiteral(token)) {
			return TYPE_NUMBER_LITERAL;
		}
		if (isComment(token)) {
			return TYPE_COMMENT;
		}
		if (isAnnotation(token)) {
			return TYPE_ANNOTATION;
		}
		if (isOperatorOrPunctuation(token)) {
			return TYPE_OPERATOR;
		}
		return TYPE_OPERATOR;
	}

	private static boolean isKeyword(TerminalToken token) {
		String name = token.name();
		// Java keywords are named TokenNameXxx where xxx is the keyword
		// e.g. TokenNamepublic, TokenNameclass, TokenNameint, etc.
		if (name.startsWith("TokenName") //$NON-NLS-1$
				&& name.length() > "TokenName".length()) { //$NON-NLS-1$
			char c = name.charAt("TokenName".length()); //$NON-NLS-1$
			// Keywords start with lowercase after "TokenName"
			// Identifiers and literals start with uppercase
			if (Character.isLowerCase(c)
					&& token != TerminalToken.TokenNameIdentifier) {
				return true;
			}
		}
		return false;
	}

	private static boolean isStringLiteral(TerminalToken token) {
		return token == TerminalToken.TokenNameStringLiteral
				|| token == TerminalToken.TokenNameCharacterLiteral
				|| token == TerminalToken.TokenNameTextBlock
				|| token == TerminalToken.TokenNameSingleQuoteStringLiteral;
	}

	private static boolean isNumberLiteral(TerminalToken token) {
		return token == TerminalToken.TokenNameIntegerLiteral
				|| token == TerminalToken.TokenNameLongLiteral
				|| token == TerminalToken.TokenNameFloatingPointLiteral
				|| token == TerminalToken.TokenNameDoubleLiteral;
	}

	private static boolean isComment(TerminalToken token) {
		return token == TerminalToken.TokenNameCOMMENT_LINE
				|| token == TerminalToken.TokenNameCOMMENT_BLOCK
				|| token == TerminalToken.TokenNameCOMMENT_JAVADOC
				|| token == TerminalToken.TokenNameCOMMENT_MARKDOWN;
	}

	private static boolean isAnnotation(TerminalToken token) {
		return token == TerminalToken.TokenNameAT;
	}

	private static boolean isOperatorOrPunctuation(TerminalToken token) {
		String name = token.name();
		return name.startsWith("TokenName") //$NON-NLS-1$
				&& name.length() > "TokenName".length() //$NON-NLS-1$
				&& Character.isUpperCase(
						name.charAt("TokenName".length())); //$NON-NLS-1$
	}

	private static char[] readFully(Reader reader) throws IOException {
		StringBuilder sb = new StringBuilder(4096);
		char[] buf = new char[4096];
		int n;
		while ((n = reader.read(buf)) != -1) {
			sb.append(buf, 0, n);
		}
		return sb.toString().toCharArray();
	}
}
