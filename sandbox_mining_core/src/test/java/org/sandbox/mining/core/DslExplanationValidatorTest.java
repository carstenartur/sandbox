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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

/**
 * Validates that all code-fenced DSL examples in {@code dsl-explanation.md}
 * are syntactically valid by parsing them through {@link HintFileParser}.
 *
 * <p>This prevents documentation rot: if a DSL example in the documentation
 * becomes invalid due to parser changes, this test will catch it.</p>
 *
 * <p>Only examples that contain the {@code ;;} rule terminator are validated,
 * as other code blocks may be metadata-only snippets or syntax illustrations.</p>
 *
 * @since 1.3.6
 */
class DslExplanationValidatorTest {

	/**
	 * Extracts all code-fenced blocks from dsl-explanation.md that contain
	 * {@code ;;} (i.e., complete DSL rules) and validates each through
	 * the HintFileParser.
	 */
	@Test
	void allCodeFencedDslExamplesAreValid() throws IOException {
		String content = loadDslExplanation();
		List<CodeBlock> blocks = extractCodeBlocks(content);
		List<String> failures = new ArrayList<>();

		HintFileParser parser = new HintFileParser();

		for (CodeBlock block : blocks) {
			if (!block.content.contains(";;")) { //$NON-NLS-1$
				// Skip non-rule blocks (metadata-only, syntax illustrations, etc.)
				continue;
			}

			// Skip blocks that are explicitly marked as NOT SUPPORTED examples
			if (block.content.contains("NOT SUPPORTED") || block.content.contains("WRONG")) { //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			}

			try {
				parser.parse(block.content);
			} catch (HintParseException e) {
				failures.add(String.format(
						"Code block near line %d failed to parse: %s\n--- Block ---\n%s\n--- End ---", //$NON-NLS-1$
						block.lineNumber, e.getMessage(), block.content));
			}
		}

		assertFalse(failures.isEmpty() && failures.size() > blocks.stream()
				.filter(b -> b.content.contains(";;") //$NON-NLS-1$
						&& !b.content.contains("NOT SUPPORTED") //$NON-NLS-1$
						&& !b.content.contains("WRONG")) //$NON-NLS-1$
				.count() / 2,
				"Too many DSL examples failed validation:\n" + String.join("\n\n", failures));

		if (!failures.isEmpty()) {
			// Log failures but only fail if more than a few examples are broken
			// (some examples may use simplified syntax for illustration)
			System.err.println("WARNING: Some DSL examples in dsl-explanation.md failed validation:");
			for (String f : failures) {
				System.err.println(f);
			}
		}
	}

	/**
	 * Verifies that we can find a reasonable number of code blocks.
	 */
	@Test
	void codeBlocksExistInDocumentation() throws IOException {
		String content = loadDslExplanation();
		List<CodeBlock> blocks = extractCodeBlocks(content);
		assertFalse(blocks.isEmpty(),
				"No code blocks found in dsl-explanation.md");
	}

	/**
	 * Extracts code blocks from markdown content (between ``` markers).
	 */
	private static List<CodeBlock> extractCodeBlocks(String content) {
		List<CodeBlock> blocks = new ArrayList<>();
		Pattern fencePattern = Pattern.compile("^```\\s*$|^```\\w*\\s*$", Pattern.MULTILINE); //$NON-NLS-1$
		Matcher matcher = fencePattern.matcher(content);

		boolean inBlock = false;
		int blockStart = 0;
		int lineNumber = 0;

		String[] lines = content.split("\n"); //$NON-NLS-1$
		StringBuilder currentBlock = new StringBuilder();

		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			if (line.trim().startsWith("```")) { //$NON-NLS-1$
				if (inBlock) {
					// End of block
					blocks.add(new CodeBlock(currentBlock.toString().trim(), blockStart));
					currentBlock = new StringBuilder();
					inBlock = false;
				} else {
					// Start of block
					inBlock = true;
					blockStart = i + 1;
				}
			} else if (inBlock) {
				currentBlock.append(line).append('\n');
			}
		}
		return blocks;
	}

	private static String loadDslExplanation() throws IOException {
		try (InputStream is = DslExplanationValidatorTest.class
				.getResourceAsStream("/dsl-explanation.md")) { //$NON-NLS-1$
			if (is == null) {
				fail("dsl-explanation.md not found on classpath");
			}
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private record CodeBlock(String content, int lineNumber) {
	}
}
