/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
package org.sandbox.functional.core.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link LoopMetadata} safety rules.
 * 
 * <p>Validates strict rules for loop refactoring (Issue #670):</p>
 * <ul>
 *   <li>Collection modifications block conversion</li>
 *   <li>Iterator.remove() blocks conversion</li>
 *   <li>Index variable misuse blocks conversion</li>
 * </ul>
 * 
 * @see <a href="https://github.com/carstenartur/sandbox/issues/670">Issue #670</a>
 */
class LoopMetadataTest {

	@Test
	void testSafeMetadataIsConvertible() {
		LoopMetadata metadata = LoopMetadata.safe();
		assertThat(metadata.isConvertible()).isTrue();
	}

	@Test
	void testBreakBlocksConversion() {
		LoopMetadata metadata = new LoopMetadata(true, false, false, false, false, false, false);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testReturnBlocksConversion() {
		LoopMetadata metadata = new LoopMetadata(false, false, true, false, false, false, false);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testModifiesCollectionBlocksConversion() {
		LoopMetadata metadata = new LoopMetadata(false, false, false, true, false, false, false);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testIteratorRemoveBlocksConversion() {
		LoopMetadata metadata = new LoopMetadata(false, false, false, false, false, true, false);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testIndexBeyondGetBlocksConversion() {
		LoopMetadata metadata = new LoopMetadata(false, false, false, false, false, false, true);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testContinueAloneDoesNotBlockConversion() {
		LoopMetadata metadata = new LoopMetadata(false, true, false, false, false, false, false);
		assertThat(metadata.isConvertible()).isTrue();
	}

	@Test
	void testRequiresOrderingAloneDoesNotBlockConversion() {
		LoopMetadata metadata = new LoopMetadata(false, false, false, false, true, false, false);
		assertThat(metadata.isConvertible()).isTrue();
	}

	@Test
	void testMultipleBlockingFlags() {
		LoopMetadata metadata = new LoopMetadata(false, false, false, true, false, true, true);
		assertThat(metadata.isConvertible()).isFalse();
	}

	@Test
	void testSafeMetadataFieldValues() {
		LoopMetadata metadata = LoopMetadata.safe();
		assertThat(metadata.hasBreak()).isFalse();
		assertThat(metadata.hasContinue()).isFalse();
		assertThat(metadata.hasReturn()).isFalse();
		assertThat(metadata.modifiesCollection()).isFalse();
		assertThat(metadata.requiresOrdering()).isFalse();
		assertThat(metadata.hasIteratorRemove()).isFalse();
		assertThat(metadata.usesIndexBeyondGet()).isFalse();
	}
}
