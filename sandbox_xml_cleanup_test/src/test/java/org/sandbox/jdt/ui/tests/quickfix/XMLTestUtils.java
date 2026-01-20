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
package org.sandbox.jdt.ui.tests.quickfix;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import java.io.StringReader;

/**
 * Utility methods for XML testing using standard Java DOM APIs.
 * Provides semantic XML comparison that ignores whitespace differences.
 */
public class XMLTestUtils {
    
    /**
     * Check if two XML documents are semantically equivalent (ignoring whitespace).
     */
    public static boolean isXmlSemanticallyEqual(String expected, String actual) {
        try {
            Document expectedDoc = parseXml(expected);
            Document actualDoc = parseXml(actual);
            
            expectedDoc.normalizeDocument();
            actualDoc.normalizeDocument();
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Parse XML string to Document, normalizing whitespace.
     */
    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(true);
        factory.setNamespaceAware(true);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        
        // Remove whitespace-only text nodes
        removeWhitespaceNodes(doc.getDocumentElement());
        doc.normalizeDocument();
        
        return doc;
    }
    
    /**
     * Recursively remove whitespace-only text nodes.
     */
    private static void removeWhitespaceNodes(Node node) {
        if (node == null) return;
        
        Node child = node.getFirstChild();
        while (child != null) {
            Node next = child.getNextSibling();
            if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getTextContent().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                removeWhitespaceNodes(child);
            }
            child = next;
        }
    }
    
    /**
     * Assert that two XML documents are semantically equivalent.
     */
    public static void assertXmlSemanticallyEqual(String expected, String actual) {
        if (!isXmlSemanticallyEqual(expected, actual)) {
            throw new AssertionError(
                "XML documents are not semantically equal.\n" +
                "Expected:\n" + expected + "\n" +
                "Actual:\n" + actual
            );
        }
    }
    
    /**
     * Check if two XML documents are semantically equivalent, preserving comments.
     */
    public static boolean isXmlSemanticallyEqualWithComments(String expected, String actual) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringElementContentWhitespace(true);
            factory.setIgnoringComments(false); // Keep comments
            factory.setNamespaceAware(true);
            
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document expectedDoc = builder.parse(new InputSource(new StringReader(expected)));
            Document actualDoc = builder.parse(new InputSource(new StringReader(actual)));
            
            removeWhitespaceNodes(expectedDoc.getDocumentElement());
            removeWhitespaceNodes(actualDoc.getDocumentElement());
            
            expectedDoc.normalizeDocument();
            actualDoc.normalizeDocument();
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (Exception e) {
            return false;
        }
    }
}
