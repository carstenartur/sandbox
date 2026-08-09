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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

/** Verifies regenerated Eclipse Help screenshots without masking UI changes. */
public final class CompareHelpScreenshots {

    private static final double MAX_CHANGED_FRACTION = 0.005;
    private static final int MAX_CHANNEL_DELTA = 8;
    private static final int MIN_CHANGED_PIXEL_ALLOWANCE = 64;

    private CompareHelpScreenshots() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 2) {
            throw new IllegalArgumentException(
                    "Usage: CompareHelpScreenshots <baseline-root> <checkout-root>");
        }
        Path baselineRoot = Path.of(arguments[0]).toAbsolutePath().normalize();
        Path checkoutRoot = Path.of(arguments[1]).toAbsolutePath().normalize();
        Set<Path> baselines = images(baselineRoot);
        Set<Path> generated = images(checkoutRoot);
        Set<Path> missing = new HashSet<>(baselines);
        missing.removeAll(generated);
        Set<Path> unexpected = new HashSet<>(generated);
        unexpected.removeAll(baselines);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw new AssertionError("Missing screenshots: " + missing
                    + "; unexpected screenshots: " + unexpected);
        }
        boolean failed = false;
        for (Path relative : baselines.stream().sorted().toList()) {
            failed |= !matches(baselineRoot.resolve(relative),
                    checkoutRoot.resolve(relative), relative);
        }
        if (failed) {
            throw new AssertionError("Eclipse Help screenshot verification failed");
        }
    }

    private static Set<Path> images(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            throw new IOException("Screenshot root does not exist: " + root);
        }
        Set<Path> result = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root, 4)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                Path relative = root.relativize(path);
                String normalized = relative.toString().replace('\\', '/');
                if (normalized.matches("sandbox.*_help/images/[^/]+\\.png")) {
                    result.add(relative);
                }
            });
        }
        return result;
    }

    private static boolean matches(Path baselinePath, Path generatedPath,
            Path relative) throws IOException {
        BufferedImage baseline = read(baselinePath);
        BufferedImage generated = read(generatedPath);
        if (baseline.getWidth() != generated.getWidth()
                || baseline.getHeight() != generated.getHeight()) {
            System.err.println(relative + ": dimensions changed from "
                    + baseline.getWidth() + "x" + baseline.getHeight() + " to "
                    + generated.getWidth() + "x" + generated.getHeight());
            return false;
        }
        long changedPixels = 0;
        int maxDelta = 0;
        for (int y = 0; y < baseline.getHeight(); y++) {
            for (int x = 0; x < baseline.getWidth(); x++) {
                int expected = baseline.getRGB(x, y);
                int actual = generated.getRGB(x, y);
                if (expected == actual) {
                    continue;
                }
                changedPixels++;
                for (int shift : new int[] { 24, 16, 8, 0 }) {
                    maxDelta = Math.max(maxDelta,
                            Math.abs(((expected >>> shift) & 0xff)
                                    - ((actual >>> shift) & 0xff)));
                }
            }
        }
        long totalPixels = (long) baseline.getWidth() * baseline.getHeight();
        long allowance = Math.max(MIN_CHANGED_PIXEL_ALLOWANCE,
                Math.round(totalPixels * MAX_CHANGED_FRACTION));
        String metrics = String.format(Locale.ROOT,
                "%s: %,d of %,d pixels differ (%.4f%%), max channel delta %d",
                relative, changedPixels, totalPixels,
                100.0 * changedPixels / totalPixels, maxDelta);
        if (changedPixels == 0) {
            System.out.println(relative + ": exact match");
            return true;
        }
        if (changedPixels <= allowance && maxDelta <= MAX_CHANNEL_DELTA) {
            System.out.println(metrics
                    + " — accepted as bounded GTK rasterization noise");
            return true;
        }
        System.err.println(metrics + "; allowance is " + allowance
                + " pixels and max channel delta " + MAX_CHANNEL_DELTA);
        return false;
    }

    private static BufferedImage read(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            throw new IOException("Not a readable PNG image: " + path);
        }
        return image;
    }
}
