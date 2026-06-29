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
package org.sandbox.jdt.triggerpattern.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Pattern;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.internal.FqnAwarePlaceholderAstMatcher;
import org.sandbox.jdt.triggerpattern.internal.PatternParser;

/**
 * Documents the intended FQN-aware DSL style.
 *
 * <p>Rules should normally express concrete API identities with fully qualified
 * names. The matcher is responsible for accepting target code that uses simple
 * names plus imports. This keeps the DSL elegant and avoids duplicating rules
 * for FQN and imported-source variants.</p>
 */
class FqnAwareHintRuleMatchingTest extends HintRuleTestSupport {

	@BeforeEach
	void setUp() {
		registerBuiltInGuards();
	}

	@Test
	void fqnMethodReceiverMatchesImportedSimpleName() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-method-receiver>

				java.util.Collections.emptyList()
				=> java.util.List.of()
				;;
				"""); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"""
				import java.util.Collections;
				class Test { Object m() { return Collections.emptyList(); } }
				""", //$NON-NLS-1$
				"""
				import java.util.Collections;
				class Test { Object m() { return java.util.List.of(); } }
				"""); //$NON-NLS-1$
	}

	@Test
	void fqnMethodReceiverDoesNotMatchSameSimpleNameFromDifferentImport() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-method-receiver-negative>

				java.util.Collections.emptyList()
				=> java.util.List.of()
				;;
				"""); //$NON-NLS-1$

		assertNoMatch(hintFile,
				"""
				import com.example.Collections;
				class Test { Object m() { return Collections.emptyList(); } }
				"""); //$NON-NLS-1$
	}

	@Test
	void fqnConstructorMatchesImportedSimpleNameWithDiamond() throws Exception {
		HintFile hintFile = parseHint("""
				<!id: fqn-constructor>

				new java.util.ArrayList<>()
				=> new java.util.LinkedList<>()
				;;
				"""); //$NON-NLS-1$

		assertFullReplacement(hintFile,
				"""
				import java.util.ArrayList;
				class Test { Object m() { return new ArrayList<>(); } }
				""", //$NON-NLS-1$
				"""
				import java.util.ArrayList;
				class Test { Object m() { return new java.util.LinkedList<>(); } }
				"""); //$NON-NLS-1$
	}

	@Test
	void fqnFieldTypeMatchesImportedSimpleName() {
		assertPatternMatches("java.util.List $items;", PatternKind.FIELD, //$NON-NLS-1$
				"""
				import java.util.List;
				class Test { List items; }
				""", //$NON-NLS-1$
				FieldDeclaration.class);
	}

	@Test
	void fqnLocalVariableTypeMatchesImportedSimpleName() {
		assertPatternMatches("java.util.List $items = $init;", PatternKind.DECLARATION, //$NON-NLS-1$
				"""
				import java.util.List;
				class Test { void m(List source) { List items = source; } }
				""", //$NON-NLS-1$
				VariableDeclarationStatement.class);
	}

	@Test
	void fqnReturnAndParameterTypesMatchImportedSimpleNames() {
		assertPatternMatches("java.util.List convert(java.util.Set input);", PatternKind.METHOD_DECLARATION, //$NON-NLS-1$
				"""
				import java.util.List;
				import java.util.Set;
				interface Test { List convert(Set input); }
				""", //$NON-NLS-1$
				MethodDeclaration.class);
	}

	@Test
	void fqnCastTypeMatchesImportedSimpleName() {
		assertPatternMatches("(java.util.List) $value", PatternKind.EXPRESSION, //$NON-NLS-1$
				"""
				import java.util.List;
				class Test { Object m(Object value) { return (List) value; } }
				""", //$NON-NLS-1$
				CastExpression.class);
	}

	private void assertPatternMatches(String patternText, PatternKind kind, String source,
			Class<? extends ASTNode> candidateType) {
		PatternParser parser = new PatternParser();
		ASTNode patternNode = parser.parse(Pattern.of(patternText, kind));
		assertTrue(patternNode != null, "Pattern should parse"); //$NON-NLS-1$

		CompilationUnit cu = parseCompilationUnit(source);
		List<ASTNode> candidates = new ArrayList<>();
		cu.accept(new ASTVisitor() {
			@Override
			public void preVisit(ASTNode node) {
				if (candidateType.isInstance(node)) {
					candidates.add(node);
				}
			}
		});
		assertFalse(candidates.isEmpty(), "Test source should contain candidates"); //$NON-NLS-1$

		boolean matches = candidates.stream()
				.anyMatch(candidate -> patternNode.subtreeMatch(new FqnAwarePlaceholderAstMatcher(), candidate));
		assertTrue(matches, "FQN-aware matcher should match imported simple-name source"); //$NON-NLS-1$
	}

	private CompilationUnit parseCompilationUnit(String source) {
		ASTParser astParser = ASTParser.newParser(AST.getJLSLatest());
		astParser.setSource(source.toCharArray());
		astParser.setKind(ASTParser.K_COMPILATION_UNIT);
		Map<String, String> options = JavaCore.getOptions();
		options.put(JavaCore.COMPILER_SOURCE, "17"); //$NON-NLS-1$
		astParser.setCompilerOptions(options);
		return (CompilationUnit) astParser.createAST(null);
	}
}
