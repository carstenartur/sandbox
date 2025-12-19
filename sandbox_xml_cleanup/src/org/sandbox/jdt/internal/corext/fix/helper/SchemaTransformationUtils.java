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

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Utilities for transforming XML/XSD/EXSD files using XSLT and post-processing.
 */
public class SchemaTransformationUtils {
	
	/**
	 * Transform an XML file with default settings (no indentation).
	 * 
	 * @param schemaPath path to the XML file
	 * @return transformed content
	 * @throws Exception if transformation fails
	 */
	public static String transform(Path schemaPath) throws Exception {
		return transform(schemaPath, false);
	}
	
	/**
	 * Transform an XML file with configurable indentation.
	 * 
	 * @param schemaPath path to the XML file
	 * @param enableIndent whether to enable indentation (default is false for size reduction)
	 * @return transformed content
	 * @throws Exception if transformation fails
	 */
	public static String transform(Path schemaPath, boolean enableIndent) throws Exception {
		// Load the formatter.xsl file from classpath
		try (InputStream xslStream = SchemaTransformationUtils.class.getClassLoader().getResourceAsStream("resources/formatter.xsl")) {
			if (xslStream == null) {
				throw new IllegalArgumentException("Unable to find formatter.xsl in resources.");
			}

			// Initialize transformer with secure settings
			TransformerFactory factory = TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
			
			// Additional security features
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
			factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
			
			Transformer transformer = factory.newTransformer(new StreamSource(xslStream));
			
			// Set indentation based on preference - default is "no" to reduce file size
			transformer.setOutputProperty(OutputKeys.INDENT, enableIndent ? "yes" : "no");

			// Perform transformation
			StreamSource source = new StreamSource(schemaPath.toFile());
			Path tempOutput = Files.createTempFile("formatted-schema", ".xml");
			
			try {
				StreamResult result = new StreamResult(tempOutput.toFile());
				transformer.transform(source, result);

				// Read transformed content
				String transformed = Files.readString(tempOutput, StandardCharsets.UTF_8);
				
				// Post-process: normalize whitespace and convert leading spaces to tabs
				transformed = normalizeWhitespace(transformed);
				
				return transformed;
			} finally {
				// Ensure temp file is always deleted
				Files.deleteIfExists(tempOutput);
			}
		}
	}
	
	/**
	 * Normalize whitespace in the transformed XML:
	 * - Reduce excessive empty lines (max 2 consecutive empty lines)
	 * - Convert leading 4-space indentation to tabs (not inside text nodes)
	 * - Preserve comments and content
	 * - Preserve original line ending style (CRLF vs LF)
	 * 
	 * @param content the XML content to normalize
	 * @return normalized content
	 */
	private static String normalizeWhitespace(String content) {
		// Reduce excessive empty lines - keep max 2 consecutive empty lines,
		// preserving the original line ending style (LF vs CRLF)
		content = content.replaceAll("(\\r?\\n){3,}", "$1$1");
		
		// Convert leading 4 spaces to tabs (only at line start, not in text content)
		// This pattern matches lines that start with spaces (after optional newline)
		Pattern leadingSpaces = Pattern.compile("^( {4})+", Pattern.MULTILINE);
		Matcher matcher = leadingSpaces.matcher(content);
		StringBuilder sb = new StringBuilder();
		
		while (matcher.find()) {
			String spaces = matcher.group();
			int numSpaces = spaces.length();
			int numTabs = numSpaces / 4;
			matcher.appendReplacement(sb, "\t".repeat(numTabs));
		}
		matcher.appendTail(sb);
		
		return sb.toString();
	}
}
