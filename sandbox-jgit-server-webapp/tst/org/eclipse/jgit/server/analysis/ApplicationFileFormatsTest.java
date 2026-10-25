/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import static org.junit.Assert.*;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class ApplicationFileFormatsTest {
  @Test public void pomNamesRemainSeparateAndModulesAreFieldFacets() {
    var result = ApplicationFileFormats.extract("module/POM.XML", """
        <project><groupId> org.example </groupId><artifactId>app</artifactId>
        <dependency><groupId>org.example</groupId><artifactId>lib</artifactId></dependency>
        <module> core </module><module>web</module><module>core</module></project>
        """);
    assertEquals("pom", result.fileType());
    assertEquals("org.example\napp\nlib", result.fullyQualifiedNames());
    assertEquals("app\nlib", result.declaredTypes());
    assertEquals("core\nweb", result.declaredFields());
  }

  @Test public void manifestsKeepBundleAndPackageFacetsIncludingFoldedHeaders() {
    var result = ApplicationFileFormats.extract("META-INF/MANIFEST.MF", """
        Manifest-Version: 1.0
        Bundle-SymbolicName: org.example.app;singleton:=true
        Export-Package: org.example.api;version="1.0",
         org.example.extra
        Import-Package: java.util;resolution:=optional,org.example.api
        Require-Bundle: org.eclipse.core.runtime;bundle-version="1.0"
        Bundle-Name: Example App

        """);
    assertEquals("manifest", result.fileType());
    assertEquals("org.example.app\norg.example.api\norg.example.extra\njava.util", result.fullyQualifiedNames());
    assertEquals("org.eclipse.core.runtime\nExample App", result.declaredTypes());
    assertNull(result.declaredFields());
  }

  @Test public void pluginAndFragmentNamesTakePrecedenceOverXmlExtension() {
    for (String path : List.of("plugins/PLUGIN.XML", "fragment.xml")) {
      var result = ApplicationFileFormats.extract(path,
          "<extension point=\"org.eclipse.ui.views\"><view class=\"example.View\"/>"
          + "<view class=\"example.View\"/></extension>");
      assertEquals("plugin-xml", result.fileType());
      assertEquals("org.eclipse.ui.views", result.fullyQualifiedNames());
      assertEquals("example.View", result.declaredTypes());
    }
  }

  @Test public void xmlConventionsPreserveNamespacesAndWhitespaceDelimitedOpeningTags() {
    for (String suffix : List.of("xml", "XSD", "exsd", "xsl")) {
      var result = ApplicationFileFormats.extract("schema." + suffix,
          "<root xmlns=\"urn:root\" xmlns:x=\"urn:x\"><x:item value=\"1\"/><empty/></root>");
      assertEquals("xml", result.fileType());
      assertEquals("urn:root\nurn:x", result.fullyQualifiedNames());
      assertEquals("root\nx:item", result.declaredTypes());
    }
  }

  @Test public void propertiesDecodeEscapesAndContinuationsBeforeExtractingValues() {
    var result = ApplicationFileFormats.extract("messages.PROPERTIES",
        "handler=org.example.\\u0048andler\ncontinued=org.example.\\\n  Handler\nplain=hello\n");
    assertEquals("properties", result.fileType());
    assertEquals(Set.of("handler", "continued", "plain"), Set.of(result.declaredFields().split("\n")));
    assertEquals("org.example.Handler\norg.example.Handler", result.fullyQualifiedNames());
    assertNull(result.declaredTypes());
  }

  @Test public void textKeepsDuplicateNamesAndOriginalSourceAndHandlesMissingExtensions() {
    String source = "Use java.util.List twice: java.util.List";
    var result = ApplicationFileFormats.extract("docs/README", source);
    assertEquals("text", result.fileType());
    assertEquals("java.util.List\njava.util.List", result.fullyQualifiedNames());
    assertEquals(source, result.sourceSnippet());
    assertEquals("md", ApplicationFileFormats.extract("notes.MD", source).fileType());
    assertNull(ApplicationFileFormats.extract("empty.txt", "").fullyQualifiedNames());
    for (String name : List.of("java", "properties", "mf", "xml")) {
      assertEquals("text", ApplicationFileFormats.extract(name, source).fileType());
    }
  }

  @Test public void invalidManifestRetainsSourceAndPropertiesErrorsRemainVisible() {
    var manifest = ApplicationFileFormats.extract("broken.mf", "not a manifest\n");
    assertEquals("manifest", manifest.fileType());
    assertEquals("not a manifest\n", manifest.sourceSnippet());
    assertNull(manifest.fullyQualifiedNames());
    assertThrows(IllegalArgumentException.class,
        () -> ApplicationFileFormats.extract("broken.properties", "value=\\uXYZW"));
  }

  @Test public void emptyPropertyKeysAndMalformedSymbolicNamesPreserveAbsentVersusEmptyFacets() {
    var properties = ApplicationFileFormats.extract("empty-key.properties", "=org.example.Handler\n");
    assertNull(properties.declaredFields());
    assertEquals("org.example.Handler", properties.fullyQualifiedNames());
    var manifest = ApplicationFileFormats.extract("MANIFEST.MF",
        "Manifest-Version: 1.0\nBundle-SymbolicName:  ;singleton:=true\n\n");
    assertEquals("", manifest.fullyQualifiedNames());
  }

  @Test public void sourceLimitDoesNotSplitASurrogatePairAndJavaRequiresReleasedAnalysis() {
    var result = ApplicationFileFormats.extract("long.txt", "a".repeat(65534) + "\uD83D\uDE00tail");
    assertEquals(65534, result.sourceSnippet().length());
    assertThrows(IllegalArgumentException.class,
        () -> ApplicationFileFormats.extract("Example.JAVA", "class Example {}"));
  }
}
