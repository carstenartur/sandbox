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

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmark for AST parsing performance.
 * Tests parsing small, medium, and large Java source files.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class ASTParsingBenchmark {

	@Param({"small", "medium", "large"})
	private String codeSize;
	
	private String sourceCode;
	
	@Setup
	public void setup() {
		sourceCode = switch (codeSize) {
			case "small" -> generateSmallCode();
			case "medium" -> generateMediumCode();
			case "large" -> generateLargeCode();
			default -> generateSmallCode();
		};
	}
	
	@Benchmark
	public CompilationUnit parseASTWithBindings() {
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setSource(sourceCode.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(true);
		parser.setBindingsRecovery(true);
		parser.setStatementsRecovery(true);
		parser.setUnitName("Test.java");
		parser.setEnvironment(new String[0], new String[0], new String[0], true);
		parser.setCompilerOptions(JavaCore.getOptions());
		return (CompilationUnit) parser.createAST(null);
	}
	
	@Benchmark
	public CompilationUnit parseASTWithoutBindings() {
		ASTParser parser = ASTParser.newParser(AST.JLS21);
		parser.setSource(sourceCode.toCharArray());
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setResolveBindings(false);
		return (CompilationUnit) parser.createAST(null);
	}
	
	private String generateSmallCode() {
		return """
			package test;
			
			public class SmallClass {
				private int field;
				
				public int getField() {
					return field;
				}
				
				public void setField(int value) {
					this.field = value;
				}
			}
			""";
	}
	
	private String generateMediumCode() {
		StringBuilder sb = new StringBuilder();
		sb.append("package test;\n\n");
		sb.append("public class MediumClass {\n");
		
		// Add 50 fields
		for (int i = 0; i < 50; i++) {
			sb.append("    private int field").append(i).append(";\n");
		}
		
		// Add 50 methods
		for (int i = 0; i < 50; i++) {
			sb.append("    public int getField").append(i).append("() {\n");
			sb.append("        return field").append(i).append(";\n");
			sb.append("    }\n\n");
		}
		
		sb.append("}\n");
		return sb.toString();
	}
	
	private String generateLargeCode() {
		StringBuilder sb = new StringBuilder();
		sb.append("package test;\n\n");
		sb.append("public class LargeClass {\n");
		
		// Add 200 fields
		for (int i = 0; i < 200; i++) {
			sb.append("    private int field").append(i).append(";\n");
		}
		
		// Add 200 methods with more complex logic
		for (int i = 0; i < 200; i++) {
			sb.append("    public int getField").append(i).append("() {\n");
			sb.append("        int result = field").append(i).append(";\n");
			sb.append("        for (int j = 0; j < 10; j++) {\n");
			sb.append("            result += j;\n");
			sb.append("        }\n");
			sb.append("        return result;\n");
			sb.append("    }\n\n");
		}
		
		sb.append("}\n");
		return sb.toString();
	}
}
