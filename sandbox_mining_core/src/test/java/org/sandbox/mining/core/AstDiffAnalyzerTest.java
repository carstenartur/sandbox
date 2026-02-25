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
package org.sandbox.mining.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.mining.core.astdiff.AstDiff;
import org.sandbox.mining.core.astdiff.AstDiffAnalyzer;
import org.sandbox.mining.core.astdiff.AstNodeChange;
import org.sandbox.mining.core.astdiff.AstNodeChange.ChangeType;
import org.sandbox.mining.core.astdiff.CodeChangePair;

/**
 * Tests for {@link AstDiffAnalyzer}.
 */
class AstDiffAnalyzerTest {

	private final AstDiffAnalyzer analyzer = new AstDiffAnalyzer();

	@Test
	void testAnalyzeNullPair() {
		AstDiff diff = analyzer.analyze(null);
		assertNotNull(diff);
		assertTrue(diff.isEmpty());
		assertNull(diff.source());
	}

	@Test
	void testAnalyzeIdenticalCode() {
		CodeChangePair pair = CodeChangePair.of("int x = 1;", "int x = 1;");
		AstDiff diff = analyzer.analyze(pair);
		assertNotNull(diff);
		assertTrue(diff.isEmpty());
		assertEquals(0, diff.changeCount());
	}

	@Test
	void testAnalyzeSimpleReplace() {
		CodeChangePair pair = CodeChangePair.of(
				"new String(buf, \"UTF-8\")",
				"new String(buf, StandardCharsets.UTF_8)");
		AstDiff diff = analyzer.analyze(pair);

		assertNotNull(diff);
		assertFalse(diff.isEmpty());
		assertEquals(1, diff.changeCount());

		AstNodeChange change = diff.changes().get(0);
		assertEquals(ChangeType.REPLACE, change.changeType());
	}

	@Test
	void testAnalyzeInsert() {
		CodeChangePair pair = CodeChangePair.of("", "import java.nio.charset.StandardCharsets;");
		AstDiff diff = analyzer.analyze(pair);

		assertFalse(diff.isEmpty());
		AstNodeChange change = diff.changes().get(0);
		assertEquals(ChangeType.INSERT, change.changeType());
		assertEquals("ImportDeclaration", change.nodeType());
	}

	@Test
	void testAnalyzeDelete() {
		CodeChangePair pair = CodeChangePair.of("import java.io.UnsupportedEncodingException;", "");
		AstDiff diff = analyzer.analyze(pair);

		assertFalse(diff.isEmpty());
		AstNodeChange change = diff.changes().get(0);
		assertEquals(ChangeType.DELETE, change.changeType());
		assertEquals("ImportDeclaration", change.nodeType());
	}

	@Test
	void testAnalyzeBatch() {
		List<CodeChangePair> pairs = List.of(
				CodeChangePair.of("foo()", "bar()"),
				CodeChangePair.of("x = 1;", "x = 2;"));

		List<AstDiff> diffs = analyzer.analyzeBatch(pairs);
		assertEquals(2, diffs.size());
		assertFalse(diffs.get(0).isEmpty());
		assertFalse(diffs.get(1).isEmpty());
	}

	@Test
	void testAnalyzeBatchNull() {
		List<AstDiff> diffs = analyzer.analyzeBatch(null);
		assertTrue(diffs.isEmpty());
	}

	@Test
	void testNodeTypeInferredForMethodInvocation() {
		CodeChangePair pair = CodeChangePair.of("obj.toString()", "obj.asString()");
		AstDiff diff = analyzer.analyze(pair);
		assertFalse(diff.isEmpty());
		assertEquals("MethodInvocation", diff.changes().get(0).nodeType());
	}

	@Test
	void testNodeTypeInferredForClassInstanceCreation() {
		CodeChangePair pair = CodeChangePair.of("new ArrayList<>()", "new LinkedList<>()");
		AstDiff diff = analyzer.analyze(pair);
		assertFalse(diff.isEmpty());
		assertEquals("ClassInstanceCreation", diff.changes().get(0).nodeType());
	}

	@Test
	void testNodeTypeInferredForImport() {
		CodeChangePair pair = CodeChangePair.of("import java.util.List;", "import java.util.Set;");
		AstDiff diff = analyzer.analyze(pair);
		assertFalse(diff.isEmpty());
		assertEquals("ImportDeclaration", diff.changes().get(0).nodeType());
	}

	@Test
	void testCodeChangePairFactory() {
		CodeChangePair pair = CodeChangePair.of("a", "b");
		assertNull(pair.filePath());
		assertEquals(0, pair.startLine());
		assertNull(pair.commitId());
		assertEquals("a", pair.before());
		assertEquals("b", pair.after());
	}

	@Test
	void testMultiLineReplace() {
		String before = "String s = new String(data, \"UTF-8\");\nSystem.out.println(s);";
		String after = "String s = new String(data, StandardCharsets.UTF_8);\nSystem.out.println(s);";
		CodeChangePair pair = CodeChangePair.of(before, after);
		AstDiff diff = analyzer.analyze(pair);

		assertFalse(diff.isEmpty());
		assertEquals(1, diff.changeCount());
		AstNodeChange change = diff.changes().get(0);
		assertEquals(ChangeType.REPLACE, change.changeType());
	}
}
