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
import org.sandbox.functional.core.builder.LoopModelBuilder;
import org.sandbox.functional.core.builder.StreamCodeBuilder;
import org.sandbox.functional.core.model.LoopModel;
import org.sandbox.functional.core.model.SourceDescriptor.SourceType;
import org.sandbox.functional.core.terminal.CollectTerminal.CollectorType;

/**
 * Benchmark for loop transformation operations using the Unified Loop Representation (ULR).
 * Tests performance of building loop models and generating stream code.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3)
@Measurement(iterations = 5)
@Fork(1)
@State(Scope.Benchmark)
public class LoopTransformBenchmark {

	private LoopModel simpleForEachModel;
	private LoopModel complexFilterMapModel;
	private LoopModel collectModel;
	
	@Setup
	public void setup() {
		// Simple forEach loop model
		simpleForEachModel = new LoopModelBuilder()
			.source(SourceType.COLLECTION, "items", "String")
			.element("item", "String", false)
			.metadata(false, false, false, false, true)
			.forEach(java.util.List.of("System.out.println(item)"), false)
			.build();
		
		// Complex filter + map loop model
		complexFilterMapModel = new LoopModelBuilder()
			.source(SourceType.COLLECTION, "users", "User")
			.element("user", "User", false)
			.metadata(false, false, false, false, true)
			.filter("user.isActive()")
			.map("user.getName()", "String")
			.collect(CollectorType.TO_LIST, "names")
			.build();
		
		// Collect loop model
		collectModel = new LoopModelBuilder()
			.source(SourceType.ARRAY, "values", "Integer")
			.element("value", "Integer", false)
			.metadata(false, false, false, false, true)
			.filter("value > 10")
			.collect(CollectorType.TO_LIST, "result")
			.build();
	}
	
	@Benchmark
	public LoopModel buildSimpleLoopModel() {
		return new LoopModelBuilder()
			.source(SourceType.COLLECTION, "items", "String")
			.element("item", "String", false)
			.metadata(false, false, false, false, true)
			.forEach(java.util.List.of("System.out.println(item)"), false)
			.build();
	}
	
	@Benchmark
	public LoopModel buildComplexLoopModel() {
		return new LoopModelBuilder()
			.source(SourceType.COLLECTION, "users", "User")
			.element("user", "User", false)
			.metadata(false, false, false, false, true)
			.filter("user.isActive()")
			.map("user.getName()", "String")
			.filter("name.length() > 5")
			.collect(CollectorType.TO_LIST, "names")
			.build();
	}
	
	@Benchmark
	public String generateStreamCodeForEach() {
		StreamCodeBuilder builder = new StreamCodeBuilder(simpleForEachModel);
		return builder.build();
	}
	
	@Benchmark
	public String generateStreamCodeComplex() {
		StreamCodeBuilder builder = new StreamCodeBuilder(complexFilterMapModel);
		return builder.build();
	}
	
	@Benchmark
	public String generateStreamCodeCollect() {
		StreamCodeBuilder builder = new StreamCodeBuilder(collectModel);
		return builder.build();
	}
}
