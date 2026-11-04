/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.eclipse.pde.internal.core.builders.SchemaTransformer;
import org.eclipse.pde.internal.core.ischema.IMetaAttribute;
import org.eclipse.pde.internal.core.schema.Schema;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercise the PDE readers and HTML generator that consume the formatted metadata. */
@SuppressWarnings("restriction")
class PdeMetadataConsumerTest {

	@ParameterizedTest
	@ValueSource(strings = { "appInfo", "appinfo" }) //$NON-NLS-1$ //$NON-NLS-2$
	void preservesPdeModelsAndGeneratedDocumentation(String appinfo) throws Exception {
		String source= """
				<schema targetNamespace="org.example">
				    <annotation>
				        <appinfo>
				            <meta.schema plugin="org.example" id="items" name="Example Items">  </meta.schema>
				        </appinfo>
				        <documentation>Overview with &lt;code&gt;literal  spaces&lt;/code&gt;.</documentation>
				    </annotation>
				    <element name="item">
				        <annotation>
				            <appinfo>
				                <meta.element labelAttribute="name" icon="icon" deprecated="true">  </meta.element>
				            </appinfo>
				            <documentation>   </documentation>
				        </annotation>
				        <complexType>
				            <attribute name="class" type="string">
				                <annotation>
				                    <appinfo>
				                        <meta.attribute kind="java" basedOn=":org.example.Item" translatable="true">  </meta.attribute>
				                    </appinfo>
				                    <documentation>Class description.</documentation>
				                </annotation>
				            </attribute>
				        </complexType>
				    </element>
				    <annotation>
				        <appinfo>
				            <meta.section type="examples">  </meta.section>
				        </appinfo>
				        <documentation>&lt;pre&gt;
				  &lt;item class="Example"/&gt;
				    keep  spaces
				&lt;/pre&gt;</documentation>
				    </annotation>
				</schema>
				""".replace("appinfo", appinfo); //$NON-NLS-1$ //$NON-NLS-2$
		for (boolean indent : new boolean[] { false, true }) {
			String transformed= SchemaTransformationUtils.transform(source, StandardCharsets.UTF_8, indent);
			for (boolean abbreviated : new boolean[] { false, true }) {
				Schema before= read(source, abbreviated);
				Schema after= read(transformed, abbreviated);
				try {
					assertMetadata(before);
					assertMetadata(after);
					assertEquals(before.getDescription(), after.getDescription());
					assertEquals(before.findElement("item").getDescription(), after.findElement("item").getDescription()); //$NON-NLS-1$ //$NON-NLS-2$
					if (!abbreviated) {
						String originalHtml= html(before);
						assertTrue(originalHtml.contains("keep  spaces"), originalHtml); //$NON-NLS-1$
						assertEquals(originalHtml, html(after));
					}
				} finally {
					before.dispose();
					after.dispose();
				}
			}
		}
	}

	private static Schema read(String xml, boolean abbreviated) {
		Schema schema= new Schema(null, (URL) null, abbreviated);
		schema.load(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
		assertTrue(schema.isLoaded(), "PDE did not finish loading the schema"); //$NON-NLS-1$
		assertTrue(schema.isValid(), "PDE did not recognize the metadata"); //$NON-NLS-1$
		return schema;
	}

	private static void assertMetadata(Schema schema) {
		assertEquals("Example Items", schema.getName()); //$NON-NLS-1$
		assertEquals("org.example", schema.getPluginId()); //$NON-NLS-1$
		assertEquals("items", schema.getPointId()); //$NON-NLS-1$
		var element= schema.findElement("item"); //$NON-NLS-1$
		assertEquals("name", element.getLabelProperty()); //$NON-NLS-1$
		assertEquals("icon", element.getIconProperty()); //$NON-NLS-1$
		assertTrue(element.isDeprecated());
		var attribute= element.getAttribute("class"); //$NON-NLS-1$
		assertEquals(IMetaAttribute.JAVA, attribute.getKind());
		assertEquals(":org.example.Item", attribute.getBasedOn()); //$NON-NLS-1$
		// PDE translates STRING attributes only, regardless of the stored flag.
		assertFalse(attribute.isTranslatable());
	}

	private static String html(Schema schema) {
		StringWriter output= new StringWriter();
		new SchemaTransformer().transform(schema, new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), Charset.defaultCharset())), null, SchemaTransformer.BUILD);
		return output.toString();
	}
}
