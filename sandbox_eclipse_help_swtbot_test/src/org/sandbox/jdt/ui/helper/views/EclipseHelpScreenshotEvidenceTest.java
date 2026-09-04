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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Truthfulness contract for user-facing Help screenshots.
 *
 * <p>Every shipped PNG must be referenced from its Help bundle. The real JFace
 * execution-preview images must be separate captures and must not alias the
 * cleanup-profile configuration screenshot. Semantic UI assertions remain in
 * the SWTBot generator; this test protects the committed evidence in every
 * normal Maven build.</p>
 */
public class EclipseHelpScreenshotEvidenceTest {

    private static final List<String> JFACE_REAL_PREVIEWS = List.of(
            "jface-cleanup-real-preview-single-file-steps.png", //$NON-NLS-1$
            "jface-cleanup-real-preview-diff-step.png", //$NON-NLS-1$
            "jface-cleanup-real-preview-multi-file-selection.png"); //$NON-NLS-1$

    @Test
    public void everyShippedScreenshotIsReferencedFromItsHelpBundle() throws Exception {
        Path repository = SandboxCheckout.locate(null);
        try (Stream<Path> bundles = Files.list(repository)) {
            for (Path helpBundle : bundles.filter(Files::isDirectory)
                    .filter(path -> pathFileName(path).startsWith("sandbox")) //$NON-NLS-1$
                    .filter(path -> pathFileName(path).endsWith("_help")) //$NON-NLS-1$
                    .toList()) {
                assertEveryPngIsReferenced(helpBundle);
            }
        }
    }

    @Test
    public void realJfacePreviewImagesAreDistinctFullDialogCaptures() throws Exception {
        Path repository = SandboxCheckout.locate(null);
        Path imageDirectory = repository.resolve("sandbox_jface_cleanup_help/images"); //$NON-NLS-1$
        Path configuration = imageDirectory.resolve("jface-cleanup.png"); //$NON-NLS-1$
        byte[] configurationBytes = Files.readAllBytes(configuration);
        BufferedImage configurationImage = requireImage(configuration);

        Set<String> digests = new HashSet<>();
        for (String fileName : JFACE_REAL_PREVIEWS) {
            Path preview = imageDirectory.resolve(fileName);
            byte[] previewBytes = Files.readAllBytes(preview);
            assertFalse(java.util.Arrays.equals(configurationBytes, previewBytes),
                    () -> preview + " aliases the cleanup-profile configuration screenshot"); //$NON-NLS-1$
            assertTrue(digests.add(sha256(previewBytes)),
                    () -> "JFace real-preview screenshots are byte-identical: " + preview); //$NON-NLS-1$

            BufferedImage previewImage = requireImage(preview);
            assertEquals(configurationImage.getWidth(), previewImage.getWidth(),
                    () -> "Unexpected screenshot width for " + preview); //$NON-NLS-1$
            assertEquals(configurationImage.getHeight(), previewImage.getHeight(),
                    () -> "Unexpected screenshot height for " + preview); //$NON-NLS-1$
            assertTrue(previewImage.getWidth() >= 1_000 && previewImage.getHeight() >= 700,
                    () -> "The full Cleanup preview dialog is not visible in " + preview); //$NON-NLS-1$
        }
    }

    @Test
    public void coordinatedPreviewEvidenceIsInstalledAndReferenced() throws Exception {
        Path repository = SandboxCheckout.locate(null);
        requireReferenced(repository, "sandbox_int_to_enum_help", //$NON-NLS-1$
                "int-to-enum-coordinated-preview.png"); //$NON-NLS-1$
        requireReferenced(repository, "sandbox_junit_cleanup_help", //$NON-NLS-1$
                "junit-coordinated-preview.png"); //$NON-NLS-1$
    }

    private static void assertEveryPngIsReferenced(Path helpBundle) throws IOException {
        Path imageDirectory = helpBundle.resolve("images"); //$NON-NLS-1$
        if (!Files.isDirectory(imageDirectory)) {
            return;
        }
        String html = allHtml(helpBundle.resolve("html")); //$NON-NLS-1$
        try (Stream<Path> images = Files.list(imageDirectory)) {
            for (Path image : images.filter(Files::isRegularFile)
                    .filter(path -> pathFileName(path).endsWith(".png")) //$NON-NLS-1$
                    .toList()) {
                assertTrue(Files.size(image) > 0, () -> "Empty Help screenshot " + image); //$NON-NLS-1$
                String fileName = pathFileName(image);
                assertTrue(html.contains(fileName),
                        () -> "Shipped Help screenshot is not referenced by any HTML page: " + image); //$NON-NLS-1$
            }
        }
    }

    private static void requireReferenced(Path repository, String bundle, String fileName)
            throws IOException {
        Path helpBundle = repository.resolve(bundle);
        Path image = helpBundle.resolve("images").resolve(fileName); //$NON-NLS-1$
        assertTrue(Files.isRegularFile(image) && Files.size(image) > 0,
                () -> "Missing coordinated Cleanup preview evidence: " + image); //$NON-NLS-1$
        assertTrue(allHtml(helpBundle.resolve("html")).contains(fileName), //$NON-NLS-1$
                () -> "Coordinated Cleanup preview is not referenced from Help: " + image); //$NON-NLS-1$
    }

    private static String allHtml(Path htmlDirectory) throws IOException {
        if (!Files.isDirectory(htmlDirectory)) {
            return ""; //$NON-NLS-1$
        }
        StringBuilder result = new StringBuilder();
        try (Stream<Path> pages = Files.walk(htmlDirectory)) {
            for (Path page : pages.filter(Files::isRegularFile)
                    .filter(path -> pathFileName(path).endsWith(".html")) //$NON-NLS-1$
                    .toList()) {
                result.append(Files.readString(page, StandardCharsets.UTF_8)).append('\n');
            }
        }
        return result.toString();
    }

    private static String pathFileName(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("Path has no file name: " + path); //$NON-NLS-1$
        }
        return fileName.toString();
    }

    private static BufferedImage requireImage(Path image) throws IOException {
        BufferedImage decoded = ImageIO.read(image.toFile());
        assertNotNull(decoded, () -> "Unreadable Help screenshot " + image); //$NON-NLS-1$
        return decoded;
    }

    private static String sha256(byte[] content) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(content); //$NON-NLS-1$
        return java.util.HexFormat.of().formatHex(digest);
    }
}
