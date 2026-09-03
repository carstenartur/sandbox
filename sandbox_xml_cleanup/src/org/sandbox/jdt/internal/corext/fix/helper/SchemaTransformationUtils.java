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
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

/**
 * Utilities for transforming XML/XSD/EXSD files using XSLT and safe
 * formatting-only post-processing.
 */
public class SchemaTransformationUtils {

	private static final Pattern MARKUP_INDENTATION= Pattern.compile("^( {4})+(?= *<)", Pattern.MULTILINE); //$NON-NLS-1$
	private static final Pattern PROTECTED_XML_REGION= Pattern.compile(
			"<!--.*?(?:-->|\\z)|<!\\[CDATA\\[.*?(?:\\]\\]>|\\z)|<\\?.*?(?:\\?>|\\z)", //$NON-NLS-1$
			Pattern.DOTALL);
	private static final String CDATA_START= "<![CDATA["; //$NON-NLS-1$

	private SchemaTransformationUtils() {
	}

	/** Location of the first structural indentation that can be normalized. */
	record IndentationFinding(int lineNumber, int offset, int length) {
	}

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
	 * Transform an XML file with configurable indentation. This compatibility
	 * overload assumes UTF-8; workspace callers use the content/charset overload.
	 *
	 * @param schemaPath path to the XML file
	 * @param enableIndent whether to enable indentation
	 * @return transformed content
	 * @throws Exception if transformation fails
	 */
	public static String transform(Path schemaPath, boolean enableIndent) throws Exception {
		return transform(Files.readString(schemaPath, StandardCharsets.UTF_8),
				StandardCharsets.UTF_8, enableIndent);
	}

	/**
	 * Transforms XML content without requiring a physical workspace location.
	 *
	 * @param sourceContent decoded XML source
	 * @param outputCharset charset named in the serialized XML declaration
	 * @param enableIndent whether to enable indentation
	 * @return transformed content
	 * @throws Exception if transformation fails
	 */
	public static String transform(String sourceContent, Charset outputCharset,
			boolean enableIndent) throws Exception {
		try (InputStream xslStream= SchemaTransformationUtils.class.getClassLoader()
				.getResourceAsStream("resources/formatter.xsl")) { //$NON-NLS-1$
			if (xslStream == null) {
				throw new IllegalArgumentException("Unable to find formatter.xsl in resources."); //$NON-NLS-1$
			}

			TransformerFactory factory= TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); //$NON-NLS-1$

			Transformer transformer= factory.newTransformer(new StreamSource(xslStream));
			transformer.setOutputProperty(OutputKeys.INDENT, enableIndent ? "yes" : "no"); //$NON-NLS-1$ //$NON-NLS-2$
			transformer.setOutputProperty(OutputKeys.ENCODING, outputCharset.name());
			StringWriter output= new StringWriter(Math.max(1024, sourceContent.length()));
			transformer.transform(new StreamSource(new StringReader(sourceContent)),
					new StreamResult(output));
			return convertMarkupIndentationToTabs(output.toString());
		}
	}

	/**
	 * Converts complete groups of four leading spaces only when the spaces form a
	 * whitespace-only segment between XML markup. Indentation that follows
	 * meaningful text is retained because it belongs to that text node, even when
	 * the next line starts with a closing tag.
	 */
	static String convertMarkupIndentationToTabs(String content) {
		Matcher matcher= MARKUP_INDENTATION.matcher(content);
		ProtectedXmlRegions protectedRegions= new ProtectedXmlRegions(content);
		StringBuilder result= new StringBuilder(content.length());
		int copiedThrough= 0;
		while (matcher.find()) {
			result.append(content, copiedThrough, matcher.start());
			if (isConvertibleMarkupIndentation(content, matcher.start(), protectedRegions)) {
				result.append("\t".repeat(matcher.group().length() / 4)); //$NON-NLS-1$
			} else {
				result.append(matcher.group());
			}
			copiedThrough= matcher.end();
		}
		return result.append(content, copiedThrough, content.length()).toString();
	}

	static IndentationFinding firstConvertibleMarkupIndentation(String content) {
		Matcher matcher= MARKUP_INDENTATION.matcher(content);
		ProtectedXmlRegions protectedRegions= new ProtectedXmlRegions(content);
		while (matcher.find()) {
			if (!isConvertibleMarkupIndentation(content, matcher.start(), protectedRegions)) {
				continue;
			}
			int lineNumber= 1;
			for (int offset= 0; offset < matcher.start(); offset++) {
				if (content.charAt(offset) == '\n') {
					lineNumber++;
				}
			}
			return new IndentationFinding(lineNumber, matcher.start(), matcher.end() - matcher.start());
		}
		return null;
	}

	private static boolean isConvertibleMarkupIndentation(String content, int indentationOffset,
			ProtectedXmlRegions protectedRegions) {
		return !protectedRegions.contains(indentationOffset)
				&& isFormattingOnlyIndentation(content, indentationOffset, protectedRegions);
	}

	private record ProtectedXmlRegion(int start, int end, boolean textContent) {
	}

	private static final class ProtectedXmlRegions {

		private final List<ProtectedXmlRegion> regions= new ArrayList<>();

		ProtectedXmlRegions(String content) {
			Matcher matcher= PROTECTED_XML_REGION.matcher(content);
			while (matcher.find()) {
				regions.add(new ProtectedXmlRegion(matcher.start(), matcher.end(),
						content.startsWith(CDATA_START, matcher.start())));
			}
		}

		boolean contains(int offset) {
			return containing(offset) != null;
		}

		ProtectedXmlRegion containing(int offset) {
			int low= 0;
			int high= regions.size() - 1;
			while (low <= high) {
				int middle= (low + high) >>> 1;
				ProtectedXmlRegion region= regions.get(middle);
				if (offset < region.start()) {
					high= middle - 1;
				} else if (offset >= region.end()) {
					low= middle + 1;
				} else {
					return region;
				}
			}
			return null;
		}
	}

	private static boolean isFormattingOnlyIndentation(String content, int indentationOffset,
			ProtectedXmlRegions protectedRegions) {
		int offset= indentationOffset - 1;
		while (offset >= 0) {
			ProtectedXmlRegion protectedRegion= protectedRegions.containing(offset);
			if (protectedRegion != null) {
				if (protectedRegion.textContent()) {
					return false;
				}
				offset= protectedRegion.start() - 1;
				continue;
			}
			char character= content.charAt(offset);
			if (Character.isWhitespace(character)) {
				offset--;
				continue;
			}
			return character == '>' && isMarkupEnd(content, offset, protectedRegions);
		}
		return true;
	}

	private static boolean isMarkupEnd(String content, int markupEnd,
			ProtectedXmlRegions protectedRegions) {
		char quote= 0;
		for (int offset= markupEnd - 1; offset >= 0; offset--) {
			ProtectedXmlRegion protectedRegion= protectedRegions.containing(offset);
			if (protectedRegion != null) {
				offset= protectedRegion.start();
				continue;
			}
			char character= content.charAt(offset);
			if (quote != 0) {
				if (character == quote) {
					quote= 0;
				}
				continue;
			}
			if (character == '\'' || character == '"') {
				quote= character;
			} else if (character == '<') {
				return true;
			} else if (character == '>') {
				return false;
			}
		}
		return false;
	}
}
