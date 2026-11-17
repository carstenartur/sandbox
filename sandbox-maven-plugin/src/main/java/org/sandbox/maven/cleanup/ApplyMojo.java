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
 * Apply goal: runs cleanup in apply mode (modifies files).
 *
 * Usage: {@code mvn sandbox:apply}
 */
@Mojo(name = "apply")
public class ApplyMojo extends AbstractSandboxMojo {

	@Override
	protected String getMode() {
		return "apply";
	}

	@Override
	protected void handleExitCode(int exitCode) throws MojoExecutionException, MojoFailureException {
		if (exitCode == 0) {
			getLog().info("Cleanup applied successfully.");
		} else if (exitCode == EXIT_CHANGES) {
			getLog().info("Cleanup applied, files were changed.");
		} else {
			throw new MojoExecutionException("Cleanup tool failed with exit code: " + exitCode);
		}
	}
}
