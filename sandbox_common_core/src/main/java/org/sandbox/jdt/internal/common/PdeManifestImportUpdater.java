/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Adds one package to the main OSGi {@code Import-Package} header without reformatting existing clauses. */
public final class PdeManifestImportUpdater {

	private static final String IMPORT_PACKAGE_HEADER= "Import-Package:"; //$NON-NLS-1$
	private static final Pattern IMPORT_PACKAGE_PATTERN= Pattern.compile(
			"(?m)^Import-Package:[^\\r\\n]*(?:\\r?\\n [^\\r\\n]*)*"); //$NON-NLS-1$
	private static final Pattern MAIN_SECTION_SEPARATOR= Pattern.compile("\\r?\\n\\r?\\n"); //$NON-NLS-1$
	private static final Pattern PACKAGE_NAME= Pattern.compile(
			"[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*"); //$NON-NLS-1$

	private PdeManifestImportUpdater() {
	}

	public static byte[] addImport(byte[] original, String packageName) {
		return addImport(original, packageName, null);
	}

	public record Exporter(String symbolicName, String version) {
		private static final Pattern BUNDLE_COMPONENT= Pattern.compile("[A-Za-z0-9_-]++"); //$NON-NLS-1$
		private static final Pattern VERSION_NUMBER= Pattern.compile("[0-9]++"); //$NON-NLS-1$

		public Exporter {
			if (!Arrays.stream(symbolicName.split("\\.", -1)).allMatch(BUNDLE_COMPONENT.asMatchPredicate())) //$NON-NLS-1$
				throw new IllegalArgumentException("Invalid bundle symbolic name: " + symbolicName); //$NON-NLS-1$
			String[] parts= version.split("\\.", 4); //$NON-NLS-1$
			if (parts.length < 3 || !VERSION_NUMBER.matcher(parts[0]).matches() || !VERSION_NUMBER.matcher(parts[1]).matches()
					|| !VERSION_NUMBER.matcher(parts[2]).matches() || parts.length == 4 && !BUNDLE_COMPONENT.matcher(parts[3]).matches())
				throw new IllegalArgumentException("Invalid bundle version: " + version); //$NON-NLS-1$
		}
	}

	public static byte[] addImport(byte[] original, String packageName, Exporter exporter) {
		Objects.requireNonNull(original);
		String content= new String(original, StandardCharsets.UTF_8);
		String updated= addImport(content, packageName, exporter);
		return content.equals(updated) ? original : updated.getBytes(StandardCharsets.UTF_8);
	}

	public static String addImport(String manifest, String packageName) {
		return addImport(manifest, packageName, null);
	}

	public static String addImport(String manifest, String packageName, Exporter exporter) {
		Objects.requireNonNull(manifest);
		Objects.requireNonNull(packageName);
		if (!PACKAGE_NAME.matcher(packageName).matches()) {
			throw new IllegalArgumentException("Invalid Java package name: " + packageName); //$NON-NLS-1$
		}

		int mainSectionEnd= mainSectionEnd(manifest);
		Matcher header= IMPORT_PACKAGE_PATTERN.matcher(manifest.substring(0, mainSectionEnd));
		String lineDelimiter= lineDelimiter(manifest);
		String clause= packageName;
		if (exporter != null) {
			String attributes= ";bundle-symbolic-name=" + exporter.symbolicName() + ";bundle-version=\"[" //$NON-NLS-1$ //$NON-NLS-2$
					+ exporter.version() + "," + exporter.version() + "]\""; //$NON-NLS-1$ //$NON-NLS-2$
			// Import clauses are ASCII here. Leave room for the header and continuation prefix.
			clause= (clause + attributes).replaceAll("(.{54})(?=.)", "$1" + lineDelimiter + " "); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}
		if (header.find()) {
			String physicalHeader= header.group();
			String unfolded= physicalHeader.substring(IMPORT_PACKAGE_HEADER.length())
					.replace("\r\n ", "").replace("\n ", ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
			if (containsPackage(unfolded, packageName)) {
				return manifest;
			}
			String separator= physicalHeader.stripTrailing().endsWith(",") //$NON-NLS-1$
					? lineDelimiter + " " : "," + lineDelimiter + " "; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			return manifest.substring(0, header.end()) + separator + clause
					+ manifest.substring(header.end());
		}

		String prefix= mainSectionEnd == 0 || isLineBreakBefore(manifest, mainSectionEnd)
				? "" : lineDelimiter; //$NON-NLS-1$
		String suffix= mainSectionEnd == manifest.length() && isLineBreakBefore(manifest, mainSectionEnd)
				? lineDelimiter : ""; //$NON-NLS-1$
		return manifest.substring(0, mainSectionEnd) + prefix + IMPORT_PACKAGE_HEADER + " " //$NON-NLS-1$
				+ clause + suffix + manifest.substring(mainSectionEnd);
	}

	private static int mainSectionEnd(String manifest) {
		Matcher separator= MAIN_SECTION_SEPARATOR.matcher(manifest);
		return separator.find() ? separator.start() : manifest.length();
	}

	private static boolean isLineBreakBefore(String value, int offset) {
		if (offset <= 0) {
			return false;
		}
		char previous= value.charAt(offset - 1);
		return previous == '\n' || previous == '\r';
	}

	private static String lineDelimiter(String manifest) {
		return manifest.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	private static boolean containsPackage(String headerValue, String packageName) {
		for (String clause : splitOutsideQuotes(headerValue, ',')) {
			for (String segment : splitOutsideQuotes(clause, ';')) {
				String candidate= segment.trim();
				if (candidate.contains(":=") || candidate.indexOf('=') >= 0) { //$NON-NLS-1$
					break;
				}
				if (packageName.equals(candidate)) {
					return true;
				}
			}
		}
		return false;
	}

	private static List<String> splitOutsideQuotes(String value, char delimiter) {
		List<String> result= new ArrayList<>();
		int start= 0;
		boolean quoted= false;
		boolean escaped= false;
		for (int index= 0; index < value.length(); index++) {
			char current= value.charAt(index);
			if (escaped) {
				escaped= false;
				continue;
			}
			if (quoted && current == '\\') {
				escaped= true;
				continue;
			}
			if (current == '"') {
				quoted= !quoted;
				continue;
			}
			if (!quoted && current == delimiter) {
				result.add(value.substring(start, index));
				start= index + 1;
			}
		}
		result.add(value.substring(start));
		return result;
	}
}
