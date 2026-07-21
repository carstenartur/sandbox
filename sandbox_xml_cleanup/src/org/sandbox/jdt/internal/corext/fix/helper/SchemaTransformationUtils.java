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
 * Utilities for transforming XML/XSD/EXSD files using XSLT and safe
 * formatting-only post-processing.
 */
public class SchemaTransformationUtils {

	private static final Pattern MARKUP_INDENTATION= Pattern.compile("^( {4})+(?=<)", Pattern.MULTILINE); //$NON-NLS-1$
	
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
		try (InputStream xslStream= SchemaTransformationUtils.class.getClassLoader()
				.getResourceAsStream("resources/formatter.xsl")) { //$NON-NLS-1$
			if (xslStream == null) {
				throw new IllegalArgumentException("Unable to find formatter.xsl in resources."); //$NON-NLS-1$
			}

			// Initialize transformer with secure settings
			TransformerFactory factory= TransformerFactory.newInstance();
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); //$NON-NLS-1$
			factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, ""); //$NON-NLS-1$
			
			Transformer transformer= factory.newTransformer(new StreamSource(xslStream));
			transformer.setOutputProperty(OutputKeys.INDENT, enableIndent ? "yes" : "no"); //$NON-NLS-1$ //$NON-NLS-2$

			// Perform transformation
			StreamSource source= new StreamSource(schemaPath.toFile());
			Path tempOutput= Files.createTempFile("formatted-schema", ".xml"); //$NON-NLS-1$ //$NON-NLS-2$
			
			try {
				StreamResult result= new StreamResult(tempOutput.toFile());
				transformer.transform(source, result);

				String transformed= Files.readString(tempOutput, StandardCharsets.UTF_8);
				return convertMarkupIndentationToTabs(transformed);
			} finally {
				Files.deleteIfExists(tempOutput);
			}
		}
	}

	/**
	 * Converts groups of four leading spaces only when they indent serialized XML
	 * markup. Text lines are left unchanged, including intentional blank lines and
	 * leading spaces inside element content.
	 */
	private static String convertMarkupIndentationToTabs(String content) {
		Matcher matcher= MARKUP_INDENTATION.matcher(content);
		StringBuilder result= new StringBuilder(content.length());
		while (matcher.find()) {
			int tabs= matcher.group().length() / 4;
			matcher.appendReplacement(result, "\t".repeat(tabs)); //$NON-NLS-1$
		}
		matcher.appendTail(result);
		return result.toString();
	}
}
