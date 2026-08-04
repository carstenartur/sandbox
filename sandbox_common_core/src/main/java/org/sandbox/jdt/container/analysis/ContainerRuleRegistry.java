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
package org.sandbox.jdt.container.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.sandbox.jdt.container.api.ContainerRuleDescriptor;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.container.api.ContainerShape;

/**
 * Central inventory used to prevent semantic container analysis from duplicating
 * existing local cleanups and refactorings.
 */
public final class ContainerRuleRegistry {

	public static final String ARRAY_APPEND_SEQUENCE= "semantic.array.append.sequence"; //$NON-NLS-1$
	public static final String COLLECTION_BULK_ADD= "existing.collection.bulk-add"; //$NON-NLS-1$
	public static final String COLLECTION_COPY_CONSTRUCTOR= "existing.collection.copy-constructor"; //$NON-NLS-1$
	public static final String ARRAY_FILL= "existing.array.fill"; //$NON-NLS-1$
	public static final String TYPE_GENERALIZATION= "existing.type.generalization"; //$NON-NLS-1$

	private static final Map<String, ContainerRuleDescriptor> RULES= createRules();

	private ContainerRuleRegistry() {
		// Static registry.
	}

	/** Returns one descriptor by stable identifier. */
	public static Optional<ContainerRuleDescriptor> find(String ruleId) {
		return Optional.ofNullable(RULES.get(ruleId));
	}

	/** Returns descriptors in deterministic registration order. */
	public static List<ContainerRuleDescriptor> all() {
		return List.copyOf(RULES.values());
	}

	/** Returns the descriptor for the first implemented semantic migration family. */
	public static ContainerRuleDescriptor arrayAppendSequence() {
		return RULES.get(ARRAY_APPEND_SEQUENCE);
	}

	private static Map<String, ContainerRuleDescriptor> createRules() {
		Map<String, ContainerRuleDescriptor> rules= new LinkedHashMap<>();
		register(rules, new ContainerRuleDescriptor(
				COLLECTION_BULK_ADD,
				ContainerShape.LIST,
				ContainerShape.LIST,
				RuleOwnership.DUPLICATE,
				"Eclipse collection bulk-add cleanup", //$NON-NLS-1$
				"Local loop-to-bulk-add syntax is already owned by an existing cleanup.")); //$NON-NLS-1$
		register(rules, new ContainerRuleDescriptor(
				COLLECTION_COPY_CONSTRUCTOR,
				ContainerShape.LIST,
				ContainerShape.LIST,
				RuleOwnership.DUPLICATE,
				"Eclipse collection copy-constructor cleanup", //$NON-NLS-1$
				"Local construction followed by copying is already normalized elsewhere.")); //$NON-NLS-1$
		register(rules, new ContainerRuleDescriptor(
				ARRAY_FILL,
				ContainerShape.ARRAY,
				ContainerShape.ARRAY,
				RuleOwnership.DUPLICATE,
				"Eclipse array-fill cleanup", //$NON-NLS-1$
				"Replacing a uniform assignment loop with array fill is a local syntax cleanup.")); //$NON-NLS-1$
		register(rules, new ContainerRuleDescriptor(
				TYPE_GENERALIZATION,
				ContainerShape.LIST,
				ContainerShape.LIST,
				RuleOwnership.DUPLICATE,
				"Eclipse Change Type and Sandbox Use General Type", //$NON-NLS-1$
				"Changing only the declared supertype is already covered by type-generalization tools.")); //$NON-NLS-1$
		register(rules, new ContainerRuleDescriptor(
				ARRAY_APPEND_SEQUENCE,
				ContainerShape.ARRAY,
				ContainerShape.LIST,
				RuleOwnership.NOVEL,
				"", //$NON-NLS-1$
				"The rule changes representation and may propagate through fields, signatures and callers.")); //$NON-NLS-1$
		return Collections.unmodifiableMap(new LinkedHashMap<>(rules));
	}

	private static void register(
			Map<String, ContainerRuleDescriptor> rules,
			ContainerRuleDescriptor descriptor) {
		if (rules.putIfAbsent(descriptor.ruleId(), descriptor) != null) {
			throw new IllegalStateException("Duplicate container rule id: " + descriptor.ruleId()); //$NON-NLS-1$
		}
	}
}
