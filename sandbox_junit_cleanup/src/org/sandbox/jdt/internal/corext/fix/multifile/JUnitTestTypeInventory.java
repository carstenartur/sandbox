/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.multifile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.junit.JUnitCore;

/** Stable test-type inventory produced by Eclipse JDT's configured JUnit finder. */
public record JUnitTestTypeInventory(List<String> typeHandles) {

	public JUnitTestTypeInventory {
		typeHandles= typeHandles.stream().distinct().sorted().toList();
	}

	/** Captures the finder-visible test types for the supplied project or source container. */
	public static JUnitTestTypeInventory capture(IJavaElement container, IProgressMonitor monitor)
			throws CoreException {
		List<String> handles= Arrays.stream(JUnitCore.findTestTypes(container, monitor))
				.map(IJavaElement::getHandleIdentifier)
				.toList();
		return new JUnitTestTypeInventory(handles);
	}

	/** Returns whether the configured JDT JUnit finder currently exposes the type. */
	public boolean contains(IType type) {
		return type != null && Collections.binarySearch(typeHandles, type.getHandleIdentifier()) >= 0;
	}
}
