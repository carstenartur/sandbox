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
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.compiler.IProblem;

import org.sandbox.jdt.triggerpattern.api.EmbeddedJavaBlock;

/**
 * Compiles embedded Java code blocks from {@code .sandbox-hint} files.
 *
 * <p>Wraps the raw Java source from {@code <? ?>} blocks into a synthetic
 * compilation unit and uses JDT's {@link ASTParser} to parse and validate it.
 * The resulting AST can be inspected for method declarations that serve as
 * custom guard or fix functions.</p>
 *
 * <p>The synthetic class wraps the embedded code as follows:</p>
 * <pre>
 * package org.sandbox.generated;
 * import org.eclipse.jdt.core.dom.*;
 * public class HintCode_&lt;ruleId&gt; {
 *     // embedded code here
 * }
 * </pre>
 *
 * @since 1.5.0
 */
public final class EmbeddedJavaCompiler {

	private static final Logger LOGGER = Logger.getLogger(EmbeddedJavaCompiler.class.getName());

	private static final String SYNTHETIC_PACKAGE = "org.sandbox.generated"; //$NON-NLS-1$
	private static final String CLASS_PREFIX = "HintCode_"; //$NON-NLS-1$

	/**
	 * A source-to-line mapping entry that maps a line in the hint file to
	 * the corresponding line in the synthetic generated class.
	 *
	 * <p>Used by the debugger to set breakpoints and map stack frames.</p>
	 *
	 * @param hintFileLine     the 1-based line number in the {@code .sandbox-hint} file
	 * @param syntheticClassLine the 1-based line number in the generated synthetic class
	 * @since 1.5.0
	 */
	public record SourceLineMapping(int hintFileLine, int syntheticClassLine) {
	}

	/**
	 * Result of compiling an embedded Java block.
	 *
	 * @param compilationUnit the parsed AST compilation unit
	 * @param problems        compilation problems (errors and warnings)
	 * @param guardMethods    method declarations that match the guard function signature
	 * @param lineOffset      the line offset to map synthetic class lines back to hint file lines
	 * @param syntheticClassName the fully qualified name of the generated synthetic class
	 * @param lineMappings    source line mappings from hint file to synthetic class
	 * @param syntheticHeaderLength the character length of the synthetic header before embedded code
	 */
	public record CompilationResult(
			CompilationUnit compilationUnit,
			List<IProblem> problems,
			List<MethodDeclaration> guardMethods,
			int lineOffset,
			String syntheticClassName,
			List<SourceLineMapping> lineMappings,
			int syntheticHeaderLength) {

		/**
		 * Backwards-compatible constructor without syntheticClassName, lineMappings, and syntheticHeaderLength.
		 */
		public CompilationResult(
				CompilationUnit compilationUnit,
				List<IProblem> problems,
				List<MethodDeclaration> guardMethods,
				int lineOffset) {
			this(compilationUnit, problems, guardMethods, lineOffset,
					"", Collections.emptyList(), 0); //$NON-NLS-1$
		}

		/**
		 * Returns {@code true} if compilation produced no errors.
		 *
		 * @return {@code true} if error-free
		 */
		public boolean hasErrors() {
			return problems.stream().anyMatch(IProblem::isError);
		}

		/**
		 * Maps a hint file line to the corresponding synthetic class line.
		 *
		 * @param hintLine the 1-based line in the hint file
		 * @return the synthetic class line, or -1 if not mapped
		 * @since 1.5.0
		 */
		public int toSyntheticLine(int hintLine) {
			for (SourceLineMapping mapping : lineMappings) {
				if (mapping.hintFileLine() == hintLine) {
					return mapping.syntheticClassLine();
				}
			}
			return -1;
		}

		/**
		 * Maps a synthetic class line to the corresponding hint file line.
		 *
		 * @param syntheticLine the 1-based line in the synthetic class
		 * @return the hint file line, or -1 if not mapped
		 * @since 1.5.0
		 */
		public int toHintLine(int syntheticLine) {
			for (SourceLineMapping mapping : lineMappings) {
				if (mapping.syntheticClassLine() == syntheticLine) {
					return mapping.hintFileLine();
				}
			}
			return -1;
		}
	}

	private EmbeddedJavaCompiler() {
		// utility class
	}

	/**
	 * Compiles an embedded Java block by wrapping it into a synthetic class
	 * and parsing it with JDT's ASTParser.
	 *
	 * @param block  the embedded Java block to compile
	 * @param ruleId the hint file rule ID (used for the synthetic class name);
	 *               may be {@code null}, in which case a default name is used
	 * @return the compilation result
	 */
	public static CompilationResult compile(EmbeddedJavaBlock block, String ruleId) {
		String className = CLASS_PREFIX + sanitizeIdentifier(ruleId);
		String syntheticSource = buildSyntheticSource(className, block.getSource());

		// The number of lines added before the embedded code starts
		int headerLineCount = countLines(syntheticSource.substring(0,
				syntheticSource.indexOf(block.getSource().isEmpty() ? "}" : block.getSource()))); //$NON-NLS-1$

		CompilationUnit cu = parseSource(syntheticSource);

		List<IProblem> problems = collectProblems(cu);
		List<MethodDeclaration> guardMethods = extractGuardMethods(cu);

		int lineOffset = block.getStartLine() - headerLineCount - 1;

		// Build source line mappings for debugging support
		String syntheticClassName = SYNTHETIC_PACKAGE + "." + className; //$NON-NLS-1$
		List<SourceLineMapping> lineMappings = buildLineMappings(block, headerLineCount);
		int headerCharLength = computeSyntheticHeaderLength(ruleId);

		LOGGER.log(Level.FINE, "Compiled embedded Java block: {0} problems, {1} guard methods", //$NON-NLS-1$
				new Object[] { problems.size(), guardMethods.size() });

		return new CompilationResult(cu, problems, guardMethods, lineOffset,
				syntheticClassName, lineMappings, headerCharLength);
	}

	/**
	 * Builds the synthetic Java source by wrapping the embedded code in a class.
	 */
	static String buildSyntheticSource(String className, String embeddedCode) {
		StringBuilder sb = new StringBuilder();
		sb.append("package ").append(SYNTHETIC_PACKAGE).append(";\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append("\n"); //$NON-NLS-1$
		sb.append("public class ").append(className).append(" {\n"); //$NON-NLS-1$ //$NON-NLS-2$
		sb.append(embeddedCode);
		if (!embeddedCode.endsWith("\n")) { //$NON-NLS-1$
			sb.append("\n"); //$NON-NLS-1$
		}
		sb.append("}\n"); //$NON-NLS-1$
		return sb.toString();
	}

	/**
	 * Returns the character length of the synthetic class header that is
	 * prepended before the embedded code. Useful for mapping character
	 * positions from the synthetic source back to the original embedded code.
	 *
	 * @param ruleId the hint file rule ID (used to derive the class name)
	 * @return the header length in characters
	 * @since 1.5.0
	 */
	public static int computeSyntheticHeaderLength(String ruleId) {
		String className = CLASS_PREFIX + sanitizeIdentifier(ruleId);
		String header = "package " + SYNTHETIC_PACKAGE + ";\n\npublic class " + className + " {\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		return header.length();
	}

	/**
	 * Parses Java source using JDT's ASTParser.
	 */
	private static CompilationUnit parseSource(String source) {
		ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());

		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_COMPLIANCE, "21"); //$NON-NLS-1$
		options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, "21"); //$NON-NLS-1$
		parser.setCompilerOptions(options);

		return (CompilationUnit) parser.createAST(null);
	}

	/**
	 * Collects all problems from the compilation unit.
	 */
	private static List<IProblem> collectProblems(CompilationUnit cu) {
		IProblem[] rawProblems = cu.getProblems();
		if (rawProblems == null || rawProblems.length == 0) {
			return Collections.emptyList();
		}
		List<IProblem> problems = new ArrayList<>(rawProblems.length);
		Collections.addAll(problems, rawProblems);
		return problems;
	}

	/**
	 * Extracts method declarations from the compilation unit that could serve
	 * as guard functions (public boolean methods).
	 */
	private static List<MethodDeclaration> extractGuardMethods(CompilationUnit cu) {
		List<MethodDeclaration> guards = new ArrayList<>();
		for (Object typeObj : cu.types()) {
			if (typeObj instanceof TypeDeclaration typeDecl) {
				for (MethodDeclaration method : typeDecl.getMethods()) {
					if (isGuardMethod(method)) {
						guards.add(method);
					}
				}
			}
		}
		return guards;
	}

	/**
	 * Checks if a method declaration could serve as a guard function.
	 * A guard method is any method that returns {@code boolean}.
	 */
	private static boolean isGuardMethod(MethodDeclaration method) {
		if (method.getReturnType2() == null) {
			return false;
		}
		String returnType = method.getReturnType2().toString();
		return "boolean".equals(returnType); //$NON-NLS-1$
	}

	/**
	 * Sanitizes a string for use as a Java identifier.
	 */
	static String sanitizeIdentifier(String input) {
		if (input == null || input.isEmpty()) {
			return "anonymous"; //$NON-NLS-1$
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < input.length(); i++) {
			char c = input.charAt(i);
			if (Character.isJavaIdentifierPart(c)) {
				sb.append(c);
			} else {
				sb.append('_');
			}
		}
		String result = sb.toString();
		if (result.isEmpty() || !Character.isJavaIdentifierStart(result.charAt(0))) {
			return "_" + result; //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Counts the number of newlines in a string.
	 */
	private static int countLines(String text) {
		int count = 0;
		for (int i = 0; i < text.length(); i++) {
			if (text.charAt(i) == '\n') {
				count++;
			}
		}
		return count;
	}

	/**
	 * Builds source-to-line mappings for debugging support.
	 *
	 * <p>Maps each line in the embedded Java block to the corresponding line
	 * in the synthetic class source. The header lines added by {@link #buildSyntheticSource}
	 * shift the embedded code down, so the mapping accounts for this offset.</p>
	 *
	 * @param block           the embedded Java block
	 * @param headerLineCount the number of header lines in the synthetic source
	 * @return the line mappings
	 */
	private static List<SourceLineMapping> buildLineMappings(EmbeddedJavaBlock block,
			int headerLineCount) {
		int lineCount = block.getLineCount();
		List<SourceLineMapping> mappings = new ArrayList<>(lineCount);
		for (int i = 0; i < lineCount; i++) {
			int hintLine = block.getStartLine() + i;
			int syntheticLine = headerLineCount + 1 + i; // +1 for 1-based indexing
			mappings.add(new SourceLineMapping(hintLine, syntheticLine));
		}
		return Collections.unmodifiableList(mappings);
	}
}
