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

import java.io.IOException;
import java.io.StringReader;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Utility methods for XML testing using standard Java DOM APIs.
 * Provides semantic XML comparison that ignores whitespace differences.
 */
public class XMLTestUtils {
    
    /**
     * Utility class constructor to prevent instantiation.
     */
    private XMLTestUtils() {
        // utility class, do not instantiate
    }
    
    /**
     * Check if two XML documents are semantically equivalent (ignoring whitespace and comments).
     * 
     * @param expected the expected XML string
     * @param actual the actual XML string
     * @return true if the XML documents are semantically equal, false otherwise
     */
    public static boolean isXmlSemanticallyEqual(String expected, String actual) {
        try {
            Document expectedDoc = parseXml(expected, true);
            Document actualDoc = parseXml(actual, true);
            
            expectedDoc.normalizeDocument();
            actualDoc.normalizeDocument();
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return false;
        }
    }
    
    /**
     * Parse XML string to Document, normalizing whitespace.
     * 
     * @param xml the XML string to parse
     * @param ignoreComments whether to ignore comments during parsing
     * @return the parsed Document
     * @throws ParserConfigurationException if a DocumentBuilder cannot be created
     * @throws SAXException if the XML is malformed
     * @throws IOException if an I/O error occurs
     */
    private static Document parseXml(String xml, boolean ignoreComments) 
            throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory factory = createSecureDocumentBuilderFactory(ignoreComments);
        
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));
        
        // Remove whitespace-only text nodes
        removeWhitespaceNodes(doc.getDocumentElement());
        doc.normalizeDocument();
        
        return doc;
    }
    
    /**
     * Create a secure DocumentBuilderFactory with XXE protections.
     * 
     * @param ignoreComments whether to ignore comments during parsing
     * @return a configured DocumentBuilderFactory
     * @throws ParserConfigurationException if security features cannot be set
     */
    private static DocumentBuilderFactory createSecureDocumentBuilderFactory(boolean ignoreComments) 
            throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setIgnoringElementContentWhitespace(true);
        factory.setIgnoringComments(ignoreComments);
        factory.setNamespaceAware(true);
        
        // Security features to prevent XXE attacks
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        
        return factory;
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
     * Unlike {@link #isXmlSemanticallyEqual(String, String)}, this method includes
     * comments in the comparison.
     * 
     * @param expected the expected XML string
     * @param actual the actual XML string
     * @return true if the XML documents are semantically equal including comments, false otherwise
     */
    public static boolean isXmlSemanticallyEqualWithComments(String expected, String actual) {
        try {
            Document expectedDoc = parseXml(expected, false);
            Document actualDoc = parseXml(actual, false);
            
            expectedDoc.normalizeDocument();
            actualDoc.normalizeDocument();
            
            return expectedDoc.isEqualNode(actualDoc);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            return false;
        }
    }
}
