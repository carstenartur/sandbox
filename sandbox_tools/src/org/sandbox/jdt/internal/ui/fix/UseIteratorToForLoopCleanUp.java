/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
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
package org.sandbox.jdt.internal.ui.fix;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.manipulation.ICleanUpFixCore;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.CleanUpFixWrapper;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.CleanUpRequirements;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
/**
 */
@SuppressWarnings("restriction")
public class UseIteratorToForLoopCleanUp extends AbstractCleanUp {
	private final UseIteratorToForLoopCleanUpCore coreCleanUp= new UseIteratorToForLoopCleanUpCore();
	public UseIteratorToForLoopCleanUp(final Map<String, String> options) {
		setOptions(options);
	}
	public UseIteratorToForLoopCleanUp() {
	}
	@Override
	public void setOptions(final CleanUpOptions options) {
		coreCleanUp.setOptions(options);
	}
	@Override
	public CleanUpRequirements getRequirements() {
		return new CleanUpRequirements(coreCleanUp.getRequirementsCore());
	}
	@Override
	public ICleanUpFix createFix(final CleanUpContext context) throws CoreException {
		ICleanUpFixCore fixCore= coreCleanUp.createFixCore(context);
		return fixCore == null ? null : new CleanUpFixWrapper(fixCore);
	}
	@Override
	public String[] getStepDescriptions() {
		return coreCleanUp.getStepDescriptions();
	}
	@Override
	public String getPreview() {
		return coreCleanUp.getPreview();
	}
}
