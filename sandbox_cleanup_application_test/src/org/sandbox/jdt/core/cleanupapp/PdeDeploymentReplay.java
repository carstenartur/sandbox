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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.resources.IFile;

/** Exports the actual before/after bytes for an opt-in, independent Tycho deployment replay. */
final class PdeDeploymentReplay {
	private PdeDeploymentReplay() {
	}

	static void export(Map<IFile, byte[]> originals) throws Exception {
		String location= System.getenv("SANDBOX_PDE_SUITE_REPLAY"); //$NON-NLS-1$
		if (location == null || location.isBlank()) {
			return;
		}
		Path root= Path.of(location).toAbsolutePath();
		Files.createDirectories(root);
		for (String phase : new String[] { "before", "after" }) { //$NON-NLS-1$ //$NON-NLS-2$
			Path project= Files.createDirectory(root.resolve(phase));
			for (var entry : originals.entrySet()) {
				IFile file= entry.getKey();
				String relative= file.getProjectRelativePath().toPortableString();
				Path output= relative.startsWith("deployment/") //$NON-NLS-1$
						? project.resolve("feature").resolve(file.getName()) : project.resolve("bundle").resolve(relative); //$NON-NLS-1$ //$NON-NLS-2$
				Files.createDirectories(Objects.requireNonNull(output.getParent()));
				Files.write(output, "before".equals(phase) ? entry.getValue() //$NON-NLS-1$
						: Files.readAllBytes(file.getLocation().toFile().toPath()));
			}
			Files.writeString(project.resolve("feature/build.properties"), "bin.includes = feature.xml\n", StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}
}
