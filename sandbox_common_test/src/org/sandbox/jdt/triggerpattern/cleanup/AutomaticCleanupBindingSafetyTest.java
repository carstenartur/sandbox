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
package org.sandbox.jdt.triggerpattern.cleanup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperation;
import org.eclipse.text.edits.TextEditGroup;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.CleanupPattern;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;

/** Automatic cleanup consumers must not execute unresolved concrete API matches. */
class AutomaticCleanupBindingSafetyTest {

	private static final String SOURCE= """
			import java.util.Collections;
			class Sample { Object value() { return Collections.emptyList(); } }
			""";
	private static final Pattern API_PATTERN= Pattern.of("java.util.Collections.emptyList()", PatternKind.METHOD_CALL); //$NON-NLS-1$

	@Test
	void compositionHelperDoesNotExposeUnresolvedApiIdentityAsAnApplicableMatch() {
		PatternCleanupHelper helper= new PatternCleanupHelper(RecordingCleanup.class);

		List<Match> matches= helper.findAllMatches(parse(false), helper.getPatterns());

		assertTrue(matches.isEmpty(), "A spelling-only API match cannot authorize automatic cleanup"); //$NON-NLS-1$
	}

	@Test
	void compositionHelperRetainsResolvedApiMatchesOnTheOriginalAst() {
		PatternCleanupHelper helper= new PatternCleanupHelper(RecordingCleanup.class);
		CompilationUnit unit= parse(true);

		List<Match> matches= helper.findAllMatches(unit, helper.getPatterns());

		assertEquals(1, matches.size());
		assertSame(unit, matches.getFirst().getMatchedNode().getRoot());
	}

	@Test
	void pluginDoesNotCreateAHolderOrOperationForUnresolvedApiIdentity() {
		RecordingCleanup cleanup= new RecordingCleanup();
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();

		cleanup.findOperations(parse(false), operations);

		assertEquals(0, cleanup.createdHolders);
		assertTrue(operations.isEmpty());
	}

	@Test
	void pluginCreatesOneHolderAndOperationForAResolvedApiIdentity() {
		RecordingCleanup cleanup= new RecordingCleanup();
		Set<CompilationUnitRewriteOperation> operations= new LinkedHashSet<>();

		cleanup.findOperations(parse(true), operations);

		assertEquals(1, cleanup.createdHolders);
		assertEquals(1, operations.size());
	}

	private static CompilationUnit parse(boolean resolveBindings) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(SOURCE.toCharArray());
		Map<String, String> options= new HashMap<>(JavaCore.getOptions());
		JavaCore.setComplianceOptions("21", options); //$NON-NLS-1$
		parser.setCompilerOptions(options);
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setEnvironment(new String[0], null, null, true);
		parser.setResolveBindings(resolveBindings);
		parser.setBindingsRecovery(false);
		CompilationUnit unit= (CompilationUnit) parser.createAST(null);
		if (resolveBindings) {
			for (var problem : unit.getProblems()) {
				assertFalse(problem.isError(), problem::getMessage);
			}
		}
		return unit;
	}

	@CleanupPattern(value= "java.util.Collections.emptyList()", kind= PatternKind.METHOD_CALL)
	private static final class RecordingCleanup extends AbstractPatternCleanupPlugin<Match> {

		private int createdHolders;

		@Override
		protected List<Pattern> getPatterns() {
			return List.of(API_PATTERN);
		}

		@Override
		protected Match createHolder(Match match) {
			createdHolders++;
			return match;
		}

		@Override
		protected void processRewrite(TextEditGroup group, ASTRewrite rewriter, AST ast,
				ImportRewrite importRewriter, Match holder) {
			throw new AssertionError("Discovering operations must not apply them"); //$NON-NLS-1$
		}

		@Override
		public String getPreview(boolean afterRefactoring) {
			return ""; //$NON-NLS-1$
		}
	}
}
