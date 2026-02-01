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
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Benchmarks for TypeInfo creation and query operations.
 * Compares old-style instanceof checks with new fluent API.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class TypeInfoBenchmark {
	
	@Benchmark
	public TypeInfo createSimpleType() {
		return TypeInfo.Builder.of("java.lang.String").build();
	}
	
	@Benchmark
	public TypeInfo createParameterizedType() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		return TypeInfo.Builder.of("java.util.List")
			.addTypeArgument(stringType)
			.build();
	}
	
	@Benchmark
	public boolean queryIsCollection() {
		TypeInfo type = TypeInfo.Builder.of("java.util.ArrayList").build();
		return type.isCollection();
	}
	
	@Benchmark
	public boolean queryIsClass() {
		TypeInfo type = TypeInfo.Builder.of("java.lang.String").build();
		return type.is(String.class);
	}
	
	@Benchmark
	public boolean oldStyleTypeCheck() {
		String qualifiedName = "java.util.List";
		// Old style: manual string comparison
		return qualifiedName.equals("java.util.List") ||
			   qualifiedName.equals("java.util.ArrayList") ||
			   qualifiedName.equals("java.util.LinkedList");
	}
	
	@Benchmark
	public boolean newStyleTypeCheck() {
		TypeInfo type = TypeInfo.Builder.of("java.util.List").build();
		// New style: fluent API
		return type.isList();
	}
	
	@Benchmark
	public boolean queryIsNumeric() {
		TypeInfo type = TypeInfo.Builder.of("int").primitive().build();
		return type.isNumeric();
	}
	
	@Benchmark
	public boolean queryIsStream() {
		TypeInfo type = TypeInfo.Builder.of("java.util.stream.Stream").build();
		return type.isStream();
	}
	
	@Benchmark
	public boolean queryIsOptional() {
		TypeInfo type = TypeInfo.Builder.of("java.util.Optional").build();
		return type.isOptional();
	}
}
