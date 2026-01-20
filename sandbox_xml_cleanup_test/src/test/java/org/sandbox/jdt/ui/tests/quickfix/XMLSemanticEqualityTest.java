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

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils;

/**
 * Tests to verify that XML transformations preserve semantic equality.
 * Uses XMLUnit to compare XML documents ignoring whitespace differences.
 */
public class XMLSemanticEqualityTest {

	@Test
	public void testTransformationPreservesSemanticEquality() throws Exception {
		String originalXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="test" name="Test View" class="com.example.TestView"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify semantic equality - content should be the same, only whitespace differs
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testTransformationPreservesAllElements() throws Exception {
		String originalXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view id="view1" name="View 1" class="View1"/>
				        <view id="view2" name="View 2" class="View2"/>
				    </extension>
				    <extension point="org.eclipse.ui.commands">
				        <command id="cmd1" name="Command 1"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testTransformationPreservesAttributes() throws Exception {
		String originalXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				    <extension point="org.eclipse.ui.views">
				        <view 
				            id="my.view.id"
				            name="My View Name"
				            class="com.example.MyViewClass"
				            icon="icons/view.png"
				            restorable="true"
				            allowMultiple="false"/>
				    </extension>
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testTransformationPreservesTextContent() throws Exception {
		String originalXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<feature id="my.feature" label="My Feature" version="1.0.0">
				    <description>
				        This is a multi-line description
				        with preserved text content.
				    </description>
				    <copyright>
				        Copyright (c) 2024 Example Corp.
				    </copyright>
				</feature>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testTransformationPreservesNamespaces() throws Exception {
		String originalXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<schema xmlns="http://www.w3.org/2001/XMLSchema"
				        xmlns:pde="http://www.eclipse.org/pde"
				        targetNamespace="org.example">
				    <element name="extension">
				        <complexType>
				            <attribute name="point" type="string" use="required"/>
				        </complexType>
				    </element>
				</schema>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xsd");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testComplexExsdSemanticEquality() throws Exception {
		String originalXml = """
				<?xml version='1.0' encoding='UTF-8'?>
				<schema targetNamespace="org.example" xmlns="http://www.w3.org/2001/XMLSchema">
				    <annotation>
				        <appinfo>
				            <meta.schema plugin="org.example" id="sample" name="Sample"/>
				        </appinfo>
				        <documentation>
				            This extension point allows clients to contribute samples.
				        </documentation>
				    </annotation>
				    <element name="extension">
				        <complexType>
				            <sequence minOccurs="1" maxOccurs="unbounded">
				                <element ref="sample"/>
				            </sequence>
				            <attribute name="point" type="string" use="required"/>
				        </complexType>
				    </element>
				    <element name="sample">
				        <complexType>
				            <attribute name="id" type="string" use="required"/>
				            <attribute name="name" type="string" use="required"/>
				            <attribute name="class" type="string" use="required"/>
				        </complexType>
				    </element>
				</schema>
				""";
		
		Path tempFile = Files.createTempFile("test", ".exsd");
		try {
			Files.writeString(tempFile, originalXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			XMLTestUtils.assertXmlSemanticallyEqual(originalXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
	
	@Test
	public void testSizeReductionWithSemanticEquality() throws Exception {
		String verboseXml = """
				<?xml version="1.0" encoding="UTF-8"?>
				<plugin>
				
				
				    <extension
				            point="org.eclipse.ui.views">
				        <view
				                id="test"
				                name="Test"
				                class="Test"
				                />
				    </extension>
				
				
				</plugin>
				""";
		
		Path tempFile = Files.createTempFile("test", ".xml");
		try {
			Files.writeString(tempFile, verboseXml);
			
			String transformed = SchemaTransformationUtils.transform(tempFile, false);
			
			// Verify size reduction
			assertTrue(transformed.length() < verboseXml.length(),
				"Transformed XML should be smaller");
			
			// Verify semantic equality
			XMLTestUtils.assertXmlSemanticallyEqual(verboseXml, transformed);
			
		} finally {
			Files.deleteIfExists(tempFile);
		}
	}
}
