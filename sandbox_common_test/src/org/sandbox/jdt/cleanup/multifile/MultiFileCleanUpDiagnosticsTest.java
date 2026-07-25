/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.cleanup.multifile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;

class MultiFileCleanUpDiagnosticsTest {

	@Test
	void normalizesAndSerializesScopeCandidatesAndImpactDeterministically() {
		MultiFileCleanUpDiagnostics diagnostics= new MultiFileCleanUpDiagnostics("int-to-enum", //$NON-NLS-1$
				new MultiFileScopeDiagnostic(List.of("z", "a", "a"), List.of("c", "b"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
						"RELATED_SOURCE_CLOSURE", "Callers added", true), //$NON-NLS-1$ //$NON-NLS-2$
				List.of(
						MultiFileCandidateDiagnostic.rejected("method-b", "z", "PUBLIC_API", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								"Public method", List.of("z", "a")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						MultiFileCandidateDiagnostic.transformed("method-a", "a", //$NON-NLS-1$ //$NON-NLS-2$
								"Converted", List.of("b", "a", "b")))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$

		assertEquals(List.of("a", "z"), diagnostics.scope().selectedCompilationUnitHandles()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(List.of("b", "c"), diagnostics.scope().addedCompilationUnitHandles()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals("method-a", diagnostics.candidates().get(0).candidateId()); //$NON-NLS-1$
		assertEquals(List.of("a", "b"), diagnostics.candidates().get(0).relatedCompilationUnitHandles()); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(CleanUpImpact.PROJECT_CLOSED, diagnostics.impact());
		assertEquals(4, diagnostics.affectedCompilationUnitCount());
		assertFalse(diagnostics.impact().ordinarySaveActionAllowed());
		String json= diagnostics.toJson();
		assertEquals("""
				{"schemaVersion":"2","cleanupId":"int-to-enum","impact":"PROJECT_CLOSED","projectWide":true,"ordinarySaveActionAllowed":false,"affectedCompilationUnitCount":4,"compatibilityStatement":"The transformation changes multiple compilation units and is safe only for the proven closed source scope.","scope":{"selectedCompilationUnits":["cu-594e519ae499","cu-ca978112ca1b"],"addedCompilationUnits":["cu-2e7d2c03a950","cu-3e23e8160039"],"reasonCode":"RELATED_SOURCE_CLOSURE","explanation":"Callers added","complete":true},"candidates":[{"candidateId":"method-a","ownerCompilationUnit":"cu-ca978112ca1b","outcome":"TRANSFORMED","reasonCode":"TRANSFORMED","message":"Converted","relatedCompilationUnits":["cu-3e23e8160039","cu-ca978112ca1b"]},{"candidateId":"method-b","ownerCompilationUnit":"cu-594e519ae499","outcome":"REJECTED","reasonCode":"PUBLIC_API","message":"Public method","relatedCompilationUnits":["cu-594e519ae499","cu-ca978112ca1b"]}]}
				""".strip(), json);
		assertFalse(json.contains("\"ownerCompilationUnit\":\"a\"")); //$NON-NLS-1$
		assertFalse(json.contains("\"selectedCompilationUnits\":[\"a\"")); //$NON-NLS-1$
	}

	@Test
	void summaryReportsImpactCompatibilityAndCounts() {
		MultiFileCleanUpDiagnostics diagnostics= new MultiFileCleanUpDiagnostics("junit", //$NON-NLS-1$
				new MultiFileScopeDiagnostic(List.of("selected"), List.of("added"), //$NON-NLS-1$ //$NON-NLS-2$
						"RELATED_SOURCE_CLOSURE", "Rule user added", true), //$NON-NLS-1$ //$NON-NLS-2$
				List.of(
						MultiFileCandidateDiagnostic.transformed("resource", "selected", "Converted", List.of()), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						MultiFileCandidateDiagnostic.rejected("field", "added", "MIXED_RULE_MODES", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
								"Mixed modes", List.of()))); //$NON-NLS-1$
		RefactoringStatus status= new RefactoringStatus();

		diagnostics.appendSummary(status);

		assertTrue(status.hasInfo());
		String message= status.getMessageMatchingSeverity(RefactoringStatus.INFO);
		assertTrue(message.contains("PROJECT_CLOSED")); //$NON-NLS-1$
		assertTrue(message.contains("1 selected")); //$NON-NLS-1$
		assertTrue(message.contains("1 added")); //$NON-NLS-1$
		assertTrue(message.contains("2 affected")); //$NON-NLS-1$
		assertTrue(message.contains("1 transformed")); //$NON-NLS-1$
		assertTrue(message.contains("1 rejected")); //$NON-NLS-1$
		assertTrue(message.contains("MIXED_RULE_MODES=1")); //$NON-NLS-1$
		assertTrue(message.contains("Compatibility:")); //$NON-NLS-1$
	}

	@Test
	void localSafeImpactAllowsOrdinarySaveActions() {
		MultiFileCleanUpDiagnostics diagnostics= MultiFileCleanUpDiagnostics.empty();

		assertEquals(CleanUpImpact.LOCAL_SAFE, diagnostics.impact());
		assertTrue(diagnostics.impact().ordinarySaveActionAllowed());
		assertFalse(diagnostics.impact().projectWide());
	}
}
