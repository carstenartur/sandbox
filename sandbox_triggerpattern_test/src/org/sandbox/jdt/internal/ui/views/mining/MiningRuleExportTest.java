/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.views.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class MiningRuleExportTest {
	@Test
	public void exportPreservesTheCompleteSelectedDocument() {
		String document= "<!id: team>\r\n<!binding-policy: required>\r\n"
				+ "$x\r\n=> $x :: sourceVersionGE(17)\r\n=> $x :: otherwise\r\n;;\r\n";
		assertEquals(document, RefactoringMiningView.buildHintFileContent(List.of(document)));
	}

	@Test
	public void independentFileMetadataMustNotBeSilentlyMerged() {
		assertThrows(IllegalArgumentException.class,
				() -> RefactoringMiningView.buildHintFileContent(List.of("first", "second")));
	}
}
