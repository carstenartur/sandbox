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
package org.eclipse.jgit.storage.hibernate.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jgit.storage.hibernate.entity.JavaBlobIndex;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RankFusionUtil}.
 */
class RankFusionUtilTest {

	@Test
	void emptyListsReturnEmpty() {
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(List.of(), List.of(), 10);
		assertTrue(result.isEmpty());
	}

	@Test
	void singleSemanticListReturnsSameOrder() {
		JavaBlobIndex a = createEntry(1L);
		JavaBlobIndex b = createEntry(2L);
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(List.of(a, b), List.of(), 10);
		assertEquals(2, result.size());
		assertEquals(1L, result.get(0).getId());
		assertEquals(2L, result.get(1).getId());
	}

	@Test
	void singleFulltextListReturnsSameOrder() {
		JavaBlobIndex a = createEntry(1L);
		JavaBlobIndex b = createEntry(2L);
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(List.of(), List.of(a, b), 10);
		assertEquals(2, result.size());
		assertEquals(1L, result.get(0).getId());
		assertEquals(2L, result.get(1).getId());
	}

	@Test
	void overlappingEntriesGetHigherScore() {
		JavaBlobIndex shared = createEntry(1L);
		JavaBlobIndex onlySemantic = createEntry(2L);
		JavaBlobIndex onlyFulltext = createEntry(3L);

		// Shared appears in both lists → should rank first
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(
						List.of(onlySemantic, shared),
						List.of(onlyFulltext, shared), 10);

		assertEquals(3, result.size());
		assertEquals(1L, result.get(0).getId(),
				"Shared entry should rank first due to double RRF score"); //$NON-NLS-1$
	}

	@Test
	void topKLimitsResults() {
		JavaBlobIndex a = createEntry(1L);
		JavaBlobIndex b = createEntry(2L);
		JavaBlobIndex c = createEntry(3L);
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(List.of(a, b, c), List.of(), 2);
		assertEquals(2, result.size());
	}

	@Test
	void nullIdsAreSkipped() {
		JavaBlobIndex withId = createEntry(1L);
		JavaBlobIndex noId = new JavaBlobIndex(); // id = null
		List<JavaBlobIndex> result = RankFusionUtil
				.reciprocalRankFusion(List.of(withId, noId), List.of(),
						10);
		assertEquals(1, result.size());
		assertEquals(1L, result.get(0).getId());
	}

	private static JavaBlobIndex createEntry(Long id) {
		JavaBlobIndex entry = new JavaBlobIndex();
		entry.setId(id);
		return entry;
	}
}
