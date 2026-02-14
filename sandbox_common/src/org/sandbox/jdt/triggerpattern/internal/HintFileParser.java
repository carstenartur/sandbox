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
package org.sandbox.jdt.triggerpattern.internal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Parser for {@code .sandbox-hint} files.
 * 
 * <p>Reads a text file containing transformation rules and produces a {@link HintFile}
 * data model. Supports comments, metadata directives, simple rules, guarded rules,
 * and multi-rewrite rules.</p>
 * 
 * <h2>File format</h2>
 * <pre>
 * // Line comments
 * /* Block comments * /
 *
 * &lt;!id: my.rule.id&gt;
 * &lt;!description: Descriptive text&gt;
 * &lt;!severity: warning&gt;
 * &lt;!minJavaVersion: 11&gt;
 * &lt;!tags: performance, modernization&gt;
 *
 * // Simple rule
 * source_pattern
 * =&gt; replacement_pattern
 * ;;
 *
 * // Rule with guard
 * source_pattern :: guard_expression
 * =&gt; replacement_pattern
 * ;;
 *
 * // Multi-rewrite rule
 * source_pattern :: guard_expression
 * =&gt; replacement1 :: condition1
 * =&gt; replacement2 :: otherwise
 * ;;
 *
 * // Hint-only (no rewrite)
 * "Warning message":
 * source_pattern :: guard_expression
 * ;;
 * </pre>
 * 
 * @since 1.3.2
 */
public final class HintFileParser {
	
	private final GuardExpressionParser guardParser = new GuardExpressionParser();
	
	/**
	 * Parses a {@code .sandbox-hint} file from a string.
	 * 
	 * @param content the file content
	 * @return the parsed hint file
	 * @throws HintParseException if the content cannot be parsed
	 */
	public HintFile parse(String content) throws HintParseException {
		if (content == null || content.isBlank()) {
			throw new HintParseException("Hint file content is empty", 0); //$NON-NLS-1$
		}
		try {
			return parse(new StringReader(content));
		} catch (IOException e) {
			throw new HintParseException("I/O error reading hint file: " + e.getMessage(), 0); //$NON-NLS-1$
		}
	}
	
	/**
	 * Parses a {@code .sandbox-hint} file from a reader.
	 * 
	 * @param reader the reader to read from
	 * @return the parsed hint file
	 * @throws HintParseException if the content cannot be parsed
	 * @throws IOException if an I/O error occurs
	 */
	public HintFile parse(Reader reader) throws HintParseException, IOException {
		HintFile hintFile = new HintFile();
		List<String> lines = readAndStripComments(reader);
		
		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i).trim();
			
			if (line.isEmpty()) {
				i++;
				continue;
			}
			
			// Metadata directive: <!key: value>
			if (line.startsWith("<!")) { //$NON-NLS-1$
				parseMetadata(hintFile, line, i + 1);
				i++;
				continue;
			}
			
			// Rule: collect lines until ;;
			i = parseRule(hintFile, lines, i);
		}
		
		return hintFile;
	}
	
	/**
	 * Reads all lines from a reader, stripping comments.
	 */
	private List<String> readAndStripComments(Reader reader) throws IOException {
		List<String> result = new ArrayList<>();
		boolean inBlockComment = false;
		
		try (BufferedReader br = new BufferedReader(reader)) {
			String rawLine;
			while ((rawLine = br.readLine()) != null) {
				if (inBlockComment) {
					int endIdx = rawLine.indexOf("*/"); //$NON-NLS-1$
					if (endIdx >= 0) {
						inBlockComment = false;
						rawLine = rawLine.substring(endIdx + 2);
					} else {
						result.add(""); //$NON-NLS-1$
						continue;
					}
				}
				
				// Process the line: strip line comments and start of block comments
				StringBuilder sb = new StringBuilder();
				int len = rawLine.length();
				for (int c = 0; c < len; c++) {
					char ch = rawLine.charAt(c);
					if (c + 1 < len) {
						if (ch == '/' && rawLine.charAt(c + 1) == '/') {
							break; // Line comment
						}
						if (ch == '/' && rawLine.charAt(c + 1) == '*') {
							int endIdx = rawLine.indexOf("*/", c + 2); //$NON-NLS-1$
							if (endIdx >= 0) {
								c = endIdx + 1;
								continue;
							}
							inBlockComment = true;
							break;
						}
					}
					sb.append(ch);
				}
				
				result.add(sb.toString());
			}
		}
		
		return result;
	}
	
	/**
	 * Parses a metadata directive line.
	 */
	private void parseMetadata(HintFile hintFile, String line, int lineNumber) throws HintParseException {
		// <!key: value>
		if (!line.endsWith(">")) { //$NON-NLS-1$
			throw new HintParseException("Invalid metadata directive (missing '>'): " + line, lineNumber); //$NON-NLS-1$
		}
		
		String inner = line.substring(2, line.length() - 1).trim();
		int colonIdx = inner.indexOf(':');
		if (colonIdx < 0) {
			throw new HintParseException("Invalid metadata directive (missing ':'): " + line, lineNumber); //$NON-NLS-1$
		}
		
		String key = inner.substring(0, colonIdx).trim();
		String value = inner.substring(colonIdx + 1).trim();
		
		switch (key) {
			case "id": //$NON-NLS-1$
				hintFile.setId(value);
				break;
			case "description": //$NON-NLS-1$
				hintFile.setDescription(value);
				break;
			case "severity": //$NON-NLS-1$
				hintFile.setSeverity(value);
				break;
			case "minJavaVersion": //$NON-NLS-1$
				try {
					hintFile.setMinJavaVersion(Integer.parseInt(value));
				} catch (NumberFormatException e) {
					throw new HintParseException("Invalid minJavaVersion: " + value, lineNumber); //$NON-NLS-1$
				}
				break;
			case "tags": //$NON-NLS-1$
				hintFile.setTags(Arrays.asList(value.split("\\s*,\\s*"))); //$NON-NLS-1$
				break;
			default:
				// Unknown metadata key is ignored for forward compatibility
				break;
		}
	}
	
	/**
	 * Parses a rule block starting at the given line index.
	 * 
	 * @return the next line index after the rule
	 */
	private int parseRule(HintFile hintFile, List<String> lines, int startIndex) throws HintParseException {
		// Collect all lines until ;;
		List<String> ruleLines = new ArrayList<>();
		int i = startIndex;
		boolean foundTerminator = false;
		
		while (i < lines.size()) {
			String line = lines.get(i).trim();
			i++;
			
			if (line.isEmpty()) {
				continue;
			}
			
			if (";;".equals(line)) { //$NON-NLS-1$
				foundTerminator = true;
				break;
			}
			
			ruleLines.add(line);
		}
		
		if (ruleLines.isEmpty()) {
			return i;
		}
		
		if (!foundTerminator) {
			throw new HintParseException(
					"Rule starting at line " + (startIndex + 1) + " is missing ';;' terminator", //$NON-NLS-1$ //$NON-NLS-2$
					startIndex + 1);
		}
		
		// Parse the rule lines
		String description = null;
		String sourcePatternText;
		GuardExpression sourceGuard = null;
		List<RewriteAlternative> alternatives = new ArrayList<>();
		
		int ruleLineIdx = 0;
		
		// Check for description prefix: "text":
		String firstLine = ruleLines.get(ruleLineIdx);
		if (firstLine.startsWith("\"") && firstLine.endsWith("\":")) { //$NON-NLS-1$ //$NON-NLS-2$
			description = firstLine.substring(1, firstLine.length() - 2);
			ruleLineIdx++;
			if (ruleLineIdx >= ruleLines.size()) {
				throw new HintParseException("Rule has description but no pattern", startIndex + 1); //$NON-NLS-1$
			}
			firstLine = ruleLines.get(ruleLineIdx);
		}
		
		// Parse source pattern line (may have :: guard)
		String[] sourceAndGuard = splitGuard(firstLine);
		sourcePatternText = sourceAndGuard[0].trim();
		if (sourceAndGuard[1] != null) {
			sourceGuard = guardParser.parse(sourceAndGuard[1].trim());
		}
		ruleLineIdx++;
		
		// Parse rewrite alternatives (lines starting with =>) and import directives
		ImportDirective currentImports = new ImportDirective();
		while (ruleLineIdx < ruleLines.size()) {
			String altLine = ruleLines.get(ruleLineIdx);
			
			// Import directives
			if (altLine.startsWith("addImport ")) { //$NON-NLS-1$
				currentImports.addImport(altLine.substring(10).trim());
				ruleLineIdx++;
				continue;
			}
			if (altLine.startsWith("removeImport ")) { //$NON-NLS-1$
				currentImports.removeImport(altLine.substring(13).trim());
				ruleLineIdx++;
				continue;
			}
			if (altLine.startsWith("addStaticImport ")) { //$NON-NLS-1$
				currentImports.addStaticImport(altLine.substring(16).trim());
				ruleLineIdx++;
				continue;
			}
			if (altLine.startsWith("removeStaticImport ")) { //$NON-NLS-1$
				currentImports.removeStaticImport(altLine.substring(19).trim());
				ruleLineIdx++;
				continue;
			}
			
			if (!altLine.startsWith("=>")) { //$NON-NLS-1$
				// Might be continuation of source pattern - for now, error
				throw new HintParseException(
						"Expected '=>' or ';;' but found: " + altLine, //$NON-NLS-1$
						startIndex + ruleLineIdx + 1);
			}
			
			String altContent = altLine.substring(2).trim();
			String[] altAndGuard = splitGuard(altContent);
			String replacementPattern = altAndGuard[0].trim();
			GuardExpression altGuard = null;
			
			if (altAndGuard[1] != null) {
				String guardText = altAndGuard[1].trim();
				if ("otherwise".equals(guardText)) { //$NON-NLS-1$
					altGuard = null; // otherwise = unconditional
				} else {
					altGuard = guardParser.parse(guardText);
				}
			}
			
			alternatives.add(new RewriteAlternative(replacementPattern, altGuard));
			ruleLineIdx++;
		}
		
		// Determine PatternKind from the source pattern text
		PatternKind kind = inferPatternKind(sourcePatternText);
		Pattern sourcePattern = new Pattern(sourcePatternText, kind);
		
		// Auto-detect imports from replacement patterns if no explicit imports given
		if (currentImports.isEmpty() && !alternatives.isEmpty()) {
			for (RewriteAlternative alt : alternatives) {
				ImportDirective detected = ImportDirective.detectFromPattern(alt.replacementPattern());
				currentImports.merge(detected);
			}
		}
		
		TransformationRule rule = new TransformationRule(
				description, sourcePattern, sourceGuard, alternatives, 
				currentImports.isEmpty() ? null : currentImports);
		hintFile.addRule(rule);
		
		return i;
	}
	
	/**
	 * Splits a line into pattern text and guard expression at the {@code ::} separator.
	 * 
	 * @param line the line to split
	 * @return array of [patternText, guardText] where guardText may be null
	 */
	private String[] splitGuard(String line) {
		// Find :: that is not inside parentheses or quotes
		int depth = 0;
		boolean inQuote = false;
		for (int c = 0; c < line.length() - 1; c++) {
			char ch = line.charAt(c);
			if (ch == '"') {
				inQuote = !inQuote;
			} else if (!inQuote) {
				if (ch == '(') {
					depth++;
				} else if (ch == ')') {
					depth--;
				} else if (depth == 0 && ch == ':' && line.charAt(c + 1) == ':') {
					return new String[] {
							line.substring(0, c),
							line.substring(c + 2)
					};
				}
			}
		}
		return new String[] { line, null };
	}
	
	/**
	 * Infers the {@link PatternKind} from the source pattern text.
	 * 
	 * <p>Heuristics:</p>
	 * <ul>
	 *   <li>Starts with {@code @} → ANNOTATION</li>
	 *   <li>Starts with {@code import } → IMPORT</li>
	 *   <li>Starts with {@code new } → CONSTRUCTOR</li>
	 *   <li>Starts with {@code {}} → BLOCK</li>
	 *   <li>Contains return type + name + parens → METHOD_CALL</li>
	 *   <li>Ends with {@code ;} → STATEMENT</li>
	 *   <li>Default → EXPRESSION</li>
	 * </ul>
	 */
	private PatternKind inferPatternKind(String patternText) {
		String trimmed = patternText.trim();
		
		if (trimmed.startsWith("@")) { //$NON-NLS-1$
			return PatternKind.ANNOTATION;
		}
		if (trimmed.startsWith("import ")) { //$NON-NLS-1$
			return PatternKind.IMPORT;
		}
		if (trimmed.startsWith("new ")) { //$NON-NLS-1$
			return PatternKind.CONSTRUCTOR;
		}
		if (trimmed.startsWith("{")) { //$NON-NLS-1$
			return PatternKind.BLOCK;
		}
		// Method call: contains '(' and '.' or is a simple name with parens
		if (trimmed.contains("(") && trimmed.contains(")") && !trimmed.endsWith(";")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return PatternKind.METHOD_CALL;
		}
		if (trimmed.endsWith(";")) { //$NON-NLS-1$
			return PatternKind.STATEMENT;
		}
		return PatternKind.EXPRESSION;
	}
	
	/**
	 * Exception thrown when parsing a {@code .sandbox-hint} file fails.
	 */
	public static class HintParseException extends Exception {
		private static final long serialVersionUID = 1L;
		private final int lineNumber;
		
		/**
		 * Creates a new parse exception.
		 * 
		 * @param message the error message
		 * @param lineNumber the line number where the error occurred
		 */
		public HintParseException(String message, int lineNumber) {
			super(message + " (line " + lineNumber + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			this.lineNumber = lineNumber;
		}
		
		/**
		 * Returns the line number where the error occurred.
		 * 
		 * @return the line number
		 */
		public int getLineNumber() {
			return lineNumber;
		}
	}
}
