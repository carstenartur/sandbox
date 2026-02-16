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

import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 * Check goal: runs cleanup in check mode (dry-run).
 * Fails the build if changes are detected (exit code 2) and failOnChanges is true.
 *
 * Usage: {@code mvn sandbox:check}
 */
@Mojo(name = "check", defaultPhase = LifecyclePhase.VERIFY)
public class CheckMojo extends AbstractSandboxMojo {

	@Override
	protected String getMode() {
		return "check";
	}
}
