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
package org.eclipse.jgit.storage.hibernate.search.strategies;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jgit.storage.hibernate.search.BlobIndexData;
import org.eclipse.jgit.storage.hibernate.search.FileTypeStrategy;

/**
 * Strategy for extracting searchable metadata from MANIFEST.MF files.
 */
public class ManifestFileStrategy implements FileTypeStrategy {

	private static final Logger LOG = Logger
			.getLogger(ManifestFileStrategy.class.getName());

	private static final int MAX_SNIPPET = 65535;

	@Override
	public Set<String> supportedExtensions() {
		return Set.of(".mf"); //$NON-NLS-1$
	}

	@Override
	public Set<String> supportedFilenames() {
		return Set.of("MANIFEST.MF"); //$NON-NLS-1$
	}

	@Override
	public BlobIndexData extract(String source, String filePath) {
		BlobIndexData data = new BlobIndexData();
		data.setFileType("manifest"); //$NON-NLS-1$
		data.setSourceSnippet(
				source.length() > MAX_SNIPPET
						? source.substring(0, MAX_SNIPPET)
						: source);

		try {
			Manifest manifest = new Manifest(new ByteArrayInputStream(
					source.getBytes(StandardCharsets.UTF_8)));
			java.util.jar.Attributes attrs = manifest
					.getMainAttributes();

			Set<String> fqns = new LinkedHashSet<>();
			Set<String> symbols = new LinkedHashSet<>();

			String bsn = attrs.getValue("Bundle-SymbolicName"); //$NON-NLS-1$
			if (bsn != null) {
				// Remove directives (e.g., ;singleton:=true)
				int semi = bsn.indexOf(';');
				fqns.add(semi > 0 ? bsn.substring(0, semi).trim()
						: bsn.trim());
			}

			addPackageList(attrs, "Export-Package", fqns); //$NON-NLS-1$
			addPackageList(attrs, "Import-Package", fqns); //$NON-NLS-1$
			addPackageList(attrs, "Require-Bundle", symbols); //$NON-NLS-1$

			String bundleName = attrs.getValue("Bundle-Name"); //$NON-NLS-1$
			if (bundleName != null) {
				symbols.add(bundleName.trim());
			}

			data.setFullyQualifiedNames(
					String.join("\n", fqns)); //$NON-NLS-1$
			data.setDeclaredTypes(
					String.join("\n", symbols)); //$NON-NLS-1$
		} catch (IOException e) {
			LOG.log(Level.FINE,
					"Failed to parse manifest: {0}", filePath); //$NON-NLS-1$
		}

		return data;
	}

	@Override
	public String fileType() {
		return "manifest"; //$NON-NLS-1$
	}

	private static void addPackageList(java.util.jar.Attributes attrs,
			String header, Set<String> target) {
		String value = attrs.getValue(header);
		if (value != null) {
			for (String pkg : value.split(",")) { //$NON-NLS-1$
				String trimmed = pkg.trim();
				int semi = trimmed.indexOf(';');
				target.add(
						semi > 0 ? trimmed.substring(0, semi).trim()
								: trimmed);
			}
		}
	}
}
