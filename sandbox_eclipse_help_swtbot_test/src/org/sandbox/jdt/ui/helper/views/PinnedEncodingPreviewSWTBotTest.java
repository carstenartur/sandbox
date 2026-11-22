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
package org.sandbox.jdt.ui.helper.views;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import org.junit.jupiter.api.Test;

/** Compare the two explicit encoding policies on the same genuine fallback at R4_40. */
public class PinnedEncodingPreviewSWTBotTest {
    private static final String SOURCE = "ui/org/eclipse/jdt/internal/ui/compare/JavaCompareUtilities.java";
    private static final String BEFORE = "return s.getBytes();";
    private static final Map<String, String> CONTROLS = Map.of(
            "core extension/org/eclipse/jdt/internal/corext/util/History.java", "StandardCharsets.UTF_8",
            "ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java", "word.getBytes(UTF_8)");

    @Test
    public void preservesPlatformDefaultInPinnedFallback() throws Exception {
        compareFallback(false);
    }

    @Test
    public void setsUtf8InPinnedFallback() throws Exception {
        compareFallback(true);
    }

    private static void compareFallback(boolean preferUtf8) throws Exception {
        String name = preferUtf8 ? "encoding-utf8" : "encoding-keep";
        Path evidence = Path.of(System.getenv("SANDBOX_STATUS_EVIDENCE"), name);
        PinnedEncodingInventory.assertControls(evidence);
        var project = ResourcesPlugin.getWorkspace().getRoot().getProject("org.eclipse.jdt.ui");
        Path root = project.getLocation().toFile().toPath().toRealPath();
        Path checkout = java.util.Objects.requireNonNull(root.getParent(), "Missing retained checkout");
        try (Git git = Git.open(checkout.toFile()); RevWalk walk = new RevWalk(git.getRepository())) {
            var baseline = walk.parseCommit(git.getRepository().resolve("HEAD"));
            for (var control : CONTROLS.entrySet()) {
                Path path = root.resolve(control.getKey());
                try (var tree = TreeWalk.forPath(git.getRepository(), project.getName() + "/" + control.getKey(), baseline.getTree())) {
                    org.junit.jupiter.api.Assertions.assertNotNull(tree, control.getKey());
                    org.junit.jupiter.api.Assertions.assertArrayEquals(git.getRepository().open(tree.getObjectId(0)).getBytes(),
                            Files.readAllBytes(path), control.getKey());
                    assertTrue(Files.readString(path, StandardCharsets.UTF_8).contains(control.getValue()), control.getKey());
                }
            }
            Path reader = root.resolve(PinnedEncodingInventory.READER_SOURCE);
            try (var tree = TreeWalk.forPath(git.getRepository(), project.getName() + "/" + PinnedEncodingInventory.READER_SOURCE,
                    baseline.getTree())) {
                org.junit.jupiter.api.Assertions.assertNotNull(tree, PinnedEncodingInventory.READER_SOURCE);
                byte[] original = git.getRepository().open(tree.getObjectId(0)).getBytes();
                org.junit.jupiter.api.Assertions.assertArrayEquals(original, Files.readAllBytes(reader),
                        "The FileReader check must leave its pinned source unchanged");
                PinnedEncodingInventory.assertReader(evidence, reader, original, preferUtf8);
            }
        }
        String charset = preferUtf8 ? "StandardCharsets.UTF_8" : "Charset.defaultCharset()";
        String after = "return s.getBytes(" + charset + ");";
        var scenario = new CleanupScreenshotScenario("pinned-" + name, PreviewContract.FILE_COMBINED_DIFF,
                "org.eclipse.jdt.ui", Set.of(SOURCE), Set.of(SOURCE));
        PinnedFilePreviewDriver.compare(scenario, SOURCE, name + ".properties", "java.nio.charset.Charset",
                BEFORE, after, baseline -> {
                    assertTrue(baseline.contains("return s.getBytes(encoding);"));
                    assertTrue(baseline.contains(BEFORE));
                    String expected = baseline.replace(BEFORE, after);
                    return preferUtf8 ? expected.replace("import java.nio.charset.Charset;",
                            "import java.nio.charset.Charset;\nimport java.nio.charset.StandardCharsets;") : expected;
                }, name, name + "-pinned", "sandbox_encoding_quickfix_help");
    }
}
