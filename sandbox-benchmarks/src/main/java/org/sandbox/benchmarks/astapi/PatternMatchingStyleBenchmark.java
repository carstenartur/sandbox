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
package org.sandbox.benchmarks.astapi;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.sandbox.ast.api.info.MethodInfo;
import org.sandbox.ast.api.info.Modifier;
import org.sandbox.ast.api.info.ParameterInfo;
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Benchmarks comparing old-style pattern detection with new fluent API.
 * Demonstrates readability and performance trade-offs.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class PatternMatchingStyleBenchmark {
	
	@Benchmark
	public boolean oldStyleIsMathMax() {
		// Old style: verbose checks
		String methodName = "max";
		String declaringType = "java.lang.Math";
		String[] paramTypes = {"int", "int"};
		
		if (!methodName.equals("max")) {
			return false;
		}
		if (!declaringType.equals("java.lang.Math")) {
			return false;
		}
		if (paramTypes.length != 2) {
			return false;
		}
		return true;
	}
	
	@Benchmark
	public boolean newStyleIsMathMax() {
		// New style: fluent API
		TypeInfo mathType = TypeInfo.Builder.of("java.lang.Math").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("max")
			.declaringType(mathType)
			.returnType(intType)
			.addParameter(ParameterInfo.of("a", intType))
			.addParameter(ParameterInfo.of("b", intType))
			.build();
		
		return method.isMathMax();
	}
	
	@Benchmark
	public boolean oldStylePatternDetection() {
		// Old style: complex nested checks
		String methodName = "add";
		String declaringType = "java.util.ArrayList";
		int paramCount = 1;
		
		boolean isListType = declaringType.equals("java.util.List") ||
							 declaringType.equals("java.util.ArrayList") ||
							 declaringType.equals("java.util.LinkedList");
		
		return methodName.equals("add") && isListType && paramCount == 1;
	}
	
	@Benchmark
	public boolean newStylePatternDetection() {
		// New style: fluent chaining
		TypeInfo listType = TypeInfo.Builder.of("java.util.ArrayList").build();
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo boolType = TypeInfo.Builder.of("boolean").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("add")
			.declaringType(listType)
			.returnType(boolType)
			.addParameter(ParameterInfo.of("element", stringType))
			.build();
		
		return method.isListAdd();
	}
	
	@Benchmark
	public boolean oldStyleModifierChecks() {
		// Old style: bit operations
		int flags = 0x0001 | 0x0008; // PUBLIC | STATIC
		boolean isPublic = (flags & 0x0001) != 0;
		boolean isStatic = (flags & 0x0008) != 0;
		return isPublic && isStatic;
	}
	
	@Benchmark
	public boolean newStyleModifierChecks() {
		// New style: enum-based
		Set<Modifier> modifiers = Modifier.fromJdtFlags(0x0001 | 0x0008);
		return modifiers.contains(Modifier.PUBLIC) && modifiers.contains(Modifier.STATIC);
	}
	
	@Benchmark
	public boolean complexOldStyleCheck() {
		// Old style: deeply nested logic
		String typeName = "java.util.stream.Stream";
		String methodName = "filter";
		
		boolean isStreamType = typeName.equals("java.util.stream.Stream") ||
							   typeName.equals("java.util.stream.IntStream") ||
							   typeName.equals("java.util.stream.LongStream") ||
							   typeName.equals("java.util.stream.DoubleStream");
		
		return isStreamType && methodName.equals("filter");
	}
	
	@Benchmark
	public boolean complexNewStyleCheck() {
		// New style: fluent queries
		TypeInfo type = TypeInfo.Builder.of("java.util.stream.Stream").build();
		String methodName = "filter";
		return type.isStream() && "filter".equals(methodName);
	}
}
