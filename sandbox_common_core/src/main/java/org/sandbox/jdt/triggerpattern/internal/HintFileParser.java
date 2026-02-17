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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 * multi-rewrite rules, and foreach expansion.</p>
 * 
 * <p>Import directives are automatically inferred from fully qualified names (FQNs)
 * in source and replacement patterns. No explicit import directives are needed.</p>
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
 * &lt;!include: other.hint.id&gt;
 *
 * // Foreach expansion: define a variable with key-value pairs
 * // Rules using ${VAR} and ${VAR_CONSTANT} are expanded for each entry
 * &lt;!foreach CHARSET: "UTF-8" -&gt; UTF_8, "ISO-8859-1" -&gt; ISO_8859_1&gt;
 *
 * $s.getBytes("${CHARSET}") :: sourceVersionGE(7)
 * =&gt; $s.getBytes(java.nio.charset.StandardCharsets.${CHARSET_CONSTANT})
 * ;;
 *
 * // Simple rule with FQN-based imports
 * org.junit.Assert.assertEquals($expected, $actual)
 * =&gt; org.junit.jupiter.api.Assertions.assertEquals($expected, $actual)
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
	
	private record GuardSplit(String patternText, String guardText) {
		boolean hasGuard() { return guardText != null; }
	}
	
	private final GuardExpressionParser guardParser = new GuardExpressionParser();
	
	/**
	 * Foreach variable definitions: variable name → ordered map of key→value pairs.
	 * <p>Example: {@code <!foreach CHARSET: "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1>}
	 * creates a mapping {@code CHARSET → {"UTF-8" → "UTF_8", "ISO-8859-1" → "ISO_8859_1"}}.</p>
	 */
	private final Map<String, Map<String, String>> foreachVariables = new HashMap<>();
	
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
		foreachVariables.clear();
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
			case "include": //$NON-NLS-1$
				hintFile.addInclude(value);
				break;
			default:
				// Check for foreach directive: <!foreach VARNAME: key1 -> val1, key2 -> val2>
				if (key.startsWith("foreach ")) { //$NON-NLS-1$
					parseForeachDirective(key, value, lineNumber);
				}
				// Unknown metadata key is ignored for forward compatibility
				break;
		}
	}
	
	/**
	 * Parses a {@code <!foreach>} directive and stores the variable mapping.
	 * 
	 * <p>Syntax: {@code <!foreach VARNAME: "key1" -> val1, "key2" -> val2, ...>}</p>
	 * <p>The variable can then be used in rules as {@code ${VARNAME}} (expands to the key)
	 * and {@code ${VARNAME_CONSTANT}} (expands to the value).</p>
	 * 
	 * @param key the directive key (e.g., "foreach CHARSET")
	 * @param value the directive value (e.g., {@code "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1})
	 * @param lineNumber the line number for error reporting
	 */
	private void parseForeachDirective(String key, String value, int lineNumber) throws HintParseException {
		String varName = key.substring("foreach ".length()).trim(); //$NON-NLS-1$
		if (varName.isEmpty()) {
			throw new HintParseException("foreach directive requires a variable name", lineNumber); //$NON-NLS-1$
		}
		
		Map<String, String> mappings = new LinkedHashMap<>();
		List<String> entries = splitForeachEntries(value);
		for (String entry : entries) {
			entry = entry.trim();
			if (entry.isEmpty()) {
				continue;
			}
			int arrowIdx = entry.indexOf("->"); //$NON-NLS-1$
			if (arrowIdx < 0) {
				throw new HintParseException(
						"foreach entry must use 'key -> value' syntax: " + entry, lineNumber); //$NON-NLS-1$
			}
			String entryKey = entry.substring(0, arrowIdx).trim();
			String entryValue = entry.substring(arrowIdx + 2).trim();
			// Strip quotes from key if present
			if (entryKey.startsWith("\"") && entryKey.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
				entryKey = entryKey.substring(1, entryKey.length() - 1);
			}
			mappings.put(entryKey, entryValue);
		}
		
		if (mappings.isEmpty()) {
			throw new HintParseException("foreach directive has no entries", lineNumber); //$NON-NLS-1$
		}
		
		foreachVariables.put(varName, mappings);
	}
	
	/**
	 * Splits foreach entries at commas, respecting quoted strings.
	 * Commas inside double quotes are not treated as entry separators.
	 * 
	 * @param value the foreach value string (e.g., {@code "UTF-8" -> UTF_8, "ISO-8859-1" -> ISO_8859_1})
	 * @return list of entry strings
	 */
	private List<String> splitForeachEntries(String value) {
		List<String> entries = new ArrayList<>();
		boolean inQuotes = false;
		int start = 0;
		for (int c = 0; c < value.length(); c++) {
			char ch = value.charAt(c);
			if (ch == '"') {
				inQuotes = !inQuotes;
			} else if (ch == ',' && !inQuotes) {
				entries.add(value.substring(start, c).trim());
				start = c + 1;
			}
		}
		// Add last entry
		String last = value.substring(start).trim();
		if (!last.isEmpty()) {
			entries.add(last);
		}
		return entries;
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
		
		// Check if the rule uses foreach variables — expand if so
		String foreachVar = findForeachVariable(ruleLines);
		if (foreachVar != null) {
			expandForeachRule(hintFile, ruleLines, foreachVar, startIndex);
		} else {
			buildRule(hintFile, ruleLines, startIndex);
		}
		
		return i;
	}
	
	/**
	 * Checks if any rule line contains a {@code ${VAR}} reference to a defined foreach variable.
	 * 
	 * @return the variable name if found, or {@code null}
	 */
	private String findForeachVariable(List<String> ruleLines) {
		for (String line : ruleLines) {
			for (String varName : foreachVariables.keySet()) {
				if (line.contains("${" + varName + "}") || line.contains("${" + varName + "_CONSTANT}")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					return varName;
				}
			}
		}
		return null;
	}
	
	/**
	 * Expands a rule template for each entry in the foreach variable's mapping.
	 * 
	 * <p>{@code ${VAR}} is replaced with the key (e.g., {@code "UTF-8"})
	 * and {@code ${VAR_CONSTANT}} is replaced with the value (e.g., {@code UTF_8}).</p>
	 */
	private void expandForeachRule(HintFile hintFile, List<String> ruleLines, 
			String varName, int startIndex) throws HintParseException {
		Map<String, String> mappings = foreachVariables.get(varName);
		String keyPlaceholder = "${" + varName + "}"; //$NON-NLS-1$ //$NON-NLS-2$
		String valuePlaceholder = "${" + varName + "_CONSTANT}"; //$NON-NLS-1$ //$NON-NLS-2$
		
		for (Map.Entry<String, String> entry : mappings.entrySet()) {
			List<String> expandedLines = new ArrayList<>();
			for (String line : ruleLines) {
				String expanded = line.replace(keyPlaceholder, entry.getKey())
						.replace(valuePlaceholder, entry.getValue());
				expandedLines.add(expanded);
			}
			buildRule(hintFile, expandedLines, startIndex);
		}
	}
	
	/**
	 * Builds a single transformation rule from the given rule lines and adds it to the hint file.
	 */
	private void buildRule(HintFile hintFile, List<String> ruleLines, int startIndex) throws HintParseException {
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
		GuardSplit sourceAndGuard = splitGuard(firstLine);
		sourcePatternText = sourceAndGuard.patternText().trim();
		if (sourceAndGuard.hasGuard()) {
			sourceGuard = guardParser.parse(sourceAndGuard.guardText().trim());
		}
		ruleLineIdx++;
		
		// Check for continuation lines with guard expressions (start with ::)
		while (ruleLineIdx < ruleLines.size()) {
			String nextLine = ruleLines.get(ruleLineIdx).trim();
			if (!nextLine.startsWith("::")) { //$NON-NLS-1$
				break;
			}
			String guardText = nextLine.substring(2).trim();
			if (sourceGuard != null) {
				// Combine with existing guard using AND
				sourceGuard = new GuardExpression.And(
						sourceGuard, guardParser.parse(guardText));
			} else {
				sourceGuard = guardParser.parse(guardText);
			}
			ruleLineIdx++;
		}
		
		// Parse rewrite alternatives (lines starting with =>)
		while (ruleLineIdx < ruleLines.size()) {
			String altLine = ruleLines.get(ruleLineIdx);
			
			if (!altLine.startsWith("=>")) { //$NON-NLS-1$
				// Might be continuation of source pattern - for now, error
				throw new HintParseException(
						"Expected '=>' or ';;' but found: " + altLine, //$NON-NLS-1$
						startIndex + ruleLineIdx + 1);
			}
			
			String altContent = altLine.substring(2).trim();
			GuardSplit altAndGuard = splitGuard(altContent);
			String replacementPattern = altAndGuard.patternText().trim();
			GuardExpression altGuard = null;
			
			if (altAndGuard.hasGuard()) {
				String guardText = altAndGuard.guardText().trim();
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
		
		// FQN-based import inference: automatically derive imports from
		// fully qualified names in source and replacement patterns
		ImportDirective currentImports = new ImportDirective();
		if (!alternatives.isEmpty()) {
			List<String> replacementTexts = new ArrayList<>();
			for (RewriteAlternative alt : alternatives) {
				replacementTexts.add(alt.replacementPattern());
			}
			currentImports = ImportDirective.inferFromFqnPatterns(sourcePatternText, replacementTexts);
		}
		
		TransformationRule rule = new TransformationRule(
				description, sourcePattern, sourceGuard, alternatives, 
				currentImports.isEmpty() ? null : currentImports);
		hintFile.addRule(rule);
	}
	
	/**
	 * Splits a line into pattern text and guard expression at the {@code ::} separator.
	 * 
	 * @param line the line to split
	 * @return a {@link GuardSplit} with the pattern text and optional guard text
	 */
	private GuardSplit splitGuard(String line) {
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
					return new GuardSplit(
							line.substring(0, c),
							line.substring(c + 2));
				}
			}
		}
		return new GuardSplit(line, null);
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
