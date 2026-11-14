/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 *
 * This program and the accompanying materials are made available under the terms
 * of the Eclipse Public License 2.0 which accompanies this distribution, and is
 * available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.container.analysis.ContainerRuleRegistry;
import org.sandbox.jdt.container.api.ContainerRuleDescriptor.RuleOwnership;
import org.sandbox.jdt.internal.corext.fix2.MYCleanUpConstants;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;

/** Checks the report against actual source edits made by the registered owning cleanups. */
class ContainerCleanupOverlapTest {
	@RegisterExtension
	final AbstractEclipseJava context= new AbstractEclipseJava(null, JavaCore.VERSION_17) {
		@Override
		public IClasspathEntry[] getDefaultClasspath() {
			return new IClasspathEntry[] { JavaCore.newContainerEntry(IPath.fromPortableString("org.eclipse.jdt.launching.JRE_CONTAINER")) }; //$NON-NLS-1$
		}
	};

	@Test
	void collectionCopyConstructor() throws Exception {
		String before= """
				package sample;
				import java.util.ArrayList;
				import java.util.List;
				class Sample {
				    List<String> copy(List<String> input) {
				        List<String> output = new ArrayList<>();
				        output.addAll(input);
				        return output;
				    }
				}
				""";
		check(ContainerRuleRegistry.COLLECTION_COPY_CONSTRUCTOR, CleanUpConstants.COLLECTION_CLONING, before,
				before.replace("new ArrayList<>();\n        output.addAll(input);", "new ArrayList<>(input);")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void collectionBulkAdd() throws Exception {
		String before= """
				package sample;
				import java.util.List;
				class Sample {
				    void append(List<String> input, List<String> output) {
				        for (String s : input) { output.add(s); }
				    }
				}
				""";
		check(ContainerRuleRegistry.COLLECTION_BULK_ADD, CleanUpConstants.CONTROL_STATEMENTS_USE_ADD_ALL, before,
				before.replace("for (String s : input) { output.add(s); }", "output.addAll(input);")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void uniformArrayFill() throws Exception {
		String before= """
				package sample;
				import java.util.Arrays;
				class Sample {
				    void fill(String[] input) {
				        for (int i = 0; i < input.length; i++) { input[i] = "x"; }
				    }
				}
				""";
		check(ContainerRuleRegistry.ARRAY_FILL, CleanUpConstants.ARRAYS_FILL, before,
				before.replace("for (int i = 0; i < input.length; i++) { input[i] = \"x\"; }", "Arrays.fill(input, \"x\");")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	@Test
	void ordinaryTypeGeneralization() throws Exception {
		String before= """
				package sample;
				import java.util.HashMap;
				import java.util.Map;
				class Sample {
				    void process() {
				        HashMap<String, Integer> map = new HashMap<>();
				        map.put("a", 1);
				        map.get("a");
				    }
				}
				""";
		check(ContainerRuleRegistry.TYPE_GENERALIZATION, MYCleanUpConstants.USE_GENERAL_TYPE_CLEANUP, before,
				before.replace("HashMap<String, Integer> map", "Map<String, Integer> map")); //$NON-NLS-1$ //$NON-NLS-2$
	}

	private void check(String descriptor, String option, String before, String after) throws Exception {
		assertEquals(RuleOwnership.DUPLICATE, ContainerRuleRegistry.find(descriptor).orElseThrow().ownership());
		var pack= context.getSourceFolder().createPackageFragment("sample", false, null); //$NON-NLS-1$
		ICompilationUnit unit= pack.createCompilationUnit("Sample.java", before, false, null); //$NON-NLS-1$
		assertNoCompetingProposal(unit);
		context.enable(option);
		context.assertRefactoringResultAsExpectedNormalizingWhitespace(new ICompilationUnit[] { unit }, new String[] { after }, null);
		assertNoCompetingProposal(unit);
	}

	private static void assertNoCompetingProposal(ICompilationUnit unit) throws Exception {
		String source= unit.getSource();
		var rows= new ContainerAnalysisService().analyze(unit, null);
		assertTrue(rows.stream().allMatch(row -> "No proven target".equals(row.targetContract())), rows::toString); //$NON-NLS-1$
		assertEquals(source, unit.getSource());
	}
}
