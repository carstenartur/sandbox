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
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.dom.ASTNode;

import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;
import org.sandbox.jdt.triggerpattern.api.GuardExpression;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.ImportDirective;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.RewriteAlternative;
import org.sandbox.jdt.triggerpattern.api.Severity;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;

/**
 * Parser for {@code .sandbox-hint} and NetBeans {@code .hint} files.
 * 
 * <p>Reads a text file containing transformation rules and produces a {@link HintFile}
 * data model. Supports comments, metadata directives, simple rules, guarded rules,
 * multi-rewrite rules, and foreach expansion.</p>
 * 
 * <p>Import directives are automatically inferred from fully qualified names (FQNs)
 * in source and replacement patterns. No explicit import directives are needed.</p>
 * 
 * <h2>NetBeans compatibility</h2>
 * <ul>
 *   <li>{@code <? ?>} embedded Java code blocks are extracted and stored as
 *       {@link org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock} entries
 *       in the {@link HintFile}. These blocks can contain custom guard functions,
 *       fix functions, imports, or helper methods.</li>
 *   <li>Metadata directives support both {@code <!key: value>} (sandbox format)
 *       and {@code <!key="value">} (NetBeans format).</li>
 * </ul>
 * 
 * <h2>File format</h2>
 * <pre>
 * // Line comments
 * /* Block comments * /
 *
 * // Embedded Java code blocks (extracted)
 * &lt;? import java.util.*; ?&gt;
 *
 * &lt;!id: my.rule.id&gt;
 * &lt;!description: Descriptive text&gt;
 * &lt;!description="NetBeans style metadata"&gt;
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
	
	private static final Logger LOGGER = Logger.getLogger(HintFileParser.class.getName());

	/**
	 * Set of recognized value-less directive names (directives without ':' or '=').
	 */
	private static final Set<String> VALUELESS_DIRECTIVES = Set.of("caseInsensitive"); //$NON-NLS-1$
	
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
	 * Map variable definitions: map name → ordered map of key→value pairs.
	 * <p>Example: {@code <!map charsetNames: "UTF-8" => "StandardCharsets.UTF_8", "US-ASCII" => "StandardCharsets.US_ASCII">}
	 * creates a mapping {@code charsetNames → {"UTF-8" → "StandardCharsets.UTF_8", "US-ASCII" → "StandardCharsets.US_ASCII"}}.</p>
	 * <p>In rules, {@code #{charsetNames}} is replaced with the key and the corresponding value
	 * replaces {@code #{charsetNames_VALUE}}.</p>
	 */
	private final Map<String, Map<String, String>> mapVariables = new HashMap<>();
	
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
		mapVariables.clear();
		List<String> lines = readAndStripComments(reader, hintFile);
		
		int i = 0;
		while (i < lines.size()) {
			String line = lines.get(i).trim();
			
			if (line.isEmpty()) {
				i++;
				continue;
			}
			
			// Metadata directive: <!key: value> (may span multiple lines)
			if (line.startsWith("<!")) { //$NON-NLS-1$
				StringBuilder metaBuilder = new StringBuilder(line);
				int metaStart = i;
				boolean foundClosingBracket = line.endsWith(">"); //$NON-NLS-1$
				while (!foundClosingBracket && i + 1 < lines.size()) {
					i++;
					String nextLine = lines.get(i).trim();
					metaBuilder.append(' ').append(nextLine);
					if (nextLine.endsWith(">")) { //$NON-NLS-1$
						foundClosingBracket = true;
					}
				}
				parseMetadata(hintFile, metaBuilder.toString().trim(), metaStart + 1);
				i++;
				continue;
			}
			
			// Rule: collect lines until ;;
			i = parseRule(hintFile, lines, i);
		}
		
		return hintFile;
	}
	
	/**
	 * Reads all lines from a reader, stripping comments and extracting {@code <? ?>} blocks.
	 * 
	 * <p>{@code <? ?>} blocks contain embedded Java code. Previously these were skipped;
	 * now they are extracted and stored as {@link EmbeddedJavaBlock} entries in the
	 * {@link HintFile} for compilation and execution.</p>
	 */
	private List<String> readAndStripComments(Reader reader, HintFile hintFile) throws IOException {
		List<String> result = new ArrayList<>();
		boolean inBlockComment = false;
		boolean inCustomCodeBlock = false;
		int customCodeStartLine = 0;
		int customCodeStartOffset = 0;
		StringBuilder customCodeBuilder = null;

		// Read entire content to compute actual line offsets (handles CRLF vs LF)
		String fullContent;
		try (BufferedReader br = new BufferedReader(reader)) {
			StringBuilder contentBuilder = new StringBuilder();
			char[] buffer = new char[4096];
			int read;
			while ((read = br.read(buffer)) != -1) {
				contentBuilder.append(buffer, 0, read);
			}
			fullContent = contentBuilder.toString();
		}

		// Split into lines preserving delimiter info for offset computation
		List<String> rawLines = new ArrayList<>();
		List<Integer> lineStartOffsets = new ArrayList<>();
		int pos = 0;
		while (pos <= fullContent.length()) {
			lineStartOffsets.add(pos);
			int nextLF = fullContent.indexOf('\n', pos);
			int nextCR = fullContent.indexOf('\r', pos);
			int lineEnd;
			int nextLineStart;
			if (nextLF == -1 && nextCR == -1) {
				// Last line without trailing newline
				rawLines.add(fullContent.substring(pos));
				break;
			} else if (nextCR >= 0 && (nextLF == -1 || nextCR < nextLF)) {
				lineEnd = nextCR;
				// CRLF or just CR
				nextLineStart = (nextCR + 1 < fullContent.length() && fullContent.charAt(nextCR + 1) == '\n')
						? nextCR + 2 : nextCR + 1;
			} else {
				lineEnd = nextLF;
				nextLineStart = nextLF + 1;
			}
			rawLines.add(fullContent.substring(pos, lineEnd));
			pos = nextLineStart;
		}

		for (int lineIdx = 0; lineIdx < rawLines.size(); lineIdx++) {
			String rawLine = rawLines.get(lineIdx);
			int lineNumber = lineIdx + 1;
			int lineStartOffset = lineStartOffsets.get(lineIdx);
				
				// Handle <? ?> custom code blocks (continuation)
				if (inCustomCodeBlock) {
					if (rawLine.contains("?>")) { //$NON-NLS-1$
						int endIdx = rawLine.indexOf("?>"); //$NON-NLS-1$
						customCodeBuilder.append(rawLine, 0, endIdx);
						int blockEndOffset = lineStartOffset + endIdx + 2;
						hintFile.addEmbeddedJavaBlock(new EmbeddedJavaBlock(
								customCodeBuilder.toString(),
								customCodeStartLine, lineNumber,
								customCodeStartOffset, blockEndOffset));
						LOGGER.log(Level.FINE, "Extracted embedded Java block (multi-line, lines {0}-{1})", //$NON-NLS-1$
								new Object[] { customCodeStartLine, lineNumber });
						inCustomCodeBlock = false;
						customCodeBuilder = null;
						rawLine = rawLine.substring(endIdx + 2);
					} else {
						customCodeBuilder.append(rawLine).append('\n');
						result.add(""); //$NON-NLS-1$
						continue;
					}
				}
				
				// Check for start of <? ?> block(s) on this line
				while (!inBlockComment) {
					int startIdx = rawLine.indexOf("<?"); //$NON-NLS-1$
					if (startIdx < 0) {
						break;
					}
					int endIdx = rawLine.indexOf("?>", startIdx + 2); //$NON-NLS-1$
					if (endIdx >= 0) {
						// Single-line <? ?> block
						String javaSource = rawLine.substring(startIdx + 2, endIdx);
						int blockStartOffset = lineStartOffset + startIdx;
						int blockEndOffset = lineStartOffset + endIdx + 2;
						hintFile.addEmbeddedJavaBlock(new EmbeddedJavaBlock(
								javaSource, lineNumber, lineNumber,
								blockStartOffset, blockEndOffset));
						LOGGER.log(Level.FINE, "Extracted embedded Java block (single line {0})", lineNumber); //$NON-NLS-1$
						rawLine = rawLine.substring(0, startIdx) + rawLine.substring(endIdx + 2);
						// Continue loop to check for further blocks on the same line
					} else {
						// Multi-line <? ?> block
						customCodeStartLine = lineNumber;
						customCodeStartOffset = lineStartOffset + startIdx;
						customCodeBuilder = new StringBuilder();
						customCodeBuilder.append(rawLine.substring(startIdx + 2)).append('\n');
						LOGGER.log(Level.FINE, "Start of embedded Java block (multi-line, line {0})", lineNumber); //$NON-NLS-1$
						inCustomCodeBlock = true;
						rawLine = rawLine.substring(0, startIdx);
						break;
					}
				}
				
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
		
		return result;
	}
	
	/**
	 * Parses a metadata directive line.
	 * 
	 * <p>Supports both {@code <!key: value>} (sandbox format) and 
	 * {@code <!key="value">} (NetBeans format) syntaxes.</p>
	 */
	private void parseMetadata(HintFile hintFile, String line, int lineNumber) throws HintParseException {
		// <!key: value> or <!key="value">
		if (!line.endsWith(">")) { //$NON-NLS-1$
			throw new HintParseException("Invalid metadata directive (missing '>'): " + line, lineNumber); //$NON-NLS-1$
		}
		
		String inner = line.substring(2, line.length() - 1).trim();
		
		// Prefer colon format for backward compatibility; fall back to equals format
		int colonIdx = inner.indexOf(':');
		int equalsIdx = inner.indexOf('=');
		
		String key;
		String value;
		
		if (colonIdx >= 0 && !inner.substring(0, colonIdx).contains("=")) { //$NON-NLS-1$
			// Sandbox format: key: value (preferred for backward compatibility)
			key = inner.substring(0, colonIdx).trim();
			value = inner.substring(colonIdx + 1).trim();
		} else if (equalsIdx > 0) {
			// NetBeans format: key="value" or key=value
			key = inner.substring(0, equalsIdx).trim();
			value = inner.substring(equalsIdx + 1).trim();
			// Strip surrounding quotes if present
			if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
				value = value.substring(1, value.length() - 1);
			}
		} else {
			// Check for known value-less directives (e.g., <!caseInsensitive>)
			if (VALUELESS_DIRECTIVES.contains(inner.trim())) {
				key = inner.trim();
				value = ""; //$NON-NLS-1$
			} else {
				throw new HintParseException("Invalid metadata directive (missing ':' or '='): " + line, lineNumber); //$NON-NLS-1$
			}
		}
		
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
			case "caseInsensitive": //$NON-NLS-1$
				hintFile.setCaseInsensitive(true);
				break;
			case "suppressWarnings": //$NON-NLS-1$
				for (String sw : value.split("\\s*,\\s*")) { //$NON-NLS-1$
					hintFile.addSuppressWarnings(sw);
				}
				break;
			case "treeKind": //$NON-NLS-1$
				parseTreeKindDirective(hintFile, value, lineNumber);
				break;
			default:
				// Check for foreach directive: <!foreach VARNAME: key1 -> val1, key2 -> val2>
				if (key.startsWith("foreach ")) { //$NON-NLS-1$
					parseForeachDirective(key, value, lineNumber);
				}
				// Check for map directive: <!map MAPNAME: "key1" => "val1", "key2" => "val2">
				else if (key.startsWith("map ")) { //$NON-NLS-1$
					parseMapDirective(key, value, lineNumber);
				}
				// Unknown metadata key is ignored for forward compatibility
				break;
		}
	}
	
	/**
	 * AST node type name to {@link ASTNode} node type constant mapping.
	 * Populated lazily via reflection on first use.
	 */
	private static volatile Map<String, Integer> astNodeTypeMap;

	/**
	 * Returns the AST node type constant map, initializing it lazily.
	 */
	private static Map<String, Integer> getAstNodeTypeMap() {
		if (astNodeTypeMap == null) {
			synchronized (HintFileParser.class) {
				if (astNodeTypeMap == null) {
					astNodeTypeMap = buildAstNodeTypeMap();
				}
			}
		}
		return astNodeTypeMap;
	}

	/**
	 * Builds a map from AST node type names (e.g., "METHOD_DECLARATION") to
	 * their integer constants using reflection on {@link ASTNode}.
	 */
	private static Map<String, Integer> buildAstNodeTypeMap() {
		Map<String, Integer> map = new HashMap<>();
		for (java.lang.reflect.Field field : ASTNode.class.getDeclaredFields()) {
			if (field.getType() == int.class
					&& java.lang.reflect.Modifier.isPublic(field.getModifiers())
					&& java.lang.reflect.Modifier.isStatic(field.getModifiers())
					&& java.lang.reflect.Modifier.isFinal(field.getModifiers())) {
				try {
					map.put(field.getName(), field.getInt(null));
				} catch (IllegalAccessException e) {
					LOGGER.log(Level.FINE, "Cannot access ASTNode field: " + field.getName(), e); //$NON-NLS-1$
				}
			}
		}
		return map;
	}

	/**
	 * Parses a {@code <!treeKind:>} directive and sets the node type filter.
	 *
	 * <p>Syntax: {@code <!treeKind: METHOD_DECLARATION, IF_STATEMENT>}</p>
	 *
	 * @param hintFile the hint file to update
	 * @param value the comma-separated list of AST node type names
	 * @param lineNumber the line number for error reporting
	 */
	private void parseTreeKindDirective(HintFile hintFile, String value, int lineNumber) throws HintParseException {
		if (value == null || value.isBlank()) {
			throw new HintParseException("treeKind directive requires at least one node type", lineNumber); //$NON-NLS-1$
		}
		Map<String, Integer> typeMap = getAstNodeTypeMap();
		List<Integer> nodeTypes = new ArrayList<>();
		for (String name : value.split("\\s*,\\s*")) { //$NON-NLS-1$
			String trimmed = name.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			Integer nodeType = typeMap.get(trimmed);
			if (nodeType == null) {
				throw new HintParseException("Unknown AST node type: " + trimmed, lineNumber); //$NON-NLS-1$
			}
			nodeTypes.add(nodeType);
		}
		if (nodeTypes.isEmpty()) {
			throw new HintParseException("treeKind directive requires at least one node type", lineNumber); //$NON-NLS-1$
		}
		hintFile.setTreeKindNodeTypes(nodeTypes);
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
	 * Parses a {@code <!map>} directive and stores the variable mapping.
	 *
	 * <p>Syntax: {@code <!map MAPNAME: "key1" => "val1", "key2" => "val2", ...>}</p>
	 * <p>The map can then be used in rules as {@code #{MAPNAME}} (expands to the key)
	 * and {@code #{MAPNAME_VALUE}} (expands to the value). Rules containing map
	 * references are expanded for each entry in the map, similar to {@code <!foreach>}.</p>
	 *
	 * <p>The {@code =>} separator distinguishes map entries from foreach's {@code ->}.</p>
	 *
	 * @param key the directive key (e.g., "map charsetNames")
	 * @param value the directive value (e.g., {@code "UTF-8" => "StandardCharsets.UTF_8", ...})
	 * @param lineNumber the line number for error reporting
	 * @since 1.4.2
	 */
	private void parseMapDirective(String key, String value, int lineNumber) throws HintParseException {
		String mapName = key.substring("map ".length()).trim(); //$NON-NLS-1$
		if (mapName.isEmpty()) {
			throw new HintParseException("map directive requires a name", lineNumber); //$NON-NLS-1$
		}

		Map<String, String> mappings = new LinkedHashMap<>();
		List<String> entries = splitForeachEntries(value); // reuse comma-respecting split
		for (String entry : entries) {
			entry = entry.trim();
			if (entry.isEmpty()) {
				continue;
			}
			int arrowIdx = entry.indexOf("=>"); //$NON-NLS-1$
			if (arrowIdx < 0) {
				throw new HintParseException(
						"map entry must use 'key => value' syntax: " + entry, lineNumber); //$NON-NLS-1$
			}
			String entryKey = entry.substring(0, arrowIdx).trim();
			String entryValue = entry.substring(arrowIdx + 2).trim();
			// Strip quotes from key and value if present
			if (entryKey.startsWith("\"") && entryKey.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
				entryKey = entryKey.substring(1, entryKey.length() - 1);
			}
			if (entryValue.startsWith("\"") && entryValue.endsWith("\"")) { //$NON-NLS-1$ //$NON-NLS-2$
				entryValue = entryValue.substring(1, entryValue.length() - 1);
			}
			mappings.put(entryKey, entryValue);
		}

		if (mappings.isEmpty()) {
			throw new HintParseException("map directive has no entries", lineNumber); //$NON-NLS-1$
		}

		mapVariables.put(mapName, mappings);
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
			// Check if the rule uses map variables — expand if so
			String mapVar = findMapVariable(ruleLines);
			if (mapVar != null) {
				expandMapRule(hintFile, ruleLines, mapVar, startIndex);
			} else {
				buildRule(hintFile, ruleLines, startIndex);
			}
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
	 * Checks if any rule line contains a {@code #{MAP}} reference to a defined map variable.
	 *
	 * @return the map name if found, or {@code null}
	 * @since 1.4.2
	 */
	private String findMapVariable(List<String> ruleLines) {
		for (String line : ruleLines) {
			for (String mapName : mapVariables.keySet()) {
				if (line.contains("#{" + mapName + "}") || line.contains("#{" + mapName + "_VALUE}")) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					return mapName;
				}
			}
		}
		return null;
	}

	/**
	 * Expands a rule template for each entry in the map variable's mapping.
	 *
	 * <p>{@code #{MAP}} is replaced with the key and
	 * {@code #{MAP_VALUE}} is replaced with the value.</p>
	 *
	 * @since 1.4.2
	 */
	private void expandMapRule(HintFile hintFile, List<String> ruleLines,
			String mapName, int startIndex) throws HintParseException {
		Map<String, String> mappings = mapVariables.get(mapName);
		String keyPlaceholder = "#{" + mapName + "}"; //$NON-NLS-1$ //$NON-NLS-2$
		String valuePlaceholder = "#{" + mapName + "_VALUE}"; //$NON-NLS-1$ //$NON-NLS-2$

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
		String ruleId = null;
		Severity ruleSeverity = null;
		String sourcePatternText;
		GuardExpression sourceGuard = null;
		List<RewriteAlternative> alternatives = new ArrayList<>();
		
		int ruleLineIdx = 0;
		
		// Check for per-rule metadata annotations: @id:, @severity:
		while (ruleLineIdx < ruleLines.size()) {
			String metaLine = ruleLines.get(ruleLineIdx).trim();
			if (metaLine.startsWith("@id:")) { //$NON-NLS-1$
				ruleId = metaLine.substring(4).trim();
				if (ruleId.isBlank()) {
					throw new HintParseException("Per-rule id must not be blank", startIndex + ruleLineIdx + 1); //$NON-NLS-1$
				}
				ruleLineIdx++;
			} else if (metaLine.startsWith("@severity:")) { //$NON-NLS-1$
				String severityStr = metaLine.substring(10).trim();
				try {
					ruleSeverity = Severity.valueOf(severityStr.toUpperCase(java.util.Locale.ROOT));
				} catch (IllegalArgumentException e) {
					throw new HintParseException("Invalid per-rule severity: " + severityStr, startIndex + ruleLineIdx + 1); //$NON-NLS-1$
				}
				ruleLineIdx++;
			} else {
				break;
			}
		}
		
		if (ruleLineIdx >= ruleLines.size()) {
			throw new HintParseException("Rule has metadata annotations but no pattern", startIndex + 1); //$NON-NLS-1$
		}
		
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
			
			// Handle => on its own line: read replacement from the next line(s)
			if (altContent.isEmpty() && ruleLineIdx + 1 < ruleLines.size()) {
				ruleLineIdx++;
				altContent = ruleLines.get(ruleLineIdx).trim();
			}
			
			// Accumulate multiline replacement: continuation lines that don't
			// start with '=>' are part of the current replacement text.
			// This enables NetBeans-compatible multiline expressions.
			StringBuilder altContentBuilder = new StringBuilder(altContent);
			while (ruleLineIdx + 1 < ruleLines.size()) {
				String nextLine = ruleLines.get(ruleLineIdx + 1);
				if (nextLine.startsWith("=>")) { //$NON-NLS-1$
					break; // Next alternative — stop accumulating
				}
				ruleLineIdx++;
				altContentBuilder.append('\n').append(nextLine.trim());
			}
			altContent = altContentBuilder.toString();
			
			if (altContent.isEmpty()) {
				throw new HintParseException(
						"Missing replacement after '=>'", //$NON-NLS-1$
						startIndex + ruleLineIdx + 1);
			}
			
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
		Pattern sourcePattern = Pattern.of(sourcePatternText, kind);
		
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
				ruleId, description, sourcePattern, sourceGuard, alternatives, 
				currentImports.isEmpty() ? null : currentImports, ruleSeverity);
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
	 *   <li>Looks like a method declaration (return type + name + parens) → METHOD_DECLARATION</li>
	 *   <li>Contains name + parens → METHOD_CALL</li>
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
		// Method declaration: starts with a return type keyword followed by name and parens
		// e.g., "void $name($params$)", "String getName()", "int $name()"
		if (looksLikeMethodDeclaration(trimmed)) {
			return PatternKind.METHOD_DECLARATION;
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
	 * Checks if a pattern looks like a method declaration.
	 * 
	 * <p>A method declaration pattern has the form:
	 * {@code [modifiers] returnType name(params)} where returnType is a Java
	 * type keyword or identifier, name is an identifier or placeholder, and
	 * params may include placeholders.</p>
	 * 
	 * <p>Examples that match:</p>
	 * <ul>
	 *   <li>{@code void $name($params$)}</li>
	 *   <li>{@code void dispose()}</li>
	 *   <li>{@code String getName()}</li>
	 *   <li>{@code public void setUp()}</li>
	 *   <li>{@code int $name()}</li>
	 * </ul>
	 */
	private static boolean looksLikeMethodDeclaration(String trimmed) {
		// Must contain parens
		if (!trimmed.contains("(") || !trimmed.contains(")")) { //$NON-NLS-1$ //$NON-NLS-2$
			return false;
		}
		// Must not contain '.' (method calls have receiver.method())
		// Method declarations don't have dots in the signature part before '('
		String beforeParens = trimmed.substring(0, trimmed.indexOf('(')).trim();
		if (beforeParens.contains(".")) { //$NON-NLS-1$
			return false;
		}
		// Split into space-separated tokens before '('
		String[] tokens = beforeParens.split("\\s+"); //$NON-NLS-1$
		// Need at least 2 tokens: return type and method name
		// e.g., "void $name" or "public void setUp"
		if (tokens.length < 2) {
			return false;
		}
		// Check if any token is a primitive type or 'void' — strong indicator
		for (String token : tokens) {
			if (RETURN_TYPE_KEYWORDS.contains(token)) {
				return true;
			}
		}
		// Heuristic: if the second-to-last token looks like a type (starts with uppercase
		// or is a placeholder) and the last token looks like a name, treat as declaration
		String possibleType = tokens[tokens.length - 2];
		if (Character.isUpperCase(possibleType.charAt(0)) || possibleType.startsWith("$")) { //$NON-NLS-1$
			return true;
		}
		return false;
	}
	
	/**
	 * Java primitive types and void — used to detect method declaration patterns.
	 */
	private static final Set<String> RETURN_TYPE_KEYWORDS = Set.of(
			"void", "int", "long", "short", "byte", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
			"float", "double", "boolean", "char" //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	);
	
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
