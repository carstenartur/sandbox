/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.Arrays;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.compiler.IScanner;
import org.eclipse.jdt.core.compiler.ITerminalSymbols;
import org.eclipse.jdt.core.compiler.InvalidInputException;

/** Preserves source offsets while hiding literals and comments from Java name searches. */
public final class JavaSourceMask {

	private JavaSourceMask() {
	}

	public static String codeOnly(String source) {
		char[] code= source.toCharArray();
		IScanner scanner= ToolFactory.createScanner(true, false, false, JavaCore.VERSION_25);
		scanner.setSource(code);
		try {
			for (int token= scanner.getNextToken(); token != ITerminalSymbols.TokenNameEOF; token= scanner.getNextToken()) {
				if (token == ITerminalSymbols.TokenNameStringLiteral || token == ITerminalSymbols.TokenNameTextBlock
						|| token == ITerminalSymbols.TokenNameCharacterLiteral
						|| token == ITerminalSymbols.TokenNameSingleQuoteStringLiteral
						|| token == ITerminalSymbols.TokenNameCOMMENT_LINE || token == ITerminalSymbols.TokenNameCOMMENT_BLOCK
						|| token == ITerminalSymbols.TokenNameCOMMENT_JAVADOC || token == ITerminalSymbols.TokenNameCOMMENT_MARKDOWN) {
					Arrays.fill(code, scanner.getCurrentTokenStartPosition(), scanner.getCurrentTokenEndPosition() + 1, ' ');
				}
			}
		} catch (InvalidInputException invalidJava) {
			// A recovered token stream must never authorize an import or a textual rewrite.
			Arrays.fill(code, ' ');
		}
		return new String(code);
	}
}
