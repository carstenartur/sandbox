/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.DocumentationPolicy;
import org.sandbox.jdt.internal.corext.fix.helper.SchemaTransformationUtils.PdeSchemaTarget;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;

/** Every eligible XML resource in every actual Eclipse project at the R4_41 pins. */
class PinnedJdtXmlCorpusTest {

	private record Upstream(String repository, String commit) { }

	private static final Map<String, Upstream> UPSTREAMS= Map.of(
			"core", new Upstream("eclipse-jdt/eclipse.jdt.core", "6725c16c24d94c83302346dc384bb915a0f2fe1a"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"ui", new Upstream("eclipse-jdt/eclipse.jdt.ui", "54bd63e6453a7eb5db0bfc1d60f17e9568095619"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"swt", new Upstream("eclipse-platform/eclipse.platform.swt", "3e3b24b4691c4f18a7d5e22653fe28d4c47385d7")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final String REF= "refs/tags/R4_41"; //$NON-NLS-1$
	private final NullProgressMonitor monitor= new NullProgressMonitor();
	@TempDir
	Path temporary;

	@Test
	void verifiesEveryEligibleResourceWithEveryOptionCombination() throws Exception {
		String family= System.getProperty("sandbox.xml.corpus.family", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
		if (family.isEmpty()) {
			assertFalse(Boolean.getBoolean("sandbox.xml.corpus.required"), "Required XML corpus family is missing"); //$NON-NLS-1$ //$NON-NLS-2$
			Assumptions.abort("Enable pde-xml-upstream-corpus and select core, ui or swt"); //$NON-NLS-1$
		}
		assertTrue(UPSTREAMS.containsKey(family), () -> "Unknown XML corpus family: " + family); //$NON-NLS-1$
		Upstream upstream= UPSTREAMS.get(family);
		Path outputRoot= Path.of(System.getProperty("sandbox.xml.corpus.output", "target/xml-upstream-corpus")) //$NON-NLS-1$ //$NON-NLS-2$
				.toAbsolutePath();
		Files.createDirectories(outputRoot);
		Path output= Files.createTempDirectory(outputRoot, family + "-"); //$NON-NLS-1$
		System.out.println("XML corpus evidence: " + output); //$NON-NLS-1$
		URI remote= URI.create("https://github.com/" + upstream.repository + ".git"); //$NON-NLS-1$ //$NON-NLS-2$
		try (PinnedGitRepository fixture= PinnedGitRepository.cloneAt(temporary.resolve(family), remote, REF, upstream.commit)) {
			assertEquals(upstream.commit, fixture.headCommit());
			verifyRepository(family, fixture.directory(), output, upstream);
		}
	}

	private void verifyRepository(String family, Path repository, Path output, Upstream upstream) throws Exception {
		List<Path> paths;
		try (var walk= Files.walk(repository)) {
			paths= walk.filter(Files::isRegularFile)
					.filter(path -> !repository.relativize(path).startsWith(".git")).sorted().toList(); //$NON-NLS-1$
		}
		Set<Path> projectRoots= paths.stream().filter(path -> path.getFileName().toString().equals(".project")) //$NON-NLS-1$
				.map(Path::getParent).collect(java.util.stream.Collectors.toSet());
		assertFalse(projectRoots.isEmpty(), "Upstream contains no Eclipse project metadata"); //$NON-NLS-1$
		Map<Path, IProject> projects= new LinkedHashMap<>();
		List<Executable> cases= new ArrayList<>();
		List<String> inventory= new ArrayList<>(List.of("path\tproject\tmode\tbefore.sha256\tafter.sha256\tchanged\tstatus")); //$NON-NLS-1$
		int[] counts= new int[3]; // eligible files, executed cases, changed cases
		Properties receipt= new Properties();
		receipt.setProperty("repository", upstream.repository); //$NON-NLS-1$
		receipt.setProperty("ref", REF); //$NON-NLS-1$
		receipt.setProperty("commit", upstream.commit); //$NON-NLS-1$
		receipt.setProperty("status", "FAIL"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			for (Path source : paths) {
				String name= source.getFileName().toString();
				if (!(Set.of("plugin.xml", "fragment.xml", "feature.xml").contains(name) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						|| name.endsWith(".exsd") || name.endsWith(".xsd"))) { //$NON-NLS-1$ //$NON-NLS-2$
					continue;
				}
				Path owner= source.getParent();
				while (owner != null && owner.startsWith(repository) && !projectRoots.contains(owner)) {
					owner= owner.getParent();
				}
				String relative= repository.relativize(source).toString();
				if (owner == null || !projectRoots.contains(owner)) {
					inventory.add(relative + "\t-\t-\t-\t-\tfalse\tNO_ECLIPSE_PROJECT"); //$NON-NLS-1$
					continue;
				}
				IProject project= projects.get(owner);
				if (project == null) {
					project= ResourcesPlugin.getWorkspace().getRoot().getProject("XmlCorpus-" + family + "-" + projects.size()); //$NON-NLS-1$ //$NON-NLS-2$
					if (project.exists()) {
						project.delete(true, true, monitor);
					}
					project.create(monitor);
					project.open(monitor);
					projects.put(owner, project);
				}
				IFile copy= project.getFile(owner.relativize(source).toString());
				if (!new XMLCleanupService().isPDERelevantFile(copy)) {
					inventory.add(relative + "\t" + repository.relativize(owner) + "\t-\t-\t-\tfalse\tOUTSIDE_PDE_SCOPE"); //$NON-NLS-1$ //$NON-NLS-2$
					continue;
				}
				counts[0]++;
				byte[] original= Files.readAllBytes(source);
				createFile(copy, original, resourceCharset(owner, owner.relativize(source)));
				write(output.resolve("original").resolve(relative), original); //$NON-NLS-1$
				String projectPath= repository.relativize(owner).toString();
				for (PdeSchemaTarget target : PdeSchemaTarget.values()) {
					for (DocumentationPolicy documentation : DocumentationPolicy.values()) {
						for (boolean indent : new boolean[] { false, true }) {
							cases.add(() -> verifyFile(copy, source, relative, projectPath, original, output,
									target, documentation, indent, inventory, counts));
						}
					}
				}
			}
			assertTrue(counts[0] > 0, "The complete upstream inventory contains no eligible PDE XML"); //$NON-NLS-1$
			assertAll(family + " R4_41 XML corpus", cases); //$NON-NLS-1$
			assertEquals(counts[0] * 8, counts[1], "Every file must run all eight independent option combinations"); //$NON-NLS-1$
			assertTrue(counts[2] > 0, "Corpus exercised no actual XML transformation"); //$NON-NLS-1$
			receipt.setProperty("status", "PASS"); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			receipt.setProperty("projects.discovered", Integer.toString(projectRoots.size())); //$NON-NLS-1$
			receipt.setProperty("files.eligible", Integer.toString(counts[0])); //$NON-NLS-1$
			receipt.setProperty("cases.executed", Integer.toString(counts[1])); //$NON-NLS-1$
			receipt.setProperty("cases.changed", Integer.toString(counts[2])); //$NON-NLS-1$
			Files.write(output.resolve("inventory.tsv"), inventory, StandardCharsets.UTF_8); //$NON-NLS-1$
			try (var writer= Files.newBufferedWriter(output.resolve("receipt.properties"))) { //$NON-NLS-1$
				receipt.store(writer, "Actual XMLCleanupService workspace executions"); //$NON-NLS-1$
			}
			for (IProject project : projects.values()) {
				project.delete(true, true, monitor);
			}
		}
	}

	private void verifyFile(IFile copy, Path source, String relative, String project, byte[] original, Path output,
			PdeSchemaTarget target, DocumentationPolicy documentation, boolean indent,
			List<String> inventory, int[] counts) throws Throwable {
		String mode= target.name() + "-" + documentation.name() + "-indent-" + indent; //$NON-NLS-1$ //$NON-NLS-2$
		Path evidence= output.resolve(mode).resolve(relative);
		boolean changed= false;
		String status= "FAIL"; //$NON-NLS-1$
		counts[1]++;
		try {
			copy.setContents(new ByteArrayInputStream(original), true, false, monitor);
			XMLCleanupService service= new XMLCleanupService(target, documentation);
			service.setEnableIndent(indent);
			assertTrue(service.isPDERelevantFile(copy), relative);
			String before= XMLResourceSupport.read(copy).content();
			changed= service.processFile(copy, monitor);
			byte[] after= bytes(copy);
			write(evidence, after);
			assertEquals(!Arrays.equals(original, after), changed, relative);
			if (changed) {
				counts[2]++;
			}
			PdeXmlCorpusAssertions.assertMeaning(before, XMLResourceSupport.read(copy).content(), target, documentation);
			if (relative.endsWith(".exsd")) { //$NON-NLS-1$
				PdeXmlCorpusAssertions.assertConsumers(source, copy.getLocation().toFile().toPath());
			}
			assertFalse(service.processFile(copy, monitor), "Second pass changed " + relative); //$NON-NLS-1$
			assertArrayEquals(after, bytes(copy), "Second pass changed bytes in " + relative); //$NON-NLS-1$
			assertArrayEquals(original, Files.readAllBytes(source), "Upstream fixture was modified: " + relative); //$NON-NLS-1$
			status= "PASS"; //$NON-NLS-1$
		} catch (Throwable failure) {
			StringWriter details= new StringWriter();
			failure.printStackTrace(new PrintWriter(details));
			write(Path.of(evidence + ".failure.txt"), details.toString().getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
			throw new AssertionError(relative + " [" + mode + "]", failure); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			byte[] after= bytes(copy);
			write(evidence, after);
			inventory.add(relative + "\t" + project + "\t" + mode + "\t" + sha(original) + "\t" + sha(after) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ "\t" + changed + "\t" + status); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String resourceCharset(Path project, Path relative) throws Exception {
		Properties encodings= new Properties();
		Path preferences= project.resolve(".settings/org.eclipse.core.resources.prefs"); //$NON-NLS-1$
		if (Files.isRegularFile(preferences)) {
			try (var reader= Files.newBufferedReader(preferences)) {
				encodings.load(reader);
			}
		}
		for (Path resource= relative; resource != null; resource= resource.getParent()) {
			String encoding= encodings.getProperty("encoding//" + resource.toString().replace('\\', '/')); //$NON-NLS-1$
			if (encoding != null) {
				return encoding;
			}
		}
		return encodings.getProperty("encoding/<project>", StandardCharsets.UTF_8.name()); //$NON-NLS-1$
	}

	private void createFile(IFile file, byte[] bytes, String charset) throws Exception {
		if (file.getParent() instanceof IFolder folder && !folder.exists()) {
			createFolder(folder);
		}
		file.create(new ByteArrayInputStream(bytes), true, monitor);
		file.setCharset(charset, monitor);
	}

	private void createFolder(IFolder folder) throws Exception {
		if (folder.getParent() instanceof IFolder parent && !parent.exists()) {
			createFolder(parent);
		}
		folder.create(true, true, monitor);
	}

	private static byte[] bytes(IFile file) throws Exception {
		try (var input= file.getContents()) {
			return input.readAllBytes();
		}
	}

	private static void write(Path path, byte[] bytes) throws Exception {
		Files.createDirectories(path.getParent());
		Files.write(path, bytes);
	}

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); //$NON-NLS-1$
	}
}
