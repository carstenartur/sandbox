/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.handlers;

import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;

/** The command label explicitly selects an Eclipse 3.4-or-newer target. */
public class PdeAppinfoModernizationHandler extends XMLCleanupHandler {

	@Override
	protected XMLCleanupService createXMLCleanupService() {
		return XMLCleanupService.compactFormatting(PdeSchemaTarget.ECLIPSE_3_4_OR_NEWER);
	}
}
