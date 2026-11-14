/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.container.api;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** An atomic return signature, local producer and all bounded source callers. */
public record ClosedSourceReturnMigrationPlan(ContainerLocalRewritePlan producer, String methodHandle,
		String methodBindingKey, Range returnType, List<Consumer> consumers) implements ClosedSourceContainerPlan {

	public ClosedSourceReturnMigrationPlan {
		Objects.requireNonNull(producer);
		Objects.requireNonNull(returnType);
		if (methodHandle.isBlank() || methodBindingKey.isBlank() || consumers.isEmpty()) {
			throw new IllegalArgumentException("A return migration requires its producer method and callers"); //$NON-NLS-1$
		}
		consumers= List.copyOf(consumers);
	}

	public record Range(int start, int length) {
		public Range {
			if (start < 0 || length <= 0) {
				throw new IllegalArgumentException("Invalid return migration range"); //$NON-NLS-1$
			}
		}
	}

	public record FunctionTarget(String bindingKey, Range returnArgument, Range initializer) {
		public FunctionTarget {
			if (bindingKey.isBlank()) {
				throw new IllegalArgumentException("A local function requires a source binding"); //$NON-NLS-1$
			}
			Objects.requireNonNull(returnArgument);
			Objects.requireNonNull(initializer);
		}
	}

	public record Consumer(String unitHandle, String localBindingKey, Range localType, Range call, List<Range> lengths,
			Optional<FunctionTarget> functionTarget) {
		public Consumer(String unitHandle, String localBindingKey, Range localType, Range call, List<Range> lengths) {
			this(unitHandle, localBindingKey, localType, call, lengths, Optional.empty());
		}

		public Consumer {
			Objects.requireNonNull(localType);
			Objects.requireNonNull(functionTarget);
			Objects.requireNonNull(call);
			if (unitHandle.isBlank() || localBindingKey.isBlank()) {
				throw new IllegalArgumentException("A return consumer requires source bindings"); //$NON-NLS-1$
			}
			lengths= List.copyOf(lengths);
		}
	}

	@Override
	public List<String> affectedCompilationUnitHandles() {
		var result= new LinkedHashSet<String>();
		result.add(producer.compilationUnitHandle());
		consumers.forEach(consumer -> result.add(consumer.unitHandle()));
		return List.copyOf(result);
	}
}
