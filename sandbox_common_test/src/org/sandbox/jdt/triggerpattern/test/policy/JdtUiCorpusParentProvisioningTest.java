/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Transport contract, not evidence that the upstream JDT tests ran. */
class JdtUiCorpusParentProvisioningTest {
    private static final String COMMIT = "0cc158ecd52bb57974704dc74dbacd81dbc01b99"; //$NON-NLS-1$
    private static final String POM_BLOB = "e115e24efa7fb500bd6494bae1e8642205ca512e"; //$NON-NLS-1$

    @Test void matchingReleaseParentUsesAnImmutableSparseCheckout() throws Exception {
        String workflow = workflow();
        assertTrue(workflow.contains("repository: eclipse-platform/eclipse.platform.releng.aggregator")); //$NON-NLS-1$
        assertTrue(workflow.contains("ref: " + COMMIT)); //$NON-NLS-1$
        assertTrue(workflow.contains("sparse-checkout: eclipse-platform-parent")); //$NON-NLS-1$
        assertTrue(workflow.contains("path: upstream/platform-releng")); //$NON-NLS-1$
    }

    @Test void parentAndSourceAreVerifiedBeforeUsingTheDeclaredRelativePath() throws Exception {
        String workflow = workflow();
        int start = workflow.indexOf("- name: Verify and expose the frozen platform parent"); //$NON-NLS-1$
        int end = workflow.indexOf("- name: Set up Java 25"); //$NON-NLS-1$
        assertTrue(start >= 0 && start < end, "Provisioning must precede every upstream build"); //$NON-NLS-1$
        String step = workflow.substring(start, end);
        assertTrue(step.contains("set -euo pipefail")); //$NON-NLS-1$
        assertTrue(step.contains("test \"$(git -C upstream/platform-releng rev-parse HEAD)\" = \"" + COMMIT + "\"")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(step.contains("test \"$(git hash-object \"$parent/pom.xml\")\" = \"" + POM_BLOB + "\"")); //$NON-NLS-1$ //$NON-NLS-2$
        assertTrue(step.contains("ln -s platform-releng/eclipse-platform-parent upstream/eclipse-platform-parent")); //$NON-NLS-1$
        assertTrue(step.contains("test -f upstream/eclipse-platform-parent/pom.xml")); //$NON-NLS-1$
    }

    @Test void parentEvidenceAndTheOriginalMigrationGatesRemain() throws Exception {
        String workflow = workflow();
        assertTrue(workflow.contains("jdt-ui-strict-evidence/platform-parent.txt")); //$NON-NLS-1$
        assertTrue(workflow.contains("jdt-ui-strict-evidence/**")); //$NON-NLS-1$
        assertTrue(workflow.contains("ref: R4_40")); //$NON-NLS-1$
        assertTrue(workflow.contains("run-jdt-ui-before-after.sh")); //$NON-NLS-1$
        assertTrue(workflow.contains("--mode strict")); //$NON-NLS-1$
        assertTrue(workflow.contains("VERIFYING_WHITESPACE_REGRESSIONS")); //$NON-NLS-1$
    }

    private static String workflow() throws Exception {
        Path root = Path.of("").toAbsolutePath(); //$NON-NLS-1$
        while (root != null) {
            Path file = root.resolve(".github/workflows/jdt-ui-junit4-strict-qa.yml"); //$NON-NLS-1$
            if (Files.isRegularFile(file)) return Files.readString(file, StandardCharsets.UTF_8);
            root = root.getParent();
        }
        throw new IllegalStateException("Repository workflow not found"); //$NON-NLS-1$
    }
}
