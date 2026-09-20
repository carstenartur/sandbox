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
package org.sandbox.jdt.triggerpattern.git;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class UnifiedDiffFormatterTest {

	@TempDir
	Path tempDir;

	@Test
	void formatsPatchThatGitCanCheckAndApply() throws Exception {
		Path repoDir= tempDir.resolve("cleanup-project"); //$NON-NLS-1$
		Files.createDirectories(repoDir);
		exec(repoDir, "git", "init", "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		String spacedPath= "src/File With Spaces.java"; //$NON-NLS-1$
		byte[] spacedBefore= String.join("\n", //$NON-NLS-1$
				"package demo;", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"class FileWithSpaces {", //$NON-NLS-1$
				"\tString greeting() {", //$NON-NLS-1$
				"\t\treturn \"Grüße €\";", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"\tint untouched() {", //$NON-NLS-1$
				"\t\treturn 1;", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"\tString second() {", //$NON-NLS-1$
				"\t\treturn \"before\";", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"}", //$NON-NLS-1$
				"").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] spacedAfter= String.join("\n", //$NON-NLS-1$
				"package demo;", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"import java.nio.charset.StandardCharsets;", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"class FileWithSpaces {", //$NON-NLS-1$
				"\tString greeting() {", //$NON-NLS-1$
				"\t\treturn new String(\"Grüße €\".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"\tint untouched() {", //$NON-NLS-1$
				"\t\treturn 1;", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"", //$NON-NLS-1$
				"\tString second() {", //$NON-NLS-1$
				"\t\treturn \"after\";", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"}", //$NON-NLS-1$
				"").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		String tabPath= "src/File\tWithTab.java"; //$NON-NLS-1$
		byte[] tabBefore= "class FileWithTab {\n\tString value() {\n\t\treturn \"before\";\n\t}\n}\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);
		byte[] tabAfter= "class FileWithTab {\n\tString value() {\n\t\treturn \"after\";\n\t}\n}\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);

		String newlinePath= "src/File\nWithNewline.java"; //$NON-NLS-1$
		byte[] newlineBefore= "class FileWithNewline {\n\tint value() {\n\t\treturn 1;\n\t}\n}\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);
		byte[] newlineAfter= "class FileWithNewline {\n\tint value() {\n\t\treturn 2;\n\t}\n}\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);

		String deletionPath= "src/DeleteOnly.java"; //$NON-NLS-1$
		byte[] deletionBefore= String.join("\n", //$NON-NLS-1$
				"class DeleteOnly {", //$NON-NLS-1$
				"\tvoid run() {", //$NON-NLS-1$
				"\t\tkeep();", //$NON-NLS-1$
				"\t\tremoveOne();", //$NON-NLS-1$
				"\t\tremoveTwo();", //$NON-NLS-1$
				"\t\tkeepAgain();", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"}", //$NON-NLS-1$
				"").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$
		byte[] deletionAfter= String.join("\n", //$NON-NLS-1$
				"class DeleteOnly {", //$NON-NLS-1$
				"\tvoid run() {", //$NON-NLS-1$
				"\t\tkeep();", //$NON-NLS-1$
				"\t\tkeepAgain();", //$NON-NLS-1$
				"\t}", //$NON-NLS-1$
				"}", //$NON-NLS-1$
				"").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		String crlfPath= "src/windows/CrLfSample.java"; //$NON-NLS-1$
		byte[] crlfBefore= "class CrLfSample {\r\n\tString value() {\r\n\t\treturn \"before\";\r\n\t}\r\n}\r\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);
		byte[] crlfAfter= "class CrLfSample {\r\n\tString value() {\r\n\t\treturn \"after\";\r\n\t}\r\n}\r\n" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);

		String noNewlinePath= "src/NoNewline.java"; //$NON-NLS-1$
		byte[] noNewlineBefore= "class NoNewline {\n\tString value() {\n\t\treturn \"before\";\n\t}\n}" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);
		byte[] noNewlineAfter= "class NoNewline {\n\tString value() {\n\t\treturn \"after\";\n\t}\n}" //$NON-NLS-1$
				.getBytes(StandardCharsets.UTF_8);

		write(repoDir.resolve(spacedPath), spacedBefore);
		write(repoDir.resolve(tabPath), tabBefore);
		write(repoDir.resolve(newlinePath), newlineBefore);
		write(repoDir.resolve(deletionPath), deletionBefore);
		write(repoDir.resolve(crlfPath), crlfBefore);
		write(repoDir.resolve(noNewlinePath), noNewlineBefore);

		ByteArrayOutputStream patch= new ByteArrayOutputStream();
		patch.writeBytes(UnifiedDiffFormatter.format(spacedPath, spacedBefore, spacedAfter));
		patch.writeBytes(UnifiedDiffFormatter.format(tabPath, tabBefore, tabAfter));
		patch.writeBytes(UnifiedDiffFormatter.format(newlinePath, newlineBefore, newlineAfter));
		patch.writeBytes(UnifiedDiffFormatter.format(deletionPath, deletionBefore, deletionAfter));
		patch.writeBytes(UnifiedDiffFormatter.format(crlfPath, crlfBefore, crlfAfter));
		patch.writeBytes(UnifiedDiffFormatter.format(noNewlinePath, noNewlineBefore, noNewlineAfter));
		byte[] patchBytes= patch.toByteArray();
		String patchText= new String(patchBytes, StandardCharsets.ISO_8859_1);
		assertTrue(patchText.contains("\\ No newline at end of file")); //$NON-NLS-1$
		assertTrue(patchText.contains("\\tWithTab.java")); //$NON-NLS-1$
		assertTrue(patchText.contains("\\nWithNewline.java")); //$NON-NLS-1$

		Path patchFile= repoDir.resolve("cleanup.patch"); //$NON-NLS-1$
		Files.write(patchFile, patchBytes);

		assertArrayEquals(spacedBefore, Files.readAllBytes(repoDir.resolve(spacedPath)));
		assertArrayEquals(tabBefore, Files.readAllBytes(repoDir.resolve(tabPath)));
		assertArrayEquals(newlineBefore, Files.readAllBytes(repoDir.resolve(newlinePath)));
		assertArrayEquals(deletionBefore, Files.readAllBytes(repoDir.resolve(deletionPath)));
		assertArrayEquals(crlfBefore, Files.readAllBytes(repoDir.resolve(crlfPath)));
		assertArrayEquals(noNewlineBefore, Files.readAllBytes(repoDir.resolve(noNewlinePath)));

		String patchName= Objects.requireNonNull(patchFile.getFileName()).toString();
		exec(repoDir, "git", "apply", "--numstat", "--check", patchName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		exec(repoDir, "git", "apply", "--check", patchName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		exec(repoDir, "git", "apply", patchName); //$NON-NLS-1$ //$NON-NLS-2$

		assertArrayEquals(spacedAfter, Files.readAllBytes(repoDir.resolve(spacedPath)));
		assertArrayEquals(tabAfter, Files.readAllBytes(repoDir.resolve(tabPath)));
		assertArrayEquals(newlineAfter, Files.readAllBytes(repoDir.resolve(newlinePath)));
		assertArrayEquals(deletionAfter, Files.readAllBytes(repoDir.resolve(deletionPath)));
		assertArrayEquals(crlfAfter, Files.readAllBytes(repoDir.resolve(crlfPath)));
		assertArrayEquals(noNewlineAfter, Files.readAllBytes(repoDir.resolve(noNewlinePath)));
	}

	@Test
	void preservesNonUtf8BytesInPatchOutput() throws Exception {
		Path repoDir= tempDir.resolve("latin1-project"); //$NON-NLS-1$
		Files.createDirectories(repoDir);
		exec(repoDir, "git", "init", "."); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

		String latin1Path= "src/Latin1.java"; //$NON-NLS-1$
		byte[] before= "class A {String s=\"Grüße\";}\n".getBytes(StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		byte[] after= "class B {String s=\"Grüße\";}\n".getBytes(StandardCharsets.ISO_8859_1); //$NON-NLS-1$
		write(repoDir.resolve(latin1Path), before);

		Path patchFile= repoDir.resolve("latin1.patch"); //$NON-NLS-1$
		Files.write(patchFile, UnifiedDiffFormatter.format(latin1Path, before, after));

		String patchName= Objects.requireNonNull(patchFile.getFileName()).toString();
		exec(repoDir, "git", "apply", "--check", patchName); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		exec(repoDir, "git", "apply", patchName); //$NON-NLS-1$ //$NON-NLS-2$

		assertArrayEquals(after, Files.readAllBytes(repoDir.resolve(latin1Path)));
	}

	@Test
	void returnsEmptyPatchForUnchangedContent() throws Exception {
		byte[] original= "class Same {}\n".getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$

		assertEquals(0, UnifiedDiffFormatter.format("src/Same.java", original, original).length); //$NON-NLS-1$
	}

	private static void write(Path path, byte[] content) throws IOException {
		Files.createDirectories(Objects.requireNonNull(path.getParent()));
		Files.write(path, content);
	}

	private static void exec(Path workDir, String... command) throws IOException, InterruptedException {
		ProcessBuilder builder= new ProcessBuilder(command);
		builder.directory(workDir.toFile());
		builder.redirectErrorStream(true);
		Process process= builder.start();
		byte[] output= process.getInputStream().readAllBytes();
		assertEquals(0, process.waitFor(), new String(output, StandardCharsets.UTF_8));
	}
}
