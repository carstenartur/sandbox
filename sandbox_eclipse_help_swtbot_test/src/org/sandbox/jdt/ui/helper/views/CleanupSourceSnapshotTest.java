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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class CleanupSourceSnapshotTest {

	@Test
	void comparesContentsRatherThanArrayIdentity() {
		assertEquals(Set.of(), snapshot(new byte[] { 1, 2 }).changedPaths(snapshot(new byte[] { 1, 2 })));
	}

	@Test
	void detectsByteDifferencesWithoutDecodingSourceText() {
		// Both malformed UTF-8 sequences decode to replacement characters, but are not identical bytes.
		assertEquals(Set.of("src/A.java"), snapshot(new byte[] { (byte) 0x80 }) //$NON-NLS-1$
				.changedPaths(snapshot(new byte[] { (byte) 0x81 })));
	}

	@Test
	void recordsAddedAndDeletedFilesInStableOrder() {
		var before= new CleanupSourceSnapshot(Map.of("src/Z.java", new byte[0], "src/B.java", new byte[] { 1 })); //$NON-NLS-1$ //$NON-NLS-2$
		var after= new CleanupSourceSnapshot(Map.of("src/A.java", new byte[0], "src/B.java", new byte[] { 2 })); //$NON-NLS-1$ //$NON-NLS-2$
		assertEquals(List.of("src/A.java", "src/B.java", "src/Z.java"), List.copyOf(before.changedPaths(after))); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	@Test
	void capturesEmptyFilesRatherThanTreatingThemAsAbsent() {
		assertEquals(Set.of("src/A.java"), snapshot(new byte[0]).changedPaths(new CleanupSourceSnapshot(Map.of()))); //$NON-NLS-1$
	}

	@Test
	void copiesBothTheInputMapAndItsByteArrays() {
		byte[] bytes= { 1, 2 };
		Map<String, byte[]> files= new HashMap<>();
		files.put("src/A.java", bytes); //$NON-NLS-1$
		var before= new CleanupSourceSnapshot(files);
		bytes[0]= 9;
		files.clear();
		assertEquals(Set.of(), before.changedPaths(snapshot(new byte[] { 1, 2 })));
	}

	@Test
	void exposesOnlyUnmodifiablePathSets() {
		var before= snapshot(new byte[] { 1 });
		assertThrows(UnsupportedOperationException.class, () -> before.paths().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> before.changedPaths(snapshot(new byte[] { 2 })).clear());
	}

	private static CleanupSourceSnapshot snapshot(byte[] bytes) {
		return new CleanupSourceSnapshot(Map.of("src/A.java", bytes)); //$NON-NLS-1$
	}
}
