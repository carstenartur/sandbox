/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which accompanies this distribution
 * and is available at https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.core.cleanupapp;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Augments explicitly selected PDE test deployment descriptors at their existing Vintage entry. */
final class PdeSuiteDeployment {
	private static final String VINTAGE= "junit-vintage-engine"; //$NON-NLS-1$
	private static final String LAUNCHER= "junit-platform-launcher"; //$NON-NLS-1$
	private static final List<String> SUITE= List.of("junit-platform-suite-api", "junit-platform-suite-engine"); //$NON-NLS-1$ //$NON-NLS-2$
	private static final String ADDITIONAL= "additional.bundles"; //$NON-NLS-1$
	private static final Pattern ADDITIONAL_KEY= Pattern.compile("(?m)^[\\t ]*additional\\.bundles[\\t ]*[=:][\\t ]*"); //$NON-NLS-1$

	private PdeSuiteDeployment() {
	}

	static String buildProperties(String source) throws IOException {
		Properties before= properties(source);
		String value= before.getProperty(ADDITIONAL, ""); //$NON-NLS-1$
		List<String> bundles= Arrays.stream(value.split(",")).map(String::strip).toList(); //$NON-NLS-1$
		if (!bundles.contains(VINTAGE)) {
			return source;
		}
		List<String> missing= SUITE.stream().filter(bundle -> !bundles.contains(bundle)).toList();
		if (missing.isEmpty()) {
			return source;
		}
		var match= ADDITIONAL_KEY.matcher(source);
		if (!match.find()) {
			throw new IOException("Cannot locate the additional.bundles declaration"); //$NON-NLS-1$
		}
		int offset= match.end();
		if (match.find()) {
			throw new IOException("Repeated additional.bundles declaration"); //$NON-NLS-1$
		}
		String prefix= String.join(",", missing) + ","; //$NON-NLS-1$ //$NON-NLS-2$
		String after= source.substring(0, offset) + prefix + source.substring(offset);
		before.setProperty(ADDITIONAL, prefix + value);
		if (!before.equals(properties(after))) {
			throw new IOException("Cannot preserve the build.properties declaration"); //$NON-NLS-1$
		}
		return after;
	}

	private static Properties properties(String source) throws IOException {
		var properties= new Properties();
		properties.load(new StringReader(source));
		return properties;
	}

	static String xml(String source, boolean feature, String bundle) throws IOException {
		Element root= PlatformSuiteDependencies.parse(source);
		Element parent= deployment(root, feature, bundle);
		String tag= feature ? "plugin" : "unit"; //$NON-NLS-1$ //$NON-NLS-2$
		List<Element> entries= children(parent, tag);
		String version= feature ? "0.0.0" : platformVersion(entries); //$NON-NLS-1$
		List<String> missing= new ArrayList<>();
		for (String id : SUITE) {
			List<Element> existing= entries.stream().filter(e -> id.equals(e.getAttribute("id"))).toList(); //$NON-NLS-1$
			if (existing.isEmpty()) {
				missing.add(id);
			} else if (existing.size() != 1 || existing.getFirst().getAttributes().getLength() != 2
					|| !version.equals(existing.getFirst().getAttribute("version"))) { //$NON-NLS-1$
				throw new IOException("Incompatible or filtered existing Suite dependency: " + id); //$NON-NLS-1$
			}
		}
		if (missing.isEmpty()) {
			return source;
		}
		List<Element> anchors= entries.stream().filter(e -> VINTAGE.equals(e.getAttribute("id"))).toList(); //$NON-NLS-1$
		if (anchors.size() != 1 || anchors.getFirst().getAttributes().getLength() != 2
				|| !"0.0.0".equals(anchors.getFirst().getAttribute("version"))) { //$NON-NLS-1$ //$NON-NLS-2$
			throw new IOException("Expected an unfiltered, unpinned Vintage unit in the selected PDE deployment"); //$NON-NLS-1$
		}
		String end= "</" + parent.getTagName() + ">"; //$NON-NLS-1$ //$NON-NLS-2$
		String newline= source.contains("\r\n") ? "\r\n" : "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		for (int pos= source.indexOf(end); pos >= 0; pos= source.indexOf(end, pos + end.length())) {
			String indent= source.substring(source.lastIndexOf('\n', pos) + 1, pos);
			if (!indent.isBlank()) {
				continue;
			}
			StringBuilder additions= new StringBuilder();
			for (String id : missing) {
				additions.append("  <").append(tag).append(" id=\"").append(id) //$NON-NLS-1$ //$NON-NLS-2$
						.append("\" version=\"").append(version).append("\"/>").append(newline).append(indent); //$NON-NLS-1$ //$NON-NLS-2$
			}
			String candidate= source.substring(0, pos) + additions + source.substring(pos);
			Element updated= deployment(PlatformSuiteDependencies.parse(candidate), feature, bundle);
			List<Element> rechecked= children(updated, tag);
			if (rechecked.size() == entries.size() + missing.size()
					&& missing.stream().allMatch(id -> rechecked.stream().anyMatch(e -> id.equals(e.getAttribute("id"))))) { //$NON-NLS-1$
				return candidate;
			}
		}
		throw new IOException("Cannot insert the new units in the selected deployment section"); //$NON-NLS-1$
	}

	private static String platformVersion(List<Element> units) throws IOException {
		List<Element> launchers= units.stream().filter(e -> LAUNCHER.equals(e.getAttribute("id"))).toList(); //$NON-NLS-1$
		if (launchers.size() == 1 && launchers.getFirst().getAttributes().getLength() == 2) {
			String version= launchers.getFirst().getAttribute("version"); //$NON-NLS-1$
			if (version.matches("(1|6)\\.[0-9]+\\.[0-9]+")) { //$NON-NLS-1$
				return version;
			}
		}
		throw new IOException("The selected target requires an explicit JUnit Platform launcher release version"); //$NON-NLS-1$
	}

	private static Element deployment(Element root, boolean feature, String bundle) throws IOException {
		if (feature && "feature".equals(root.getTagName()) && children(root, "plugin").stream() //$NON-NLS-1$ //$NON-NLS-2$
				.anyMatch(p -> bundle.equals(p.getAttribute("id")))) { //$NON-NLS-1$
			return root;
		}
		if (!feature && "target".equals(root.getTagName())) { //$NON-NLS-1$
			List<Element> locations= children(root, "locations").stream().flatMap(e -> children(e, "location").stream()) //$NON-NLS-1$ //$NON-NLS-2$
					.filter(e -> "InstallableUnit".equals(e.getAttribute("type")) && children(e, "unit").stream() //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
							.anyMatch(u -> VINTAGE.equals(u.getAttribute("id")))).toList(); //$NON-NLS-1$
			if (locations.size() == 1 && "planner".equals(locations.getFirst().getAttribute("includeMode")) //$NON-NLS-1$ //$NON-NLS-2$
					&& children(locations.getFirst(), "repository").size() == 1) { //$NON-NLS-1$
				return locations.getFirst();
			}
		}
		throw new IOException("Selected PDE descriptor does not identify the test bundle or its unique Vintage installation"); //$NON-NLS-1$
	}

	private static List<Element> children(Element parent, String tag) {
		List<Element> result= new ArrayList<>();
		for (Node child= parent.getFirstChild(); child != null; child= child.getNextSibling()) {
			if (child instanceof Element element && element.getNamespaceURI() == null && tag.equals(element.getTagName())) {
				result.add(element);
			}
		}
		return result;
	}
}
