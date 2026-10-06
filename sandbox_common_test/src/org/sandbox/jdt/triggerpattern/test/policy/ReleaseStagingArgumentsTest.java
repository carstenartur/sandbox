/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Release fixtures must exercise Git without depending on Bash or WSL. */
class ReleaseStagingArgumentsTest {
    @Test
    void retainsTheQuotedExclusionAsOneLiteralGitArgument() {
        assertEquals(List.of("git", "add", "-u", "--", ":!.github/workflows/"),
                ReleaseVersionContractTest.stagingArguments("git add -u -- ':!.github/workflows/'"));
    }

    @Test
    void doesNotSilentlyReplaceARegressedWorkflowCommand() {
        // The existing real-repository fixtures must still detect staging scratch files.
        assertEquals(List.of("git", "add", "-A", "--", ":!.github/workflows/"),
                ReleaseVersionContractTest.stagingArguments("git add -A -- ':!.github/workflows/'"));
        assertEquals(List.of("git", "add", "-u", "--", ":!different directory/"),
                ReleaseVersionContractTest.stagingArguments("git add -u -- ':!different directory/'"));
    }

    @Test
    void rejectsSyntaxTheDirectProcessAdapterCannotFaithfullyExecute() {
        String command = "git add -u -- ':!.github/workflows/'";
        for (String unsupported : List.of(command + " && git status", command + " # comment",
                command + "\ngit status", "# " + command, "git add -u -- $PATH",
                "git add -u -- :!.github/workflows/")) {
            assertThrows(AssertionError.class,
                    () -> ReleaseVersionContractTest.stagingArguments(unsupported), unsupported);
        }
    }
}
