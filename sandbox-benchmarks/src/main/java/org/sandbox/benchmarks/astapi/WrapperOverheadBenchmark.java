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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
import org.sandbox.ast.api.info.TypeInfo;

/**
 * Benchmarks to measure the overhead of wrapper objects.
 * Compares raw operations with wrapped API calls.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class WrapperOverheadBenchmark {
	
	private List<String> rawStrings;
	private List<TypeInfo> typeInfos;
	
	@Setup
	public void setup() {
		rawStrings = new ArrayList<>();
		rawStrings.add("java.util.List");
		rawStrings.add("java.util.ArrayList");
		rawStrings.add("java.util.Set");
		rawStrings.add("java.util.HashSet");
		rawStrings.add("java.lang.String");
		
		typeInfos = new ArrayList<>();
		for (String s : rawStrings) {
			typeInfos.add(TypeInfo.Builder.of(s).build());
		}
	}
	
	@Benchmark
	public int iterateRawStrings() {
		int count = 0;
		for (String s : rawStrings) {
			if (s.contains("List")) {
				count++;
			}
		}
		return count;
	}
	
	@Benchmark
	public int iterateTypeInfos() {
		int count = 0;
		for (TypeInfo type : typeInfos) {
			if (type.qualifiedName().contains("List")) {
				count++;
			}
		}
		return count;
	}
	
	@Benchmark
	public boolean rawCollectionCheck() {
		String typeName = "java.util.ArrayList";
		return typeName.equals("java.util.Collection") ||
			   typeName.equals("java.util.List") ||
			   typeName.equals("java.util.Set") ||
			   typeName.equals("java.util.ArrayList") ||
			   typeName.equals("java.util.LinkedList");
	}
	
	@Benchmark
	public boolean wrapperCollectionCheck() {
		TypeInfo type = TypeInfo.Builder.of("java.util.ArrayList").build();
		return type.isCollection();
	}
	
	@Benchmark
	public TypeInfo recordAllocation() {
		// Measure pure record allocation cost
		return new TypeInfo("java.lang.String", "String", List.of(), false, false, 0);
	}
	
	@Benchmark
	public TypeInfo builderAllocation() {
		// Measure builder pattern allocation cost
		return TypeInfo.Builder.of("java.lang.String").build();
	}
	
	@Benchmark
	public boolean rawTypeComparison() {
		String typeName1 = "java.util.List";
		String typeName2 = "java.util.List";
		return typeName1.equals(typeName2);
	}
	
	@Benchmark
	public boolean wrapperTypeComparison() {
		TypeInfo type1 = TypeInfo.Builder.of("java.util.List").build();
		TypeInfo type2 = TypeInfo.Builder.of("java.util.List").build();
		return type1.equals(type2);
	}
}
