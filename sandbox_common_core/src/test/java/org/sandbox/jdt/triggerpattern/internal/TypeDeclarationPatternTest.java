/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import org.sandbox.jdt.triggerpattern.api.BatchTransformationProcessor;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.api.Match;
import org.sandbox.jdt.triggerpattern.api.PatternKind;
import org.sandbox.jdt.triggerpattern.api.TransformationRule;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser.HintParseException;

class TypeDeclarationPatternTest {

	@Test
	void explicitTypeKindMatchesAClassHeaderAndIgnoresItsBody() throws HintParseException {
		HintFile hintFile= parseProgram("""
				@id: class.header
				@kind: TYPE_DECLARATION
				public class $name {}
				=> public class $name {}
				;;
				""");
		TransformationRule rule= hintFile.getRules().get(0);
		assertEquals(PatternKind.TYPE_DECLARATION, rule.sourcePattern().getKind());
		CompilationUnit source= parseJava("""
				package sample;
				public class RealType {
					private int value;
					public void work() {}
				}
				""");

		List<Match> matches= new BatchTransformationProcessor(hintFile)
				.getPatternIndex().findAllMatches(source).get(rule);
		assertEquals(1, matches.size());
		assertEquals("RealType", matches.get(0).getBinding("$name").toString()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void modifiersAndTypeKindsRemainConstraints() throws HintParseException {
		HintFile publicClass= parseProgram(program("public class $name {}")); //$NON-NLS-1$
		HintFile interfacePattern= parseProgram(program("interface $name {}")); //$NON-NLS-1$
		CompilationUnit packagePrivateClass= parseJava("class Sample { void work() {} }"); //$NON-NLS-1$
		CompilationUnit actualInterface= parseJava("interface Sample { void work(); }"); //$NON-NLS-1$

		assertTrue(new BatchTransformationProcessor(publicClass).process(packagePrivateClass).isEmpty());
		assertFalse(new BatchTransformationProcessor(interfacePattern).process(actualInterface).isEmpty());
		assertTrue(new BatchTransformationProcessor(interfacePattern).process(packagePrivateClass).isEmpty());
	}

	@Test
	void enumRecordAndAnnotationHeadersAreSupported() throws HintParseException {
		assertOneTypeMatch("enum $name {}", "enum Actual { ONE; void work() {} }"); //$NON-NLS-1$ //$NON-NLS-2$
		assertOneTypeMatch("record $name() {}", "record Actual(String value) { void work() {} }"); //$NON-NLS-1$ //$NON-NLS-2$
		assertOneTypeMatch("@interface $name {}", "@interface Actual { String value(); }"); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void modifierSubsetBacktracksWithoutLeakingPlaceholderBindings() throws HintParseException {
		HintFile hintFile= parseProgram("""
				@id: annotation.header
				@kind: TYPE_DECLARATION
				@Pair(key=$key, value="expected")
				class $name {}
				=> class $name {}
				;;
				""");
		TransformationRule rule= hintFile.getRules().get(0);
		CompilationUnit source= parseJava("""
				@Pair(key="first", value="wrong")
				@Pair(key="second", value="expected")
				class Actual {}
				""");

		List<Match> matches= new BatchTransformationProcessor(hintFile)
				.getPatternIndex().findAllMatches(source).get(rule);
		assertEquals(1, matches.size());
		assertEquals("\"second\"", matches.get(0).getBinding("$key").toString()); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void explicitKindRequiresStableRuleIdKnownKindAndHeaderOnlySource() {
		assertThrows(HintParseException.class, () -> parseProgram("""
				@kind: TYPE_DECLARATION
				class $name {}
				=> class $name {}
				;;
				"""));
		assertThrows(HintParseException.class, () -> parseProgram("""
				@id: bad.kind
				@kind: PROJECT_SPECIFIC_TYPE
				class $name {}
				=> class $name {}
				;;
				"""));
		assertThrows(HintParseException.class, () -> parseProgram("""
				@id: bad.body
				@kind: TYPE_DECLARATION
				class $name { void method() {} }
				=> class $name {}
				;;
				"""));
	}

	@Test
	void ordinaryRulesRemainInferredByTheCompatibilityParser() throws HintParseException {
		HintFile hintFile= parseProgram("""
				void $name()
				=> @Deprecated void $name()
				;;
				""");
		assertEquals(PatternKind.METHOD_DECLARATION,
				hintFile.getRules().get(0).sourcePattern().getKind());
	}

	private static void assertOneTypeMatch(String pattern, String source) throws HintParseException {
		HintFile hintFile= parseProgram(program(pattern));
		assertEquals(1, new BatchTransformationProcessor(hintFile).process(parseJava(source)).size());
	}

	private static String program(String pattern) {
		return "@id: type.header\n@kind: TYPE_DECLARATION\n" + pattern //$NON-NLS-1$
				+ "\n=> " + pattern + "\n;;\n"; //$NON-NLS-1$ //$NON-NLS-2$
	}

	private static HintFile parseProgram(String source) throws HintParseException {
		return new HintProgramParser().parseHintFile(source);
	}

	private static CompilationUnit parseJava(String source) {
		ASTParser parser= ASTParser.newParser(AST.getJLSLatest());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(source.toCharArray());
		parser.setUnitName("Sample.java"); //$NON-NLS-1$
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setEnvironment(new String[0], new String[0], null, true);
		Map<String, String> options= JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_21, options);
		parser.setCompilerOptions(options);
		return (CompilationUnit) parser.createAST(null);
	}
}
