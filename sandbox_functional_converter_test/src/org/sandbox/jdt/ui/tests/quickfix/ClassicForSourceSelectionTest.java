/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Exercises the classic-for source option through the real cleanup registry. */
public class ClassicForSourceSelectionTest {

	private static final String GIVEN= """
			package test1;
			public class MyTest {
				void process() {
					for (int i = 0; i < 10; i++) {
						System.out.println(i);
					}
				}
			}
			"""; //$NON-NLS-1$

	private static final String EXPECTED= """
			package test1;

			import java.util.stream.IntStream;

			public class MyTest {
				void process() {
					IntStream.range(0, 10).forEach(i -> System.out.println(i));
				}
			}
			"""; //$NON-NLS-1$

	@RegisterExtension
	AbstractEclipseJava context= new EclipseJava22();

	@Test
	public void classicForSourceOptionRunsTheExistingTransformerAndIsIdempotent() throws CoreException {
		configure("stream", true, true); //$NON-NLS-1$
		ICompilationUnit unit= createUnit();
		context.assertRefactoringResultAsExpectedWithFullCompileCheck(
				new ICompilationUnit[] { unit }, new String[] { EXPECTED }, null);
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { unit });
	}

	@Test
	public void disabledClassicForSourceLeavesTheLoopUnchanged() throws CoreException {
		configure("stream", true, false); //$NON-NLS-1$
		assertUnchanged();
	}

	@Test
	public void disabledMasterLeavesTheLoopUnchanged() throws CoreException {
		configure("stream", false, true); //$NON-NLS-1$
		assertUnchanged();
	}

	@Test
	public void enhancedForTargetDoesNotSilentlyProduceAStream() throws CoreException {
		configure("enhanced_for", true, true); //$NON-NLS-1$
		assertUnchanged();
	}

	@Test
	public void iteratorWhileTargetDoesNotSilentlyProduceAStream() throws CoreException {
		configure("iterator_while", true, true); //$NON-NLS-1$
		assertUnchanged();
	}

	@Test
	public void otherSourceOptionsDoNotEnableClassicForConversion() throws CoreException {
		configure("stream", true, false); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
		context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
		assertUnchanged();
	}

	@Test
	public void legacyFunctionalCleanupStillUsesTheExistingTransformer() throws CoreException {
		configure("stream", false, false); //$NON-NLS-1$
		context.enable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.assertRefactoringResultAsExpectedWithFullCompileCheck(
				new ICompilationUnit[] { createUnit() }, new String[] { EXPECTED }, null);
	}

	private void configure(String target, boolean enabled, boolean classicFor) throws CoreException {
		// The legacy switches must not mask a missing connection in the new source selector.
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP);
		context.disable(MYCleanUpConstants.USEFUNCTIONALLOOP_CLEANUP_V2);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ENHANCED_FOR);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_ITERATOR_WHILE);
		context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_STREAM);
		context.set(MYCleanUpConstants.LOOP_CONVERSION_TARGET_FORMAT, target);
		if (enabled) {
			context.enable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		} else {
			context.disable(MYCleanUpConstants.LOOP_CONVERSION_ENABLED);
		}
		if (classicFor) {
			context.enable(MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR);
		} else {
			context.disable(MYCleanUpConstants.LOOP_CONVERSION_FROM_CLASSIC_FOR);
		}
	}

	private ICompilationUnit createUnit() throws CoreException {
		IPackageFragment pack= context.getSourceFolder().createPackageFragment("test1", false, null); //$NON-NLS-1$
		return pack.createCompilationUnit("MyTest.java", GIVEN, false, null); //$NON-NLS-1$
	}

	private void assertUnchanged() throws CoreException {
		context.assertRefactoringHasNoChange(new ICompilationUnit[] { createUnit() });
	}
}
