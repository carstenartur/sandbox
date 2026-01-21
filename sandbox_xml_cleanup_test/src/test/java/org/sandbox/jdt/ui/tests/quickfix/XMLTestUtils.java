/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.jdt.ui.tests.quickfix;

import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Utility methods for XML testing using standard Java DOM APIs.
 * Provides semantic XML comparison that ignores whitespace differences.
 * 
 * This implementation uses only standard Java XML APIs (javax.xml.parsers)
 * and does not require external dependencies like XMLUnit.
 */
public class XMLTestUtils {
    
    /**
     * Check if two XML documents are semantically equivalent (ignoring whitespace).
     * 
     * @param expected the expected XML content
     * @param actual the actual XML content
     * @return true if semantically equivalent
     */
    public static boolean isXmlSemanticallyEqual(String expected, String actual) {
        try {
            Document expectedDoc = parseXml(expected, true);
            Document actualDoc = parseXml(actual, true);
            
            normalizeDocument(expectedDoc);
            normalizeDocument(actualDoc);
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if two XML documents are semantically equivalent, preserving comments.
     * 
     * @param expected the expected XML content
     * @param actual the actual XML content
     * @return true if semantically equivalent (including comments)
     */
    public static boolean isXmlSemanticallyEqualWithComments(String expected, String actual) {
        try {
            Document expectedDoc = parseXml(expected, false);
            Document actualDoc = parseXml(actual, false);
            
            normalizeDocument(expectedDoc);
            normalizeDocument(actualDoc);
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Assert that two XML documents are semantically equivalent.
     * Throws AssertionError with details if not equivalent.
     * 
     * @param expected the expected XML content
     * @param actual the actual XML content
     */
    public static void assertXmlSemanticallyEqual(String expected, String actual) {
        if (!isXmlSemanticallyEqual(expected, actual)) {
            throw new AssertionError(
                "XML documents are not semantically equal.\n" +
                "Expected (normalized):\n" + normalizeForDisplay(expected) + "\n\n" +
                "Actual (normalized):\n" + normalizeForDisplay(actual)
            );
        }
    }
    
    /**
     * Parse XML string to Document.
     * 
     * @param xml the XML string
     * @param ignoreComments whether to ignore comments
     * @return parsed Document
     */
    private static Document parseXml(String xml, boolean ignoreComments) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(ignoreComments);
        factory.setNamespaceAware(true);
        factory.setCoalescing(true);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        
        return doc;
    }
    
    /**
     * Normalize document by removing whitespace-only text nodes.
     */
    private static void normalizeDocument(Document doc) {
        doc.normalizeDocument();
        removeWhitespaceNodes(doc.getDocumentElement());
    }
    
    /**
     * Recursively remove whitespace-only text nodes.
     */
    private static void removeWhitespaceNodes(Node node) {
        if (node == null) {
            return;
        }
        
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getTextContent().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child);
            }
        }
    }
    
    /**
     * Normalize XML for display in error messages.
     */
    private static String normalizeForDisplay(String xml) {
        try {
            Document doc = parseXml(xml, true);
            normalizeDocument(doc);
            // Return a simplified representation
            return xml.replaceAll("\\s+", " ").trim();
        } catch (Exception e) {
            return xml;
        }
    }
}
