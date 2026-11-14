/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.wizard;

import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.dom.AST;

/** Emits an executable Eclipse JUnit test for the wizard's actual rule and examples. */
final class RuleRegressionTestSource {

	private RuleRegressionTestSource() {
	}

	static String forHint(String workspacePath) {
		var path = new Path(workspacePath);
		if (!path.isAbsolute() || path.segmentCount() < 2) {
			throw new IllegalArgumentException("Expected a hint file in a workspace project"); //$NON-NLS-1$
		}
		var literal = AST.newAST(AST.getJLSLatest(), false)
				.newStringLiteral();
		literal.setLiteralValue(workspacePath);
		return """
				import static org.junit.jupiter.api.Assertions.assertTrue;

				import java.io.IOException;
				import java.nio.charset.StandardCharsets;
				import org.eclipse.core.resources.IFile;
				import org.eclipse.core.resources.ResourcesPlugin;
				import org.eclipse.core.runtime.CoreException;
				import org.eclipse.core.runtime.NullProgressMonitor;
				import org.eclipse.core.runtime.Path;
				import org.eclipse.jdt.core.JavaCore;
				import org.junit.jupiter.api.Test;
				import org.sandbox.jdt.triggerpattern.api.RuleExampleSuite;
				import org.sandbox.jdt.triggerpattern.eclipse.ProjectRuleExamples;

				class RuleRegressionTest {
				    @Test
				    void replaysSavedExamples() throws Exception {
				        var root = ResourcesPlugin.getWorkspace().getRoot();
				        var path = new Path(HINT_PATH);
				        IFile hint = root.getFile(path);
				        String rule = utf8(hint);
				        IFile sidecar = root.getFile(path.addFileExtension("examples.json"));
				        var examples = RuleExampleSuite.fromJson(utf8(sidecar));
				        var report = ProjectRuleExamples.withVerifier(JavaCore.create(hint.getProject()),
				                new NullProgressMonitor(), verifier -> verifier.verify(rule, examples));
				        assertTrue(report.passed(), report::toString);
				    }

				    private static String utf8(IFile file) throws IOException, CoreException {
				        try (var input = file.getContents()) {
				            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
				        }
				    }
				}
				""".replace("HINT_PATH", literal.getEscapedValue()); //$NON-NLS-1$ //$NON-NLS-2$
	}
}
