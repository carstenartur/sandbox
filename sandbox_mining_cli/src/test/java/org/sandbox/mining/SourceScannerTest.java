/*******************************************************************************
 * Copyright (c) 2025 Carsten Hammer.
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
package org.sandbox.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.mining.scanner.SourceScanner;
import org.sandbox.mining.scanner.StandaloneAstParser;

class SourceScannerTest {

	@TempDir
	Path tempDir;

	@Test
	void testFindJavaFiles() throws IOException {
		// Create test Java files
		Path srcDir = tempDir.resolve("src/main/java");
		Files.createDirectories(srcDir);
		Files.writeString(srcDir.resolve("Foo.java"), "class Foo {}", StandardCharsets.UTF_8);
		Files.writeString(srcDir.resolve("Bar.java"), "class Bar {}", StandardCharsets.UTF_8);

		// Also create a non-Java file that should be ignored
		Files.writeString(srcDir.resolve("readme.txt"), "not java", StandardCharsets.UTF_8);

		SourceScanner scanner = new SourceScanner();
		List<Path> javaFiles = scanner.findJavaFiles(tempDir, List.of());
		assertEquals(2, javaFiles.size());
		assertTrue(javaFiles.stream().allMatch(p -> p.toString().endsWith(".java")));
	}

	@Test
	void testFindJavaFilesWithSubPaths() throws IOException {
		Path srcDir = tempDir.resolve("src/main/java");
		Path otherDir = tempDir.resolve("other");
		Files.createDirectories(srcDir);
		Files.createDirectories(otherDir);
		Files.writeString(srcDir.resolve("Foo.java"), "class Foo {}", StandardCharsets.UTF_8);
		Files.writeString(otherDir.resolve("Bar.java"), "class Bar {}", StandardCharsets.UTF_8);

		SourceScanner scanner = new SourceScanner();
		List<Path> javaFiles = scanner.findJavaFiles(tempDir, List.of("src/main/java"));
		assertEquals(1, javaFiles.size());
		assertTrue(javaFiles.get(0).toString().endsWith("Foo.java"));
	}

	@Test
	void testFindJavaFilesRespectsLimit() throws IOException {
		Path srcDir = tempDir.resolve("src");
		Files.createDirectories(srcDir);
		for (int i = 0; i < 10; i++) {
			Files.writeString(srcDir.resolve("File" + i + ".java"), "class File" + i + " {}", StandardCharsets.UTF_8);
		}

		SourceScanner scanner = new SourceScanner(new StandaloneAstParser(), 5);
		List<Path> javaFiles = scanner.findJavaFiles(tempDir, List.of());
		assertEquals(5, javaFiles.size());
	}

	@Test
	void testStandaloneAstParser() {
		StandaloneAstParser parser = new StandaloneAstParser();
		var cu = parser.parse("class Foo { void bar() {} }");
		assertNotNull(cu);
		assertEquals(0, cu.getProblems().length);
	}

	@Test
	void testMarkdownReporter() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("test-repo", 100);
		report.addMatch("test-repo", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var reporter = new org.sandbox.mining.report.MarkdownReporter();
		String markdown = reporter.generate(report);

		assertTrue(markdown.contains("# Refactoring Mining Report"));
		assertTrue(markdown.contains("test-repo"));
		assertTrue(markdown.contains("100"));
		assertTrue(markdown.contains("Foo.java:10"));
	}

	@Test
	void testJsonReporter() {
		var report = new org.sandbox.mining.report.MiningReport();
		report.addFileCount("test-repo", 100);
		report.addMatch("test-repo", "collections", "use-isEmpty", "Foo.java", 10, "x.size() == 0", "x.isEmpty()");

		var reporter = new org.sandbox.mining.report.JsonReporter();
		String json = reporter.generate(report);

		assertTrue(json.contains("\"totalMatches\": 1"));
		assertTrue(json.contains("\"test-repo\""));
		assertTrue(json.contains("\"Foo.java\""));
		assertTrue(json.contains("\"line\": 10"));
	}

	@Test
	void testReportMerge() {
		var report1 = new org.sandbox.mining.report.MiningReport();
		report1.addFileCount("repo1", 10);
		report1.addMatch("repo1", "h1", "r1", "A.java", 1, "code1", null);

		var report2 = new org.sandbox.mining.report.MiningReport();
		report2.addFileCount("repo2", 20);
		report2.addMatch("repo2", "h2", "r2", "B.java", 2, "code2", "replacement");

		report1.merge(report2);

		assertEquals(2, report1.getMatches().size());
		assertEquals(10, report1.getFileCounts().get("repo1"));
		assertEquals(20, report1.getFileCounts().get("repo2"));
		assertFalse(report1.getMatchesByRepo().isEmpty());
	}
}
