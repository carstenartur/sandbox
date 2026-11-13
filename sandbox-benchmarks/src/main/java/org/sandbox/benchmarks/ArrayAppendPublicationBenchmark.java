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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/** Measures the original append loop and the cleanup's retained String[] return. */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
@State(Scope.Thread)
public class ArrayAppendPublicationBenchmark {

	@Param({ "0", "1", "16", "256", "1024" })
	public int size;

	private String[] input;

	@Setup(Level.Trial)
	public void prepare() {
		input = new String[size];
		for (int index = 0; index < size; index++) {
			input[index] = index % 3 == 0 ? null : Integer.toString(index % 5);
		}
		if (!Arrays.equals(input, copyOnEveryAppend()) || !Arrays.equals(input, listWithArrayReturn())) {
			throw new IllegalStateException("Both paths must publish the same ordered values");
		}
	}

	@Benchmark
	public String[] copyOnEveryAppend() {
		String[] values = new String[0];
		for (String value : input) {
			values = Arrays.copyOf(values, values.length + 1);
			values[values.length - 1] = value;
		}
		return values;
	}

	@Benchmark
	public String[] listWithArrayReturn() {
		List<String> values = new ArrayList<>();
		for (String value : input) {
			values.add(value);
		}
		return values.toArray(new String[0]);
	}
}
