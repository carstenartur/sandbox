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
package org.sandbox.mining.scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.core.JavaCore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.triggerpattern.api.GuardFunction;
import org.sandbox.jdt.triggerpattern.api.GuardFunctionResolverHolder;
import org.sandbox.jdt.triggerpattern.api.HintFile;
import org.sandbox.jdt.triggerpattern.internal.BuiltInGuards;
import org.sandbox.jdt.triggerpattern.internal.HintFileParser;
import org.sandbox.mining.report.MiningReport;

class SourceScannerTest {

	@TempDir
	Path tempDir;

	@BeforeEach
	void setUp() {
		java.util.HashMap<String, GuardFunction> guards = new java.util.HashMap<>();
		BuiltInGuards.registerAll(guards);
		GuardFunctionResolverHolder.setResolver(guards::get);
	}

	@Test
	void forwardsCompilerOptionsToSourceVersionGuards() throws Exception {
		Files.writeString(tempDir.resolve("Test.java"), //$NON-NLS-1$
				"class Test { Object m() { return java.util.Collections.emptyList(); } }", //$NON-NLS-1$
				StandardCharsets.UTF_8);
		HintFile hintFile = new HintFileParser().parse("""
				<!id: source-version-test>
				@id: source-version-test.empty-list
				@severity: info
				java.util.Collections.emptyList() :: sourceVersionGE(9)
				=> java.util.List.of()
				;;
				"""); //$NON-NLS-1$
		SourceScanner scanner = new SourceScanner(new StandaloneAstParser(), 100);

		MiningReport java8Report = scanner.scan("repo", tempDir, List.of(), List.of(hintFile), //$NON-NLS-1$
				Map.of(JavaCore.COMPILER_SOURCE, "1.8")); //$NON-NLS-1$
		MiningReport java21Report = scanner.scan("repo", tempDir, List.of(), List.of(hintFile), //$NON-NLS-1$
				Map.of(JavaCore.COMPILER_SOURCE, "21")); //$NON-NLS-1$

		assertEquals(0, java8Report.getMatches().size());
		assertEquals(1, java21Report.getMatches().size());
		assertEquals("source-version-test.empty-list", java21Report.getMatches().get(0).ruleName()); //$NON-NLS-1$
	}
}
