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
import org.sandbox.ast.api.info.ParameterInfo;
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Benchmarks for MethodInfo creation and query operations.
 * Compares old-style method detection with new fluent API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class MethodInfoBenchmark {
	
	@Benchmark
	public MethodInfo createSimpleMethod() {
		TypeInfo returnType = TypeInfo.Builder.of("void").build();
		return MethodInfo.Builder.named("toString")
			.returnType(returnType)
			.build();
	}
	
	@Benchmark
	public boolean queryIsMathMax() {
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
	public boolean queryHasSignature() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("substring")
			.returnType(stringType)
			.addParameter(ParameterInfo.of("start", intType))
			.addParameter(ParameterInfo.of("end", intType))
			.build();
		
		return method.hasSignature("substring", "int", "int");
	}
	
	@Benchmark
	public boolean oldStyleMathMaxCheck() {
		// Old style: verbose instanceof checks and casts
		String methodName = "max";
		String declaringType = "java.lang.Math";
		int paramCount = 2;
		
		return methodName.equals("max") && 
			   declaringType.equals("java.lang.Math") &&
			   paramCount == 2;
	}
	
	@Benchmark
	public boolean newStyleMathMaxCheck() {
		TypeInfo mathType = TypeInfo.Builder.of("java.lang.Math").build();
		TypeInfo intType = TypeInfo.Builder.of("int").primitive().build();
		
		MethodInfo method = MethodInfo.Builder.named("max")
			.declaringType(mathType)
			.returnType(intType)
			.addParameter(ParameterInfo.of("a", intType))
			.addParameter(ParameterInfo.of("b", intType))
			.build();
		
		// New style: fluent API
		return method.isMathMax();
	}
	
	@Benchmark
	public boolean queryIsListAdd() {
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
}
