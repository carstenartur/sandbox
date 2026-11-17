/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer.
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
package org.sandbox.maven.cleanup;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Diff goal: runs cleanup in diff mode (generates unified diff output).
 *
 * Usage: {@code mvn sandbox:diff}
 */
@Mojo(name = "diff")
public class DiffMojo extends AbstractSandboxMojo {

	@Override
	protected String getMode() {
		return "diff";
	}

	@Override
	protected void handleExitCode(int exitCode) throws MojoExecutionException, MojoFailureException {
		if (exitCode == 0) {
			getLog().info("No changes detected.");
		} else if (exitCode == EXIT_CHANGES) {
			String msg = "Changes detected. See diff output above.";
			if (failOnChanges) {
				throw new MojoFailureException(msg);
			}
			getLog().warn(msg);
		} else {
			throw new MojoExecutionException("Cleanup tool failed with exit code: " + exitCode);
		}
	}
}
