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
package org.sandbox.benchmarks;

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark for pattern matching strategies.
 * Compares AST visitor approach vs regex for detecting JUnit assertions.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class PatternMatchingBenchmark {

	private String testCode;
	private Pattern assertPattern;
	private CompilationUnit astRoot;
	
	@Setup
	public void setup() {
		testCode = generateTestCode();
		assertPattern = Pattern.compile("assert(True|False|Equals|NotNull|Null)\\s*\\(");
		
		// Pre-parse AST for AST-based benchmarks
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setSource(testCode.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		astRoot = (CompilationUnit) parser.createAST(null);
	}
	
	@Benchmark
	public int detectAssertionsWithRegex() {
		int count = 0;
		var matcher = assertPattern.matcher(testCode);
		while (matcher.find()) {
			count++;
		}
		return count;
	}
	
	@Benchmark
	public int detectAssertionsWithASTVisitor() {
		AssertionCounter counter = new AssertionCounter();
		astRoot.accept(counter);
		return counter.count;
	}
	
	private String generateTestCode() {
		StringBuilder sb = new StringBuilder();
		sb.append("package test;\n\n");
		sb.append("import org.junit.jupiter.api.Test;\n");
		sb.append("import static org.junit.jupiter.api.Assertions.*;\n\n");
		sb.append("public class TestClass {\n");
		
		// Generate 100 test methods with various assertions
		for (int i = 0; i < 100; i++) {
			sb.append("    @Test\n");
			sb.append("    public void test").append(i).append("() {\n");
			sb.append("        int value = ").append(i).append(";\n");
			sb.append("        assertTrue(value >= 0);\n");
			sb.append("        assertFalse(value < 0);\n");
			sb.append("        assertEquals(").append(i).append(", value);\n");
			sb.append("        assertNotNull(Integer.valueOf(value));\n");
			sb.append("    }\n\n");
		}
		
		sb.append("}\n");
		return sb.toString();
	}
	
	private static class AssertionCounter extends ASTVisitor {
		int count = 0;
		
		@Override
		public boolean visit(MethodInvocation node) {
			String methodName = node.getName().getIdentifier();
			if (methodName.startsWith("assert")) {
				count++;
			}
			return super.visit(node);
		}
	}
}
