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

import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.XMLCleanupService;

/** The command explicitly selects plain reference-section boundary cleanup. */
public class PdeDocumentationBoundaryHandler extends XMLCleanupHandler {

	@Override
	protected XMLCleanupService createXMLCleanupService() {
		return XMLCleanupService.compactFormatting(DocumentationPolicy.TRIM_PLAIN_SECTION_BOUNDARIES);
	}
}
