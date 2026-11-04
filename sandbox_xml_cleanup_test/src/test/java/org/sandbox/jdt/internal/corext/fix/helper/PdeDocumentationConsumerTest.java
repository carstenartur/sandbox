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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.SAXParserFactory;

import org.eclipse.pde.internal.core.builders.SchemaTransformer;
import org.eclipse.pde.internal.core.schema.Schema;
import org.eclipse.pde.internal.core.schema.SchemaAnnotationHandler;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;

/** Consumer evidence for the narrow reference-section cleanup contract. */
@SuppressWarnings("restriction")
class PdeDocumentationConsumerTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(strings = { "appInfo", "appinfo" }) //$NON-NLS-1$ //$NON-NLS-2$
	void preservesFullAbbreviatedLazyAndHtmlConsumersUsingDistinctSourceUrls(String appinfo) throws Exception {
		String source= """
				<schema targetNamespace="org.example">
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
				  <annotation><appInfo><meta.section type="implementation"/></appInfo><documentation>  Supplied implementation  </documentation></annotation>
				  <annotation><appInfo><meta.section type="copyright"/></appInfo><documentation>   </documentation></annotation>
				  <annotation><appInfo><meta.section type="examples"/></appInfo><documentation>  &lt;pre&gt;
				    &lt;item name="keep  spacing"/&gt;
				      indentation is literal
				  &lt;/pre&gt;  </documentation></annotation>
				</schema>
				""".replace("appInfo", appinfo); //$NON-NLS-1$ //$NON-NLS-2$
		Path original= Files.writeString(directory.resolve("original.exsd"), source); //$NON-NLS-1$
		List<String> lightweight= annotation(source);
		assertTrue(lightweight.get(1).startsWith("  Raw overview")); //$NON-NLS-1$
		for (boolean indent : new boolean[] { false, true }) {
			String transformed= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8,
					indent, DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
			Path cleaned= Files.writeString(directory.resolve("cleaned-" + indent + ".exsd"), transformed); //$NON-NLS-1$ //$NON-NLS-2$
			assertEquals(lightweight, annotation(transformed));
			assertTrue(transformed.contains("<documentation>Supplied implementation</documentation>")); //$NON-NLS-1$
			assertTrue(transformed.contains("<documentation>  Attribute  text  </documentation>")); //$NON-NLS-1$
			for (boolean abbreviated : new boolean[] { false, true }) {
				Schema before= read(original, abbreviated);
				Schema after= read(cleaned, abbreviated);
				try {
					assertNotEquals(before.getURL(), after.getURL());
					assertEquals(before.getName(), after.getName());
					assertEquals(before.getDescription(), after.getDescription());
					for (String name : new String[] { "item", "blank" }) { //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals(before.findElement(name).getDescription(), after.findElement(name).getDescription());
					}
					String attribute= after.findElement("item").getAttribute("name").getDescription(); //$NON-NLS-1$ //$NON-NLS-2$
					assertEquals(before.findElement("item").getAttribute("name").getDescription(), attribute); //$NON-NLS-1$ //$NON-NLS-2$
					var beforeSections= before.getDocumentSections();
					var afterSections= after.getDocumentSections();
					assertEquals(beforeSections.length, afterSections.length);
					for (int i= 0; i < beforeSections.length; i++) {
						assertEquals(beforeSections[i].getSectionId(), afterSections[i].getSectionId());
						assertEquals(beforeSections[i].getDescription(), afterSections[i].getDescription());
					}
					if (abbreviated) {
						// These real lazy getters read distinct files and preserve raw boundaries.
						assertEquals("  Attribute  text  ", attribute); //$NON-NLS-1$
						assertTrue(after.findElement("item").getDescription().startsWith("  First\tline")); //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals("   ", after.findElement("blank").getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
						// PDE adds five placeholders but does not load their descriptions.
						assertEquals(5, afterSections.length);
						for (var section : afterSections) {
							assertNull(section.getDescription());
						}
					} else {
						assertNotEquals(before.getDescription(), lightweight.get(1));
						assertEquals("Attribute  text", attribute); //$NON-NLS-1$
						assertEquals("", after.findElement("blank").getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
						assertEquals(5, afterSections.length);
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
		new SchemaTransformer().transform(schema, new PrintWriter(output), null, SchemaTransformer.BUILD);
		return output.toString();
	}
}
