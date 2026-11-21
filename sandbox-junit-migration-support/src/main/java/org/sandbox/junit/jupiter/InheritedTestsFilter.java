/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.junit.jupiter;

import org.junit.platform.engine.FilterResult;
import org.junit.platform.engine.TestDescriptor;
import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.PostDiscoveryFilter;

/** Launcher service for {@link IgnoreInheritedTests}, including the Vintage side of mixed hierarchies. */
public final class InheritedTestsFilter implements PostDiscoveryFilter {
	private static final FilterResult KEEP= FilterResult.included("Keep test descriptor");
	private static final FilterResult HIDE= FilterResult.excluded("Method inherited by @IgnoreInheritedTests class");

	@Override
	public FilterResult apply(TestDescriptor descriptor) {
		TestSource source= descriptor.getSource().orElse(null);
		if (!(source instanceof MethodSource method) || !isSupportedEngine(descriptor.getUniqueId())) {
			return KEEP;
		}
		// The nearest class owns the test even for nested classes and test templates.
		for (var parent= descriptor.getParent(); parent.isPresent(); parent= parent.get().getParent()) {
			if (parent.get().getSource().orElse(null) instanceof ClassSource owner) {
				Class<?> type= owner.getJavaClass();
				return type.isAnnotationPresent(IgnoreInheritedTests.class)
						&& type != method.getJavaMethod().getDeclaringClass() ? HIDE : KEEP;
			}
		}
		return KEEP;
	}

	private static boolean isSupportedEngine(UniqueId id) {
		// Platform suites can delegate discovery to an engine below the suite's own engine segment.
		for (var segment : id.getSegments().reversed()) {
			if ("engine".equals(segment.getType())) {
				return "junit-jupiter".equals(segment.getValue()) || "junit-vintage".equals(segment.getValue());
			}
		}
		return false;
	}
}
