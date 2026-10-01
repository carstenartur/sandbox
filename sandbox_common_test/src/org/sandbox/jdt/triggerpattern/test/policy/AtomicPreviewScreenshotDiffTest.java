/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.triggerpattern.test.policy;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Execute the actual read-only screenshot gate, including its negative cases. */
class AtomicPreviewScreenshotDiffTest {
    private static final int BACKGROUND = 0xffeeeeee;
    private static final int REPAINT = 0xffececec;
    @TempDir
    static Path verifierClasses;

    @TempDir
    Path temporary;

    @BeforeAll
    static void compileActualVerifier() throws Exception {
        Path classes = verifierClasses;
        String relative = ".github/scripts/VerifyAtomicPreviewScreenshotDiff.java"; //$NON-NLS-1$
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(); //$NON-NLS-1$
        while (root != null && !Files.isRegularFile(root.resolve(relative))) {
            root = root.getParent();
        }
        assertTrue(root != null, "Cannot locate the actual screenshot verifier"); //$NON-NLS-1$
        Path output = classes.resolve("compile.log"); //$NON-NLS-1$
        int exit = execute(List.of(tool("javac"), "-d", classes.toString(), //$NON-NLS-1$ //$NON-NLS-2$
                root.resolve(relative).toString()), output);
        assertEquals(0, exit, Files.readString(output));
        verifierClasses = classes;
    }

    @ParameterizedTest
    @ValueSource(ints = { 1280, 2057 })
    void acceptsIdenticalImagesAndBoundedWidgetRepainting(int width) throws Exception {
        BufferedImage baseline = image(width, 900);
        BufferedImage generated = image(width, 900);
        verify(baseline, generated, true, "Accepted 0"); //$NON-NLS-1$
        generated.setRGB(width - 350, 854, REPAINT);
        generated.setRGB(width - 29, width == 2057 ? 327 : 438, REPAINT);
        verify(baseline, generated, true, "Accepted 2"); //$NON-NLS-1$
    }

    @ParameterizedTest
    @ValueSource(ints = { 1280, 2057 })
    void rejectsContentChangesEvenAtOneChannelStep(int width) throws Exception {
        // Header, candidate tree, safety text, source text, and footer.
        int[][] content = { { 100, 40 }, { 100, 120 }, { 100, 390 },
                { 750, 500 }, { 100, 860 }, { width - 31, 500 }, { width - 22, 500 } };
        for (int[] point : content) {
            BufferedImage generated = image(width, 900);
            generated.setRGB(point[0], point[1], BACKGROUND - 1);
            verify(image(width, 900), generated, false, "outside the bounded GTK widget regions"); //$NON-NLS-1$
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1280, 2057 })
    void rejectsChangesJustOutsideTheMeasuredWidgets(int width) throws Exception {
        int top = width == 2057 ? 327 : 438;
        int[][] points = { { width - 468, 854 }, { width - 6, 854 },
                { width - 350, 845 }, { width - 350, 891 },
                { width - 29, top - 1 }, { width - 29, 756 } };
        for (int[] point : points) {
            BufferedImage generated = image(width, 900);
            generated.setRGB(point[0], point[1], REPAINT);
            verify(image(width, 900), generated, false, "outside the bounded GTK widget regions"); //$NON-NLS-1$
        }
    }

    @ParameterizedTest
    @ValueSource(ints = { 1280, 2057 })
    void retainsColorAndAlphaLimits(int width) throws Exception {
        BufferedImage generated = image(width, 900);
        generated.setRGB(width - 350, 854, 0xffd6eeee); // Red delta 24 is the existing limit.
        verify(image(width, 900), generated, true, "maximum channel delta 24"); //$NON-NLS-1$
        generated.setRGB(width - 350, 854, 0xffd5eeee);
        verify(image(width, 900), generated, false, "exceeding the GTK repaint allowance"); //$NON-NLS-1$
        generated.setRGB(width - 350, 854, 0xe6eeeeee); // Alpha delta 25 must also fail.
        verify(image(width, 900), generated, false, "exceeding the GTK repaint allowance"); //$NON-NLS-1$
    }

    @ParameterizedTest
    @ValueSource(ints = { 1280, 2057 })
    void retainsChangedPixelLimit(int width) throws Exception {
        BufferedImage generated = image(width, 900);
        for (int i = 0; i < 16_000; i++) {
            generated.setRGB(width - 467 + i % 461, 846 + i / 461, REPAINT);
        }
        verify(image(width, 900), generated, true, "Accepted 16000"); //$NON-NLS-1$
        generated.setRGB(width - 467 + 16_000 % 461, 846 + 16_000 / 461, REPAINT);
        verify(image(width, 900), generated, false, "Too many changed pixels"); //$NON-NLS-1$
    }

    @Test
    void rejectsTheOldButtonPositionInTheWideLayout() throws Exception {
        BufferedImage generated = image(2057, 900);
        generated.setRGB(930, 854, REPAINT);
        verify(image(2057, 900), generated, false, "outside the bounded GTK widget regions"); //$NON-NLS-1$
    }

    @Test
    void doesNotExtrapolateToUnknownLayoutsOrAcceptResizing() throws Exception {
        for (int[] size : new int[][] { { 2058, 900 }, { 2057, 901 } }) {
            BufferedImage generated = image(size[0], size[1]);
            generated.setRGB(size[0] - 350, 854, REPAINT);
            verify(image(size[0], size[1]), generated, false, "outside the bounded GTK widget regions"); //$NON-NLS-1$
        }
        verify(image(1280, 900), image(2057, 900), false, "Image dimensions changed"); //$NON-NLS-1$
    }

    private void verify(BufferedImage baseline, BufferedImage generated,
            boolean accepted, String diagnostic) throws Exception {
        Path before = temporary.resolve("baseline.png"); //$NON-NLS-1$
        Path after = temporary.resolve("generated.png"); //$NON-NLS-1$
        assertTrue(ImageIO.write(baseline, "png", before.toFile())); //$NON-NLS-1$
        assertTrue(ImageIO.write(generated, "png", after.toFile())); //$NON-NLS-1$
        byte[] originalBaseline = Files.readAllBytes(before);
        byte[] originalGenerated = Files.readAllBytes(after);
        Path output = temporary.resolve("comparison.log"); //$NON-NLS-1$
        int exit = execute(List.of(tool("java"), "-cp", verifierClasses.toString(), //$NON-NLS-1$ //$NON-NLS-2$
                "VerifyAtomicPreviewScreenshotDiff", before.toString(), after.toString()), output); //$NON-NLS-1$
        String message = Files.readString(output);
        assertEquals(accepted ? 0 : 1, exit, message);
        assertTrue(message.contains(diagnostic), message);
        assertArrayEquals(originalBaseline, Files.readAllBytes(before), "Baseline must be read-only"); //$NON-NLS-1$
        assertArrayEquals(originalGenerated, Files.readAllBytes(after), "Capture must be read-only"); //$NON-NLS-1$
    }

    private static BufferedImage image(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        int[] row = new int[width];
        Arrays.fill(row, BACKGROUND);
        for (int y = 0; y < height; y++) {
            image.setRGB(0, y, width, 1, row, 0, width);
        }
        return image;
    }

    private static String tool(String name) {
        Path executable = Path.of(System.getProperty("java.home"), "bin", name); //$NON-NLS-1$ //$NON-NLS-2$
        if (!Files.isRegularFile(executable)) {
            executable = executable.resolveSibling(name + ".exe"); //$NON-NLS-1$
        }
        assertTrue(Files.isRegularFile(executable), executable.toString());
        return executable.toString();
    }

    private static int execute(List<String> command, Path output) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(output.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Verifier process timed out: " + command); //$NON-NLS-1$
            return process.exitValue();
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
