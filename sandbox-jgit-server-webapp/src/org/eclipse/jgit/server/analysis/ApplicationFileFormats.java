/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jgit.server.analysis;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/** Sandbox's non-Java search conventions, independent of persistence and Java parsing. */
public final class ApplicationFileFormats {
  private static final int MAX_SNIPPET = 65535;
  private static final Pattern FQN = Pattern.compile("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*\\.[A-Z]\\w*");
  private static final Pattern GROUP = Pattern.compile("<groupId>\\s*([^<]+?)\\s*</groupId>");
  private static final Pattern ARTIFACT = Pattern.compile("<artifactId>\\s*([^<]+?)\\s*</artifactId>");
  private static final Pattern MODULE = Pattern.compile("<module>\\s*([^<]+?)\\s*</module>");
  private static final Pattern EXTENSION_POINT = Pattern.compile("point=\"([^\"]+)\"");
  private static final Pattern CLASS_ATTRIBUTE = Pattern.compile("class=\"([^\"]+)\"");
  private static final Pattern NAMESPACE = Pattern.compile("xmlns[^\"]*=\"([^\"]+)\"");
  private static final Pattern ELEMENT = Pattern.compile("<([a-zA-Z][\\w.:-]*)\\s");

  /** Nullable facets retain the legacy distinction between absent and empty metadata. */
  public record Facets(String fileType, String fullyQualifiedNames, String declaredTypes,
      String declaredFields, String sourceSnippet) {}

  private ApplicationFileFormats() {}

  /** Preserve text when historical format metadata cannot be decoded. */
  static Facets plainText(String fileType, String source) {
    return new Facets(fileType, null, null, null, truncate(source));
  }

  public static Facets extract(String filePath, String source) {
    Objects.requireNonNull(filePath); Objects.requireNonNull(source);
    String filename = filePath.substring(filePath.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
    String snippet = truncate(source);
    // Filename conventions take precedence over extension conventions.
    if (filename.equals("pom.xml")) {
      var names = uniqueMatches(GROUP, source);
      var artifacts = uniqueMatches(ARTIFACT, source);
      names.addAll(artifacts);
      var modules = uniqueMatches(MODULE, source);
      return new Facets("pom", join(names), join(artifacts), nullableJoin(modules), snippet);
    }
    if (filename.equals("plugin.xml") || filename.equals("fragment.xml")) {
      return new Facets("plugin-xml", join(uniqueMatches(EXTENSION_POINT, source)),
          join(uniqueMatches(CLASS_ATTRIBUTE, source)), null, snippet);
    }
    int extensionStart = filename.lastIndexOf('.');
    String extension = extensionStart < 0 ? "" : filename.substring(extensionStart + 1);
    return switch (extension) {
      case "java" -> throw new IllegalArgumentException("Java files require released Java Analysis");
      case "mf" -> manifest(source, snippet);
      case "properties" -> properties(source, snippet);
      case "xml", "xsd", "exsd", "xsl" -> new Facets("xml", join(uniqueMatches(NAMESPACE, source)),
          join(uniqueMatches(ELEMENT, source)), null, snippet);
      default -> {
        // Retain the historical fallback tag calculation, including paths with dotted directories.
        int dot = filePath.lastIndexOf('.');
        String type = dot >= 0 ? filePath.substring(dot + 1).toLowerCase(Locale.ROOT) : "text";
        String names = nullableJoin(matches(FQN, source, 0));
        yield new Facets(type, names == null ? null : truncate(names), null, null, snippet);
      }
    };
  }

  private static Facets manifest(String source, String snippet) {
    try {
      var attributes = new Manifest(new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)))
          .getMainAttributes();
      var names = new LinkedHashSet<String>();
      var types = new LinkedHashSet<String>();
      String symbolicName = attributes.getValue("Bundle-SymbolicName");
      if (symbolicName != null) {
        int separator = symbolicName.indexOf(';');
        names.add(separator > 0 ? symbolicName.substring(0, separator).trim() : symbolicName.trim());
      }
      packages(attributes, "Export-Package", names);
      packages(attributes, "Import-Package", names);
      packages(attributes, "Require-Bundle", types);
      String bundleName = attributes.getValue("Bundle-Name");
      if (bundleName != null) types.add(bundleName.trim());
      return new Facets("manifest", join(names), join(types), null, snippet);
    } catch (IOException malformed) {
      // As before, the original manifest remains searchable even when headers cannot be parsed.
      return new Facets("manifest", null, null, null, snippet);
    }
  }

  private static void packages(Attributes attributes, String header, Set<String> target) {
    String value = attributes.getValue(header);
    if (value != null) for (String part : value.split(",")) target.add(withoutDirectives(part));
  }

  private static String withoutDirectives(String text) {
    String trimmed = text.trim();
    int separator = trimmed.indexOf(';');
    return separator > 0 ? trimmed.substring(0, separator).trim() : trimmed;
  }

  private static Facets properties(String source, String snippet) {
    var properties = new Properties();
    try { properties.load(new StringReader(source)); }
    catch (IOException malformed) { return new Facets("properties", null, null, null, snippet); }
    var keys = new ArrayList<String>();
    var names = new ArrayList<String>();
    var enumeration = properties.propertyNames();
    while (enumeration.hasMoreElements()) {
      String key = (String) enumeration.nextElement();
      keys.add(key);
      names.addAll(matches(FQN, properties.getProperty(key), 0));
    }
    String joinedKeys = join(keys);
    return new Facets("properties", nullableJoin(names), null, joinedKeys.isEmpty() ? null : joinedKeys, snippet);
  }

  private static LinkedHashSet<String> uniqueMatches(Pattern pattern, String text) {
    return new LinkedHashSet<>(matches(pattern, text, 1));
  }

  private static List<String> matches(Pattern pattern, String text, int group) {
    if (pattern == FQN && text.indexOf('.') < 0) return List.of();
    var result = new ArrayList<String>();
    var matcher = pattern.matcher(text);
    while (matcher.find()) result.add(matcher.group(group));
    return result;
  }

  private static String join(Iterable<String> values) { return String.join("\n", values); }
  private static String nullableJoin(java.util.Collection<String> values) {
    return values.isEmpty() ? null : join(values);
  }
  private static String truncate(String text) {
    if (text.length() <= MAX_SNIPPET) return text;
    int end = MAX_SNIPPET;
    if (Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end))) end--;
    return text.substring(0, end);
  }
}
