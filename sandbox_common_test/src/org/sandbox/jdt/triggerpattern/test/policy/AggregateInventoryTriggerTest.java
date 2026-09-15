/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Aggregate-only membership changes must execute the existing inventory gate. */
class AggregateInventoryTriggerTest {
    @ParameterizedTest
    @ValueSource(strings = { "pull_request", "push" })
    void inventoryWatchesAggregateForEachEvent(String event) throws Exception {
        String file = ".github/workflows/capability-inventory.yml";
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (root != null && !Files.isRegularFile(root.resolve(file))) root = root.getParent();
        assertTrue(root != null, "Cannot locate the inventory workflow");
        String workflow = Files.readString(root.resolve(file));
        var block = Pattern.compile("(?ms)^  " + Pattern.quote(event)
                + ":\\R(.*?)(?=^  \\S|^\\S|\\z)").matcher(workflow);
        assertTrue(block.find(), "Missing event " + event);
        var paths = Pattern.compile("(?ms)^    paths:\\R(.*?)(?=^    \\S|\\z)").matcher(block.group(1));
        assertTrue(paths.find(), "Missing paths for " + event);
        assertTrue(paths.group(1).lines().map(String::strip)
                .anyMatch(line -> line.equals("- 'sandbox_feature/feature.xml'")),
                event + " must explicitly watch the aggregate; sandbox_*_feature does not match sandbox_feature");
    }
}
