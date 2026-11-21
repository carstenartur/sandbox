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
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

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

	private record CorpusResource(IFile copy, Path source, String relative, String project, byte[] original) { }

	private static final class CorpusCounts {
		int eligibleFiles;
		int executedCases;
		int changedCases;
	}

	private static final Map<String, Upstream> UPSTREAMS= Map.of(
			"core", new Upstream("eclipse-jdt/eclipse.jdt.core", "6725c16c24d94c83302346dc384bb915a0f2fe1a"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"ui", new Upstream("eclipse-jdt/eclipse.jdt.ui", "54bd63e6453a7eb5db0bfc1d60f17e9568095619"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			"swt", new Upstream("eclipse-platform/eclipse.platform.swt", "3e3b24b4691c4f18a7d5e22653fe28d4c47385d7")); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final String REF= "refs/tags/R4_41"; //$NON-NLS-1$
	private static final Set<String> DESCRIPTOR_NAMES= Set.of("plugin.xml", "fragment.xml", "feature.xml"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	private static final boolean[] INDENT_OPTIONS= { false, true };
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
		Set<Path> projectRoots= paths.stream().filter(path -> ".project".equals(fileName(path))) //$NON-NLS-1$
				.map(Path::getParent).collect(Collectors.toSet());
		assertFalse(projectRoots.isEmpty(), "Upstream contains no Eclipse project metadata"); //$NON-NLS-1$
		Map<Path, IProject> projects= new LinkedHashMap<>();
		List<String> inventory= new ArrayList<>(List.of("path\tproject\tmode\tbefore.sha256\tafter.sha256\tchanged\tstatus")); //$NON-NLS-1$
		CorpusCounts counts= new CorpusCounts();
		Properties receipt= new Properties();
		receipt.setProperty("repository", upstream.repository); //$NON-NLS-1$
		receipt.setProperty("ref", REF); //$NON-NLS-1$
		receipt.setProperty("commit", upstream.commit); //$NON-NLS-1$
		receipt.setProperty("status", "FAIL"); //$NON-NLS-1$ //$NON-NLS-2$
		try {
			List<Executable> cases= collectCases(family, repository, output, paths, projectRoots, projects, inventory, counts);
			assertTrue(counts.eligibleFiles > 0, "The complete upstream inventory contains no eligible PDE XML"); //$NON-NLS-1$
			assertAll(family + " R4_41 XML corpus", cases); //$NON-NLS-1$
			assertEquals(counts.eligibleFiles * 8, counts.executedCases, "Every file must run all eight independent option combinations"); //$NON-NLS-1$
			assertTrue(counts.changedCases > 0, "Corpus exercised no actual XML transformation"); //$NON-NLS-1$
			receipt.setProperty("status", "PASS"); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			receipt.setProperty("projects.discovered", Integer.toString(projectRoots.size())); //$NON-NLS-1$
			receipt.setProperty("files.eligible", Integer.toString(counts.eligibleFiles)); //$NON-NLS-1$
			receipt.setProperty("cases.executed", Integer.toString(counts.executedCases)); //$NON-NLS-1$
			receipt.setProperty("cases.changed", Integer.toString(counts.changedCases)); //$NON-NLS-1$
			Files.write(output.resolve("inventory.tsv"), inventory, StandardCharsets.UTF_8); //$NON-NLS-1$
			try (var writer= Files.newBufferedWriter(output.resolve("receipt.properties"), StandardCharsets.UTF_8)) { //$NON-NLS-1$
				receipt.store(writer, "Actual XMLCleanupService workspace executions"); //$NON-NLS-1$
			}
			for (IProject project : projects.values()) {
				project.delete(true, true, monitor);
			}
		}
	}

	private List<Executable> collectCases(String family, Path repository, Path output, List<Path> paths,
			Set<Path> projectRoots, Map<Path, IProject> projects, List<String> inventory, CorpusCounts counts) throws Exception {
		XMLCleanupService scope= new XMLCleanupService();
		List<Executable> cases= new ArrayList<>();
		for (Path source : paths) {
			String name= fileName(source);
			if (!(DESCRIPTOR_NAMES.contains(name) || name.endsWith(".exsd") || name.endsWith(".xsd"))) { //$NON-NLS-1$ //$NON-NLS-2$
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
			IProject project= workspaceProject(family, owner, projects);
			Path projectRelative= owner.relativize(source);
			IFile copy= project.getFile(projectRelative.toString());
			String projectPath= repository.relativize(owner).toString();
			if (!scope.isPDERelevantFile(copy)) {
				inventory.add(relative + "\t" + projectPath + "\t-\t-\t-\tfalse\tOUTSIDE_PDE_SCOPE"); //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}
			counts.eligibleFiles++;
			byte[] original= Files.readAllBytes(source);
			createFile(copy, original, resourceCharset(owner, projectRelative));
			write(output.resolve("original").resolve(relative), original); //$NON-NLS-1$
			addOptionCases(new CorpusResource(copy, source, relative, projectPath, original), output, inventory, counts, cases);
		}
		return cases;
	}

	private IProject workspaceProject(String family, Path owner, Map<Path, IProject> projects) throws Exception {
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
		return project;
	}

	private void addOptionCases(CorpusResource resource, Path output, List<String> inventory, CorpusCounts counts,
			List<Executable> cases) {
		DocumentationPolicy[] policies= DocumentationPolicy.values();
		for (PdeSchemaTarget target : PdeSchemaTarget.values()) {
			for (DocumentationPolicy documentation : policies) {
				for (boolean indent : INDENT_OPTIONS) {
					cases.add(() -> verifyFile(resource, output, target, documentation, indent, inventory, counts));
				}
			}
		}
	}

	private void verifyFile(CorpusResource resource, Path output, PdeSchemaTarget target,
			DocumentationPolicy documentation, boolean indent, List<String> inventory, CorpusCounts counts) throws Throwable {
		IFile copy= resource.copy();
		Path source= resource.source();
		String relative= resource.relative();
		byte[] original= resource.original();
		String mode= target.name() + "-" + documentation.name() + "-indent-" + indent; //$NON-NLS-1$ //$NON-NLS-2$
		Path evidence= output.resolve(mode).resolve(relative);
		boolean changed= false;
		String status= "FAIL"; //$NON-NLS-1$
		counts.executedCases++;
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
				counts.changedCases++;
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
			inventory.add(relative + "\t" + resource.project() + "\t" + mode + "\t" + sha(original) + "\t" + sha(after) //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					+ "\t" + changed + "\t" + status); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

	private static String resourceCharset(Path project, Path relative) throws Exception {
		Properties encodings= new Properties();
		Path preferences= project.resolve(".settings/org.eclipse.core.resources.prefs"); //$NON-NLS-1$
		if (Files.isRegularFile(preferences)) {
			try (var reader= Files.newBufferedReader(preferences, StandardCharsets.UTF_8)) {
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
		Files.createDirectories(Objects.requireNonNull(path.getParent(), "Evidence path must have a parent: " + path)); //$NON-NLS-1$
		Files.write(path, bytes);
	}

	private static String fileName(Path path) {
		return Objects.requireNonNull(path.getFileName(), "Corpus file path must have a name: " + path).toString(); //$NON-NLS-1$
	}

	private static String sha(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); //$NON-NLS-1$
	}
}
