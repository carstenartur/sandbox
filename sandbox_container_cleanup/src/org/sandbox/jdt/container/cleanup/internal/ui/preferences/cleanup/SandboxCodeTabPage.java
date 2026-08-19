/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.cleanup.internal.ui.preferences.cleanup;

import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.APPEND_ARRAY_TO_LIST;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.CLEANUP;
import static org.sandbox.jdt.container.cleanup.internal.corext.fix.ContainerCleanUpOptions.UNIQUE_SEQUENCE_TO_SET;

import java.util.Map;

import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.preferences.cleanup.AbstractCleanUpTabPage;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.PlatformUI;

import org.sandbox.jdt.container.cleanup.internal.ui.fix.ContainerCleanUp;

/** Preference page for locally executable semantic container migrations. */
public final class SandboxCodeTabPage extends AbstractCleanUpTabPage {

	private static final String[] FALSE_TRUE= {
			CleanUpOptions.FALSE,
			CleanUpOptions.TRUE
	};

	public static final String ID=
			"org.eclipse.jdt.ui.cleanup.tabpage.sandbox.container_cleanup"; //$NON-NLS-1$

	@Override
	protected AbstractCleanUp[] createPreviewCleanUps(Map<String, String> values) {
		return new AbstractCleanUp[] { new ContainerCleanUp(values) };
	}

	@Override
	protected void doCreatePreferences(Composite composite, int numColumns) {
		PlatformUI.getWorkbench().getHelpSystem().setHelp(
				composite, "sandbox_container_cleanup.cleanup_configuration"); //$NON-NLS-1$
		Group group= createGroup(
				numColumns,
				composite,
				ContainerCleanUpMessages.ContainerTabPage_GroupName);
		CheckboxPreference master= createCheckboxPref(
				group,
				numColumns,
				ContainerCleanUpMessages.ContainerTabPage_Master,
				CLEANUP,
				FALSE_TRUE);

		intent(group);
		CheckboxPreference arrayToList= createCheckboxPref(
				group,
				numColumns - 1,
				ContainerCleanUpMessages.ContainerTabPage_AppendArrayToList,
				APPEND_ARRAY_TO_LIST,
				FALSE_TRUE);
		CheckboxPreference sequenceToSet= createCheckboxPref(
				group,
				numColumns - 1,
				ContainerCleanUpMessages.ContainerTabPage_UniqueSequenceToSet,
				UNIQUE_SEQUENCE_TO_SET,
				FALSE_TRUE);
		registerSlavePreference(
				master,
				new CheckboxPreference[] { arrayToList, sequenceToSet });
		registerPreference(master);
	}
}
