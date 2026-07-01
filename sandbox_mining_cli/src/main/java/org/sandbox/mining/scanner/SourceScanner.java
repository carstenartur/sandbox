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
 ******************************************************************************/
package org.sandbox.mining.scanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.mining.report.MiningReport;

/**
 * Scans directories for Java source files and runs transformation rules against them.
 */
public class SourceScanner {

	private static final Map<String, String> NO_COMPILER_OPTIONS = Map.of();

	private final StandaloneAstParser parser;
	private final int maxFiles;

	public SourceScanner() {
		this(new StandaloneAstParser(), 5000);
	}

	public SourceScanner(StandaloneAstParser parser, int maxFiles) {
		this.parser = parser;
		this.maxFiles = maxFiles;
	}

	/**
	 * Finds all Java files in the given directory tree.
	 *
	 * @param rootDir  the root directory to scan
	 * @param subPaths optional sub-paths to restrict scanning to; if empty, scans
	 *                 all
	 * @return list of Java file paths
	 * @throws IOException if directory traversal fails
	 */
	public List<Path> findJavaFiles(Path rootDir, List<String> subPaths) throws IOException {
		List<Path> javaFiles = new ArrayList<>();

		List<Path> searchRoots = new ArrayList<>();
		if (subPaths == null || subPaths.isEmpty()) {
			searchRoots.add(rootDir);
		} else {
			for (String sub : subPaths) {
				Path subPath = rootDir.resolve(sub);
				if (Files.isDirectory(subPath)) {
					searchRoots.add(subPath);
				}
			}
		}

		for (Path searchRoot : searchRoots) {
			if (!Files.isDirectory(searchRoot)) {
				continue;
			}
			Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (javaFiles.size() >= maxFiles) {
						return FileVisitResult.TERMINATE;
					}
					if (file.toString().endsWith(".java")) { //$NON-NLS-1$
						javaFiles.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) {
					return FileVisitResult.CONTINUE;
				}
			});
		}

		return javaFiles;
	}

	/**
	 * Scans all Java files using the given hint files and produces a mining report.
	 *
	 * @param repoName  name of the repository being scanned
	 * @param rootDir   the root directory of the cloned repository
	 * @param subPaths  optional sub-paths to restrict scanning to
	 * @param hintFiles list of parsed hint files to apply
	 * @return the mining report with all matches
	 * @throws IOException if file reading fails
	 */
	public MiningReport scan(String repoName, Path rootDir, List<String> subPaths, List<HintFile> hintFiles)
			throws IOException {
		return scan(repoName, rootDir, subPaths, hintFiles, NO_COMPILER_OPTIONS);
	}

	/**
	 * Scans all Java files using the given hint files and compiler options, producing
	 * a mining report. Compiler options are forwarded to guard evaluation so rules
	 * such as {@code sourceVersionGE(11)} behave like they do in Eclipse cleanup
	 * execution.
	 *
	 * @param repoName        name of the repository being scanned
	 * @param rootDir         the root directory of the cloned repository
	 * @param subPaths        optional sub-paths to restrict scanning to
	 * @param hintFiles       list of parsed hint files to apply
	 * @param compilerOptions compiler options for guard evaluation
	 * @return the mining report with all matches
	 * @throws IOException if file reading fails
	 */
	public MiningReport scan(String repoName, Path rootDir, List<String> subPaths, List<HintFile> hintFiles,
			Map<String, String> compilerOptions) throws IOException {
		MiningReport report = new MiningReport();
		Map<String, String> effectiveCompilerOptions = compilerOptions != null ? compilerOptions : NO_COMPILER_OPTIONS;
		List<Path> javaFiles = findJavaFiles(rootDir, subPaths);
		report.addFileCount(repoName, javaFiles.size());

		for (HintFile hintFile : hintFiles) {
			BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);

			for (Path javaFile : javaFiles) {
				String source = Files.readString(javaFile, StandardCharsets.UTF_8);
				CompilationUnit cu = parser.parse(source);
				List<TransformationResult> results = processor.process(cu, effectiveCompilerOptions);

				for (TransformationResult result : results) {
					String relativePath = rootDir.relativize(javaFile).toString();
					int line = cu.getLineNumber(result.match().getOffset());
					String hintFileName = hintFile.getId() != null ? hintFile.getId() : "unknown"; //$NON-NLS-1$
					TransformationRule rule = result.rule();
					String ruleName = rule.getRuleId();
					if (ruleName == null || ruleName.isBlank()) {
						ruleName = rule.getDescription();
					}
					if (ruleName == null || ruleName.isBlank()) {
						int ruleIndex = hintFile.getRules().indexOf(rule);
						ruleName = ruleIndex >= 0 ? hintFileName + (ruleIndex + 1) : hintFileName;
					}
					report.addMatch(repoName, hintFileName, ruleName, relativePath, line, result.matchedText(),
							result.hasReplacement() ? result.replacement() : null);
				}
			}
		}

		return report;
	}
}
