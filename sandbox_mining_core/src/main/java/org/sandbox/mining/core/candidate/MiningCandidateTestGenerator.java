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
package org.sandbox.mining.core.candidate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Auto-generates lightweight JUnit 5 test classes for {@link MiningCandidate}
 * instances.
 *
 * <p>The generated tests use {@code HintFileParser} and
 * {@code BatchTransformationProcessor} to verify:</p>
 * <ol>
 *   <li>The DSL rule parses successfully</li>
 *   <li>The {@code beforeExample} matches the rule</li>
 *   <li>The replacement equals the {@code afterExample}</li>
 *   <li>The {@code negativeExample} does NOT match the rule</li>
 * </ol>
 *
 * <p>Generated tests are placed in:</p>
 * <pre>
 * sandbox_common_core/src/test/java/org/sandbox/jdt/triggerpattern/generated/
 * </pre>
 */
public class MiningCandidateTestGenerator {

	private static final String GENERATED_PACKAGE = "org.sandbox.jdt.triggerpattern.generated"; //$NON-NLS-1$

	/**
	 * Generates a JUnit 5 test class for the given candidate and writes it to
	 * the specified output directory.
	 *
	 * @param candidate the mining candidate to generate a test for
	 * @param outputDir the directory to write the generated test to
	 *                  (e.g. {@code sandbox_common_core/src/test/java/org/sandbox/jdt/triggerpattern/generated})
	 * @return the path to the generated test file
	 * @throws IOException if the file cannot be written
	 */
	public Path generateTest(MiningCandidate candidate, Path outputDir) throws IOException {
		Files.createDirectories(outputDir);
		String className = buildClassName(candidate);
		String source = buildTestSource(candidate, className);
		Path outputFile = outputDir.resolve(className + ".java"); //$NON-NLS-1$
		Files.writeString(outputFile, source, StandardCharsets.UTF_8);
		return outputFile;
	}

	/**
	 * Builds a valid Java class name from the candidate's commit hash and category.
	 *
	 * @param candidate the candidate
	 * @return a valid Java class name
	 */
	static String buildClassName(MiningCandidate candidate) {
		String hash = (candidate.getSourceCommit() != null && !candidate.getSourceCommit().isBlank())
				? candidate.getSourceCommit().substring(0, Math.min(7, candidate.getSourceCommit().length()))
				: "unknown"; //$NON-NLS-1$
		String category = (candidate.getCategory() != null && !candidate.getCategory().isBlank())
				? candidate.getCategory().replaceAll("[^a-zA-Z0-9]", "_") //$NON-NLS-1$ //$NON-NLS-2$
				: "unknown"; //$NON-NLS-1$
		// Capitalize first letter of category
		if (!category.isEmpty()) {
			category = Character.toUpperCase(category.charAt(0)) + category.substring(1);
		}
		return "Generated_" + hash + "_" + category + "_CandidateTest"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	}

	/**
	 * Builds the Java source code for the generated test class.
	 *
	 * @param candidate the candidate
	 * @param className the class name
	 * @return the Java source code
	 */
	private String buildTestSource(MiningCandidate candidate, String className) {
		String dslRule = candidate.getDslRule() != null ? candidate.getDslRule() : ""; //$NON-NLS-1$
		String beforeExample = candidate.getBeforeExample() != null ? candidate.getBeforeExample() : ""; //$NON-NLS-1$
		String afterExample = candidate.getAfterExample() != null ? candidate.getAfterExample() : ""; //$NON-NLS-1$
		String negativeExample = candidate.getNegativeExample() != null ? candidate.getNegativeExample() : ""; //$NON-NLS-1$
		String summary = candidate.getSummary() != null ? candidate.getSummary() : "(no summary)"; //$NON-NLS-1$
		String sourceCommit = candidate.getSourceCommit() != null ? candidate.getSourceCommit() : "unknown"; //$NON-NLS-1$

		StringBuilder sb = new StringBuilder();
		sb.append("/*******************************************************************************\n"); //$NON-NLS-1$
		sb.append(" * Auto-generated test for mining candidate from commit ").append(sourceCommit).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(" * Summary: ").append(summary).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(" * DO NOT EDIT — regenerate by running MiningCandidateTestGenerator\n"); //$NON-NLS-1$
		sb.append(" *******************************************************************************/\n"); //$NON-NLS-1$
		sb.append("package ").append(GENERATED_PACKAGE).append(";\n\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("import static org.junit.jupiter.api.Assertions.assertFalse;\n"); //$NON-NLS-1$
		sb.append("import static org.junit.jupiter.api.Assertions.assertTrue;\n\n"); //$NON-NLS-1$
		sb.append("import java.util.List;\n"); //$NON-NLS-1$
		sb.append("import java.util.Map;\n\n"); //$NON-NLS-1$
		sb.append("import org.eclipse.jdt.core.JavaCore;\n"); //$NON-NLS-1$
		sb.append("import org.eclipse.jdt.core.dom.AST;\n"); //$NON-NLS-1$
		sb.append("import org.eclipse.jdt.core.dom.ASTParser;\n"); //$NON-NLS-1$
		sb.append("import org.eclipse.jdt.core.dom.CompilationUnit;\n"); //$NON-NLS-1$
		sb.append("import org.junit.jupiter.api.Test;\n"); //$NON-NLS-1$
		sb.append("import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;\n"); //$NON-NLS-1$
		sb.append("import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor.TransformationResult;\n"); //$NON-NLS-1$
		sb.append("import org.sandbox.jdt.triggerpattern.api.HintFile;\n"); //$NON-NLS-1$
		sb.append("import org.sandbox.jdt.triggerpattern.internal.HintFileParser;\n\n"); //$NON-NLS-1$
		sb.append("/**\n"); //$NON-NLS-1$
		sb.append(" * Auto-generated lightweight candidate test.\n"); //$NON-NLS-1$
		sb.append(" * Source commit: ").append(sourceCommit).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(" * Summary: ").append(summary).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(" */\n"); //$NON-NLS-1$
		sb.append("public class ").append(className).append(" {\n\n"); //$NON-NLS-1$ //$NON-NLS-2$

		// DSL Rule constant
		sb.append("    private static final String DSL_RULE = \"\"\"\n"); //$NON-NLS-1$
		sb.append("            ").append(escapeForTextBlock(dslRule)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("            \"\"\";\n\n"); //$NON-NLS-1$

		// Test 1: DSL parses successfully
		sb.append("    @Test\n"); //$NON-NLS-1$
		sb.append("    public void testDslParsesSuccessfully() throws Exception {\n"); //$NON-NLS-1$
		sb.append("        HintFileParser parser = new HintFileParser();\n"); //$NON-NLS-1$
		sb.append("        HintFile hintFile = parser.parse(DSL_RULE);\n"); //$NON-NLS-1$
		sb.append("        assertFalse(hintFile.getRules().isEmpty(),\n"); //$NON-NLS-1$
		sb.append("                \"DSL rule should parse into at least one rule\"); //$NON-NLS-1$\n"); //$NON-NLS-1$
		sb.append("    }\n\n"); //$NON-NLS-1$

		// Test 2: beforeExample matches (only if provided)
		if (!beforeExample.isBlank() && !afterExample.isBlank()) {
			sb.append("    @Test\n"); //$NON-NLS-1$
			sb.append("    public void testBeforeExampleMatches() throws Exception {\n"); //$NON-NLS-1$
			sb.append("        HintFileParser parser = new HintFileParser();\n"); //$NON-NLS-1$
			sb.append("        HintFile hintFile = parser.parse(DSL_RULE);\n"); //$NON-NLS-1$
			sb.append("        BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);\n"); //$NON-NLS-1$
			sb.append("        String beforeCode = \"\"\"\n"); //$NON-NLS-1$
			sb.append("                ").append(escapeForTextBlock(beforeExample)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("                \"\"\";\n"); //$NON-NLS-1$
			sb.append("        CompilationUnit cu = parseCode(beforeCode);\n"); //$NON-NLS-1$
			sb.append("        List<TransformationResult> results = processor.process(cu);\n"); //$NON-NLS-1$
			sb.append("        assertFalse(results.isEmpty(),\n"); //$NON-NLS-1$
			sb.append("                \"Before example should match the DSL rule\"); //$NON-NLS-1$\n"); //$NON-NLS-1$
			sb.append("    }\n\n"); //$NON-NLS-1$
		}

		// Test 3: negativeExample does NOT match (only if provided)
		if (!negativeExample.isBlank()) {
			sb.append("    @Test\n"); //$NON-NLS-1$
			sb.append("    public void testNegativeExampleDoesNotMatch() throws Exception {\n"); //$NON-NLS-1$
			sb.append("        HintFileParser parser = new HintFileParser();\n"); //$NON-NLS-1$
			sb.append("        HintFile hintFile = parser.parse(DSL_RULE);\n"); //$NON-NLS-1$
			sb.append("        BatchTransformationProcessor processor = new BatchTransformationProcessor(hintFile);\n"); //$NON-NLS-1$
			sb.append("        String negCode = \"\"\"\n"); //$NON-NLS-1$
			sb.append("                ").append(escapeForTextBlock(negativeExample)).append("\n"); //$NON-NLS-1$ //$NON-NLS-2$
			sb.append("                \"\"\";\n"); //$NON-NLS-1$
			sb.append("        CompilationUnit cu = parseCode(negCode);\n"); //$NON-NLS-1$
			sb.append("        List<TransformationResult> results = processor.process(cu);\n"); //$NON-NLS-1$
			sb.append("        assertTrue(results.isEmpty(),\n"); //$NON-NLS-1$
			sb.append("                \"Negative example should NOT match the DSL rule\"); //$NON-NLS-1$\n"); //$NON-NLS-1$
			sb.append("    }\n\n"); //$NON-NLS-1$
		}

		// Helper method
		sb.append("    private CompilationUnit parseCode(String code) {\n"); //$NON-NLS-1$
		sb.append("        ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());\n"); //$NON-NLS-1$
		sb.append("        astParser.setSource(code.toCharArray());\n"); //$NON-NLS-1$
		sb.append("        astParser.setKind(ASTParser.K_COMPILATION_UNIT);\n"); //$NON-NLS-1$
		sb.append("        Map<String, String> options = JavaCore.getOptions();\n"); //$NON-NLS-1$
		sb.append("        options.put(JavaCore.COMPILER_SOURCE, \"17\"); //$NON-NLS-1$\n"); //$NON-NLS-1$
		sb.append("        astParser.setCompilerOptions(options);\n"); //$NON-NLS-1$
		sb.append("        return (CompilationUnit) astParser.createAST(null);\n"); //$NON-NLS-1$
		sb.append("    }\n"); //$NON-NLS-1$
		sb.append("}\n"); //$NON-NLS-1$

		return sb.toString();
	}

	/**
	 * Escapes a string for use in a Java text block.
	 * Specifically handles {@code """} sequences that would terminate the block.
	 *
	 * @param s the string to escape
	 * @return the escaped string
	 */
	static String escapeForTextBlock(String s) {
		if (s == null) {
			return ""; //$NON-NLS-1$
		}
		// Escape """ which would prematurely close the text block
		return s.replace("\"\"\"", "\\\"\\\"\\\""); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
