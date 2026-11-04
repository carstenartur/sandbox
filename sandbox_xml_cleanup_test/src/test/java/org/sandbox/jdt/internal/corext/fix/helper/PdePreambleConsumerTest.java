/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.SAXParserFactory;

import org.eclipse.pde.internal.core.builders.SchemaTransformer;
import org.eclipse.pde.internal.core.schema.Schema;
import org.eclipse.pde.internal.core.schema.SchemaAnnotationHandler;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The generated comment has no role in PDE's actual schema consumers. */
@SuppressWarnings("restriction")
class PdePreambleConsumerTest {

	@TempDir
	Path directory;

	static Stream<Arguments> pdeConventions() {
		return Stream.of(
				Arguments.of("", "appInfo"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of("", "appinfo"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(" xmlns=\"http://www.w3.org/2001/XMLSchema\"", "appInfo"), //$NON-NLS-1$ //$NON-NLS-2$
				Arguments.of(" xmlns=\"http://www.w3.org/2001/XMLSchema\"", "appinfo")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@ParameterizedTest
	@MethodSource("pdeConventions") //$NON-NLS-1$
	void preservesFullRawLazyLightweightAndHtmlResultsFromDistinctFiles(String namespace, String appinfo)
			throws Exception {
		String source= """
				<?xml version="1.0" encoding="UTF-8"?>
				<!-- Schema file written by PDE -->
				<?authoring retain?>
				<!-- Human maintenance note -->
				<schema NAMESPACE targetNamespace="org.example">
				  <annotation><appInfo><meta.schema plugin="org.example" id="items" name="Items"/></appInfo>
				    <documentation>  Raw overview
				      remains raw.  </documentation>
				  </annotation>
				  <element name="item"><annotation><documentation>  First&#9;line
				    second  line  </documentation></annotation>
				    <complexType><attribute name="name"><annotation><documentation>  Attribute  text  </documentation></annotation></attribute></complexType>
				  </element>
				  <element name="blank"><annotation><documentation>   </documentation></annotation></element>
				  <annotation><appInfo><meta.section type="since"/></appInfo><documentation>  3.4  </documentation></annotation>
				  <annotation><appInfo><meta.section type="apiinfo"/></appInfo><documentation>  API&#9;text
				    second  line  </documentation></annotation>
				  <annotation><appInfo><meta.section type="examples"/></appInfo><documentation>  &lt;pre&gt;
				    &lt;item name="keep  spacing"/&gt;
				      indentation is literal
				  &lt;/pre&gt;  </documentation></annotation>
				</schema>
				""".replace(" NAMESPACE", namespace).replace("appInfo", appinfo); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		Path original= Files.writeString(directory.resolve("original.exsd"), source, StandardCharsets.UTF_8); //$NON-NLS-1$
		List<String> lightweight= annotation(source);
		assertTrue(lightweight.get(1).startsWith("  Raw overview")); //$NON-NLS-1$
		for (boolean indent : new boolean[] { false, true }) {
			String transformed= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
			Path cleaned= Files.writeString(directory.resolve("cleaned-" + indent + ".exsd"), transformed, StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
			assertFalse(transformed.contains("<!-- Schema file written by PDE -->")); //$NON-NLS-1$
			assertTrue(transformed.contains("<!-- Human maintenance note -->")); //$NON-NLS-1$
			assertTrue(transformed.contains("<?authoring retain?>")); //$NON-NLS-1$
			assertEquals(lightweight, annotation(transformed));
			assertEquals(transformed, SchemaTransformationUtils.transform(transformed, StandardCharsets.UTF_8, indent));
			for (boolean abbreviated : new boolean[] { false, true }) {
				Schema before= read(original, abbreviated);
				Schema after= read(cleaned, abbreviated);
				try {
					assertNotEquals(before.getURL(), after.getURL());
					assertEquals("Items", after.getName()); //$NON-NLS-1$
					assertEquals(before.getName(), after.getName());
					assertEquals(before.getPluginId(), after.getPluginId());
					assertEquals(before.getPointId(), after.getPointId());
					assertEquals(before.getDescription(), after.getDescription());
					for (String name : new String[] { "item", "blank" }) { //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals(before.findElement(name).getDescription(), after.findElement(name).getDescription());
					}
					String attribute= after.findElement("item").getAttribute("name").getDescription(); //$NON-NLS-1$ //$NON-NLS-2$
					assertEquals(before.findElement("item").getAttribute("name").getDescription(), attribute); //$NON-NLS-1$ //$NON-NLS-2$
					var oldSections= before.getDocumentSections();
					var newSections= after.getDocumentSections();
					assertEquals(oldSections.length, newSections.length);
					for (int i= 0; i < oldSections.length; i++) {
						assertEquals(oldSections[i].getSectionId(), newSections[i].getSectionId());
						assertEquals(oldSections[i].getName(), newSections[i].getName());
						assertEquals(oldSections[i].getDescription(), newSections[i].getDescription());
					}
					if (abbreviated) {
						// These getters reparse distinct files and retain raw XML text boundaries.
						assertEquals("  Attribute  text  ", attribute); //$NON-NLS-1$
						assertTrue(after.findElement("item").getDescription().startsWith("  First\tline")); //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals("   ", after.findElement("blank").getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
					} else {
						assertEquals("Attribute  text", attribute); //$NON-NLS-1$
						assertEquals("", after.findElement("blank").getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
						assertTrue(html(before).contains("keep  spacing")); //$NON-NLS-1$
						assertEquals(html(before), html(after));
					}
				} finally {
					before.dispose();
					after.dispose();
				}
			}
		}
	}

	private static List<String> annotation(String xml) throws Exception {
		SchemaAnnotationHandler handler= new SchemaAnnotationHandler();
		SAXParserFactory factory= SAXParserFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); //$NON-NLS-1$
		factory.newSAXParser().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), handler);
		return Arrays.asList(handler.getName(), handler.getDescription());
	}

	private static Schema read(Path file, boolean abbreviated) throws Exception {
		Schema schema= new Schema(null, file.toUri().toURL(), abbreviated);
		try (var input= Files.newInputStream(file)) {
			schema.load(input);
		}
		assertTrue(schema.isLoaded());
		assertTrue(schema.isValid());
		return schema;
	}

	private static String html(Schema schema) {
		StringWriter output= new StringWriter();
		new SchemaTransformer().transform(schema, new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), Charset.defaultCharset())), null, SchemaTransformer.BUILD);
		return output.toString();
	}
}
