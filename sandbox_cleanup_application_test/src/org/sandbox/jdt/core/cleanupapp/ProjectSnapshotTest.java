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
package org.sandbox.jdt.core.cleanupapp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectSnapshotTest {

	@TempDir
	Path temporaryDirectory;

	@Test
	void restoresEveryCapturedFileByteExactly() throws Exception {
		Path a= write("a/A.java", "class A { }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		Path b= write("b/B.java", "class B { int value; }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ProjectSnapshot snapshot= ProjectSnapshot.capture(List.of(b.toFile(), a.toFile()));

		Files.writeString(a, "changed A", StandardCharsets.UTF_8); //$NON-NLS-1$
		Files.writeString(b, "changed B", StandardCharsets.UTF_8); //$NON-NLS-1$

		assertEquals(2, snapshot.verify().stream().filter(result -> !result.restored()).count());
		List<ProjectSnapshot.Verification> restored= snapshot.restoreAndVerify();

		assertTrue(restored.stream().allMatch(ProjectSnapshot.Verification::restored));
		assertEquals("class A { }\n", Files.readString(a, StandardCharsets.UTF_8)); //$NON-NLS-1$
		assertEquals("class B { int value; }\n", Files.readString(b, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void captureIsDeterministicDeduplicatedAndDefensivelyCopied() throws Exception {
		Path a= write("src/A.java", "class A { }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		Path b= write("src/B.java", "class B { }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		ProjectSnapshot snapshot= ProjectSnapshot.capture(List.of(b.toFile(), a.toFile(), a.toFile()));

		assertEquals(List.of(a.toRealPath(), b.toRealPath()),
				snapshot.entries().stream().map(ProjectSnapshot.Entry::path).toList());
		byte[] exposed= snapshot.entries().get(0).content();
		exposed[0]= (byte) 'X';

		assertArrayEquals("class A { }\n".getBytes(StandardCharsets.UTF_8), //$NON-NLS-1$
				snapshot.entries().get(0).content());
	}

	@Test
	void restoreContinuesAfterOnePathFails() throws Exception {
		Path unavailable= write("a/deleted/A.java", "class A { }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		Path restorable= write("b/B.java", "class B { }\n"); //$NON-NLS-1$ //$NON-NLS-2$
		Path expectedUnavailablePath= unavailable.toRealPath();
		ProjectSnapshot snapshot= ProjectSnapshot.capture(List.of(unavailable.toFile(), restorable.toFile()));
		Files.delete(unavailable);
		Files.delete(unavailable.getParent());
		Files.writeString(restorable, "changed", StandardCharsets.UTF_8); //$NON-NLS-1$

		ProjectSnapshot.RestoreException exception= assertThrows(ProjectSnapshot.RestoreException.class,
				snapshot::restoreAndVerify);

		assertEquals(1, exception.failures().size());
		assertEquals(expectedUnavailablePath, exception.failures().get(0).path());
		assertFalse(exception.failures().get(0).restored());
		assertEquals("class B { }\n", Files.readString(restorable, StandardCharsets.UTF_8)); //$NON-NLS-1$
	}

	@Test
	void nullAndNonRegularEntriesAreRejectedBeforeAUsableSnapshotExists() throws Exception {
		Path directory= Files.createDirectories(temporaryDirectory.resolve("directory")); //$NON-NLS-1$

		assertThrows(NullPointerException.class, () -> ProjectSnapshot.capture(null));
		assertThrows(java.io.IOException.class, () -> ProjectSnapshot.capture(java.util.Arrays.asList((File) null)));
		assertThrows(java.io.IOException.class, () -> ProjectSnapshot.capture(List.of(directory.toFile())));
	}

	private Path write(String relativePath, String content) throws Exception {
		Path path= temporaryDirectory.resolve(relativePath);
		Files.createDirectories(path.getParent());
		return Files.writeString(path, content, StandardCharsets.UTF_8);
	}
}
