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
    private static final String PDE_XML_SCREENSHOT = "xml-cleanup-marker-quick-fix.png"; //$NON-NLS-1$
    private static final String PDE_XML_PROVENANCE =
            "xml-cleanup-marker-quick-fix.provenance.json"; //$NON-NLS-1$

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

    @Test
    public void pdeXmlMarkerQuickFixEvidenceIsPinnedAndReferenced() throws Exception {
        Path repository = SandboxCheckout.locate(null);
        Path helpBundle = repository.resolve("sandbox_xml_cleanup_help"); //$NON-NLS-1$
        Path image = helpBundle.resolve("images").resolve(PDE_XML_SCREENSHOT); //$NON-NLS-1$
        assertTrue(Files.isRegularFile(image) && Files.size(image) > 0,
                () -> "Missing PDE XML Problems marker and Quick Fix screenshot: " + image); //$NON-NLS-1$
        assertTrue(allHtml(helpBundle.resolve("html")).contains(PDE_XML_SCREENSHOT), //$NON-NLS-1$
                () -> "PDE XML Problems marker and Quick Fix screenshot is not referenced from Help: " //$NON-NLS-1$
                        + image);

        BufferedImage screenshot = requireImage(image);
        assertTrue(screenshot.getWidth() >= 1_200 && screenshot.getHeight() >= 700,
                () -> "The PDE XML Problems view and Quick Fix are not fully visible in " + image); //$NON-NLS-1$

        Path provenanceFile = helpBundle.resolve("images").resolve(PDE_XML_PROVENANCE); //$NON-NLS-1$
        assertTrue(Files.isRegularFile(provenanceFile) && Files.size(provenanceFile) > 0,
                () -> "Missing PDE XML screenshot provenance: " + provenanceFile); //$NON-NLS-1$
        assertTrue(allHtml(helpBundle.resolve("html")).contains(PDE_XML_PROVENANCE), //$NON-NLS-1$
                () -> "PDE XML screenshot provenance is not referenced from Help: " //$NON-NLS-1$
                        + provenanceFile);

        String provenance = Files.readString(provenanceFile, StandardCharsets.UTF_8);
        assertEquals(1, jsonLong(provenance, "schemaVersion")); //$NON-NLS-1$
        assertEquals("eclipse-jdt/eclipse.jdt.ui", jsonString(provenance, "repository")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals("R4_40", jsonString(provenance, "ref")); //$NON-NLS-1$ //$NON-NLS-2$
        assertEquals(readPin(repository, "PIN_JDT_UI_COMMIT"), //$NON-NLS-1$
                jsonString(provenance, "commit")); //$NON-NLS-1$
        assertEquals("org.eclipse.jdt.ui/schema/cleanUps.exsd", //$NON-NLS-1$
                jsonString(provenance, "sourcePath")); //$NON-NLS-1$
        assertEquals("Problems view marker and Quick Fix", //$NON-NLS-1$
                jsonString(provenance, "scenario")); //$NON-NLS-1$
        String sourceDigest = jsonString(provenance, "sourceSha256"); //$NON-NLS-1$
        assertTrue(sourceDigest.matches("[0-9a-f]{64}"), //$NON-NLS-1$
                () -> "Invalid pinned PDE XML source digest in " + provenanceFile); //$NON-NLS-1$
        assertTrue(jsonLong(provenance, "sourceBytes") > 2_000, //$NON-NLS-1$
                () -> "PDE XML screenshot provenance does not identify a substantial real schema"); //$NON-NLS-1$
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

    private static String readPin(Path repository, String key) throws IOException {
        String prefix = key + '=';
        for (String line : Files.readAllLines(repository.resolve("qa/upstream-jdt/pins.env"), //$NON-NLS-1$
                StandardCharsets.UTF_8)) {
            if (line.startsWith(prefix)) {
                String value = line.substring(prefix.length()).strip();
                assertFalse(value.isEmpty(), () -> "Empty upstream pin " + key); //$NON-NLS-1$
                return value;
            }
        }
        throw new AssertionError("Missing upstream pin " + key); //$NON-NLS-1$
    }

    private static String jsonString(String json, String property) {
        String prefix = '"' + property + "\": \""; //$NON-NLS-1$
        int start = json.indexOf(prefix);
        assertTrue(start >= 0, () -> "Missing JSON string property " + property); //$NON-NLS-1$
        start += prefix.length();
        int end = json.indexOf('"', start);
        assertTrue(end > start, () -> "Invalid JSON string property " + property); //$NON-NLS-1$
        return json.substring(start, end);
    }

    private static long jsonLong(String json, String property) {
        String prefix = '"' + property + "\": "; //$NON-NLS-1$
        int start = json.indexOf(prefix);
        assertTrue(start >= 0, () -> "Missing JSON integer property " + property); //$NON-NLS-1$
        start += prefix.length();
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end))) {
            end++;
        }
        assertTrue(end > start, () -> "Invalid JSON integer property " + property); //$NON-NLS-1$
        return Long.parseLong(json.substring(start, end));
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
