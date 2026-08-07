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
package org.sandbox.jdt.ui.helper.views;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Locates the repository checkout without relying on a CI-specific variable. */
final class SandboxCheckout {

	private SandboxCheckout() {
	}

	/**
	 * Locates the Sandbox checkout root.
	 *
	 * @param optionalProperty an optional system property containing a path in or
	 *            below the checkout; may be {@code null}
	 * @return the checkout root
	 * @throws IllegalStateException if no checkout can be identified
	 */
	static Path locate(String optionalProperty) {
		List<Path> starts= new ArrayList<>();
		if (optionalProperty != null) {
			String configured= System.getProperty(optionalProperty);
			if (configured != null && !configured.isBlank()) {
				starts.add(Path.of(configured));
			}
		}

		starts.add(Path.of(System.getProperty("user.dir", "."))); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			starts.add(Path.of(SandboxCheckout.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI()));
		} catch (URISyntaxException | NullPointerException exception) {
			// The working-directory search below remains available.
		}

		for (Path start : starts) {
			Path root= findFrom(start);
			if (root != null) {
				return root;
			}
		}
		throw new IllegalStateException("Could not locate the Sandbox checkout from " + starts); //$NON-NLS-1$
	}

	private static Path findFrom(Path start) {
		Path candidate= start.toAbsolutePath().normalize();
		if (Files.isRegularFile(candidate)) {
			candidate= candidate.getParent();
		}
		while (candidate != null) {
			if (isCheckoutRoot(candidate)) {
				return candidate;
			}
			candidate= candidate.getParent();
		}
		return null;
	}

	private static boolean isCheckoutRoot(Path candidate) {
		return Files.isRegularFile(candidate.resolve("pom.xml")) //$NON-NLS-1$
				&& Files.isRegularFile(candidate.resolve("sandbox_help_build/pom.xml")) //$NON-NLS-1$
				&& Files.isDirectory(candidate.resolve("sandbox_target")) //$NON-NLS-1$
				&& Files.isDirectory(candidate.resolve("sandbox_usage_view_test")); //$NON-NLS-1$
	}
}
