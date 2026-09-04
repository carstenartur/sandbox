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
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.ui.tests.quickfix.XMLTestUtils;

/** Reuses the exact JDT Core and JDT UI QA checkouts as a real PDE XML corpus. */
class PinnedJdtXmlCorpusTest {

	private static final String REPOSITORY_ROOT_PROPERTY= "sandbox.repository.root"; //$NON-NLS-1$
	private static final String JDT_CORE_PROPERTY= "sandbox.xml.corpus.jdtCore"; //$NON-NLS-1$
	private static final String JDT_UI_PROPERTY= "sandbox.xml.corpus.jdtUi"; //$NON-NLS-1$
	private static final String REQUIRED_PROPERTY= "sandbox.xml.corpus.required"; //$NON-NLS-1$

	private static final List<String> JDT_CORE_FILES= List.of(
			"org.eclipse.jdt.core/plugin.xml", //$NON-NLS-1$
			"org.eclipse.jdt.core/schema/codeFormatter.exsd", //$NON-NLS-1$
			"org.eclipse.jdt.core/schema/compilationParticipant.exsd"); //$NON-NLS-1$
	private static final List<String> JDT_UI_FILES= List.of(
			"org.eclipse.jdt.ui/plugin.xml", //$NON-NLS-1$
			"org.eclipse.jdt.ui/schema/cleanUps.exsd", //$NON-NLS-1$
			"org.eclipse.jdt.ui/schema/quickFixProcessors.exsd"); //$NON-NLS-1$

	private final NullProgressMonitor monitor= new NullProgressMonitor();

	@Test
	void verifiesPinnedJdtCorePdeXmlCorpus() throws Exception {
		verifyRepository(JDT_CORE_PROPERTY, "PIN_JDT_CORE_COMMIT", //$NON-NLS-1$
				"PinnedJdtCoreXmlCorpus", JDT_CORE_FILES); //$NON-NLS-1$
	}

	@Test
	void verifiesPinnedJdtUiPdeXmlCorpus() throws Exception {
		verifyRepository(JDT_UI_PROPERTY, "PIN_JDT_UI_COMMIT", //$NON-NLS-1$
				"PinnedJdtUiXmlCorpus", JDT_UI_FILES); //$NON-NLS-1$
	}

	private void verifyRepository(String property, String pinKey, String projectName,
			Iterable<String> relativeFiles) throws Exception {
		String configured= System.getProperty(property, "").trim(); //$NON-NLS-1$
		if (configured.isEmpty()) {
			assertFalse(Boolean.getBoolean(REQUIRED_PROPERTY),
					() -> "Required pinned XML corpus path is not configured: " + property); //$NON-NLS-1$
			Assumptions.assumeTrue(false,
					() -> "Pinned XML corpus path is not configured: " + property); //$NON-NLS-1$
		}

		Path repository= Path.of(configured).toAbsolutePath().normalize();
		assertTrue(Files.isDirectory(repository), () -> "Missing pinned checkout: " + repository); //$NON-NLS-1$
		assertEquals(readPin(pinKey), gitHead(repository),
				() -> "Pinned checkout does not match " + pinKey); //$NON-NLS-1$

		IProject project= ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		if (project.exists()) {
			project.delete(true, true, monitor);
		}
		project.create(monitor);
		project.open(monitor);
		try {
			XMLCleanupService service= new XMLCleanupService();
			long totalBytes= 0;
			int changedFiles= 0;
			for (String relative : relativeFiles) {
				Path source= repository.resolve(relative);
				assertTrue(Files.isRegularFile(source),
						() -> "Pinned PDE XML source is missing: " + source); //$NON-NLS-1$
				byte[] bytes= Files.readAllBytes(source);
				totalBytes+= bytes.length;
				assertTrue(bytes.length > 2_000,
						() -> "The selected real-corpus file is unexpectedly small: " + relative); //$NON-NLS-1$

				String projectPath= relative.substring(relative.indexOf('/') + 1);
				IFile copy= createFile(project, projectPath, bytes);
				assertTrue(service.isPDERelevantFile(copy),
						() -> "Real PDE XML file was filtered out: " + projectPath); //$NON-NLS-1$
				String before= read(copy);
				boolean changed= service.processFile(copy, monitor);
				if (changed) {
					changedFiles++;
				}
				String after= read(copy);
				assertTrue(XMLTestUtils.isXmlSemanticallyEqualWithComments(before, after),
						() -> "PDE XML semantics or comments changed for " + relative); //$NON-NLS-1$
				assertFalse(service.processFile(copy, monitor),
						() -> "PDE XML cleanup is not idempotent for " + relative); //$NON-NLS-1$
			}
			assertTrue(totalBytes > 25_000,
					"The selected upstream XML corpus is not substantial: " + totalBytes); //$NON-NLS-1$
			assertTrue(changedFiles > 0,
					() -> "The pinned corpus exercised no real cleanup change for " + property); //$NON-NLS-1$
		} finally {
			project.delete(true, true, monitor);
		}
	}

	private static String readPin(String pinKey) throws IOException {
		String configuredRoot= System.getProperty(REPOSITORY_ROOT_PROPERTY, "").trim(); //$NON-NLS-1$
		assertFalse(configuredRoot.isEmpty(),
				() -> "Repository root is not configured: " + REPOSITORY_ROOT_PROPERTY); //$NON-NLS-1$
		Path pinFile= Path.of(configuredRoot).toAbsolutePath().normalize()
				.resolve("qa/upstream-jdt/pins.env"); //$NON-NLS-1$
		Properties properties= new Properties();
		try (Reader reader= Files.newBufferedReader(pinFile, StandardCharsets.UTF_8)) {
			properties.load(reader);
		}
		String pin= properties.getProperty(pinKey);
		if (pin == null || pin.isBlank()) {
			throw new IOException("Missing repository pin " + pinKey + " in " + pinFile); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return pin.trim();
	}

	private static String gitHead(Path repository) throws IOException {
		Path gitDirectory= gitDirectory(repository);
		String head= readTrimmed(gitDirectory.resolve("HEAD")); //$NON-NLS-1$
		if (!head.startsWith("ref: ")) { //$NON-NLS-1$
			return head;
		}

		String reference= head.substring("ref: ".length()).trim(); //$NON-NLS-1$
		Path looseReference= gitDirectory.resolve(reference);
		if (Files.isRegularFile(looseReference)) {
			return readTrimmed(looseReference);
		}

		Path packedReferences= gitDirectory.resolve("packed-refs"); //$NON-NLS-1$
		if (Files.isRegularFile(packedReferences)) {
			for (String line : Files.readAllLines(packedReferences, StandardCharsets.UTF_8)) {
				int separator= line.indexOf(' ');
				if (separator > 0 && line.substring(separator + 1).equals(reference)) {
					return line.substring(0, separator);
				}
			}
		}
		throw new IOException("Could not resolve Git reference " + reference + " in " + repository); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static Path gitDirectory(Path repository) throws IOException {
		Path dotGit= repository.resolve(".git"); //$NON-NLS-1$
		if (Files.isDirectory(dotGit)) {
			return dotGit;
		}

		String descriptor= readTrimmed(dotGit);
		String prefix= "gitdir: "; //$NON-NLS-1$
		if (!descriptor.startsWith(prefix)) {
			throw new IOException("Unsupported Git directory descriptor in " + dotGit); //$NON-NLS-1$
		}
		Path configured= Path.of(descriptor.substring(prefix.length()).trim());
		return configured.isAbsolute() ? configured.normalize() : repository.resolve(configured).normalize();
	}

	private static String readTrimmed(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8).trim();
	}

	private IFile createFile(IProject project, String path, byte[] bytes) throws Exception {
		IFile file= project.getFile(path);
		if (file.getParent() instanceof IFolder folder && !folder.exists()) {
			createFolder(folder);
		}
		try (ByteArrayInputStream input= new ByteArrayInputStream(bytes)) {
			file.create(input, true, monitor);
		}
		file.setCharset(StandardCharsets.UTF_8.name(), monitor);
		return file;
	}

	private void createFolder(IFolder folder) throws Exception {
		if (folder.getParent() instanceof IFolder parent && !parent.exists()) {
			createFolder(parent);
		}
		folder.create(true, true, monitor);
	}

	private static String read(IFile file) throws Exception {
		try (InputStream input= file.getContents()) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
