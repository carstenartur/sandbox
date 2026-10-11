/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0, available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.Platform;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

/** Passive observations for an existing capture, not a paint correction. */
final class JFaceCaptureState {
    private final Map<String, Object> state = new LinkedHashMap<>();
    private long snapshotEndNanos;
    private long captureStartNanos;

    static JFaceCaptureState forImage(String bundle, String fileName) {
        if (!"sandbox_jface_cleanup_help".equals(bundle)) return null;
        return switch (fileName) {
            case "jface-cleanup-real-preview-single-file-steps.png",
                    "jface-cleanup-real-preview-diff-step.png",
                    "jface-cleanup-real-preview-multi-file-selection.png" -> new JFaceCaptureState();
            default -> null;
        };
    }

    void beginSnapshot() {
        timestamp("snapshotStart");
    }

    // Called only within capture()'s existing bounds callback; no SWTBot queries.
    void observe(Shell shell, Rectangle captureBounds) {
        try {
            state.put("captureBounds", new Rectangle(captureBounds.x, captureBounds.y,
                    captureBounds.width, captureBounds.height));
            state.put("shell", controlState(shell));
            state.put("cursorDisplay", shell.getDisplay().getCursorLocation());
            state.put("defaultButton", identity(shell.getDefaultButton()));
            List<Button> buttons = new ArrayList<>();
            collectButtons(shell, buttons);
            List<Map<String, Object>> observations = new ArrayList<>();
            Button back = null;
            Button next = null;
            for (Button button : buttons) {
                Map<String, Object> observation = controlState(button);
                String text = button.getText();
                observation.put("text", text);
                observations.add(observation);
                String label = text.replace("&", "").strip();
                if ("< Back".equals(label) || "Back".equals(label)) back = button;
                if ("Next >".equals(label) || "Next".equals(label)) next = button;
            }
            state.put("buttons", observations);
            state.put("back", identity(back));
            state.put("next", identity(next));
            boolean commonParent = back != null && next != null && back.getParent() == next.getParent();
            state.put("backNextSameParent", commonParent);
            state.put("backNextParent", commonParent ? controlState(back.getParent()) : null);
        } catch (RuntimeException failure) {
            state.put("observationError", failure.toString());
        } finally {
            snapshotEndNanos = timestamp("snapshotEnd");
        }
    }

    private static void collectButtons(Composite parent, List<Button> buttons) {
        for (Control child : parent.getChildren()) {
            if (child instanceof Button button) buttons.add(button);
            if (child instanceof Composite composite) collectButtons(composite, buttons);
        }
    }

    private static Map<String, Object> controlState(Control control) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identity", identity(control));
        result.put("type", control.getClass().getName());
        Rectangle bounds = control.getBounds();
        result.put("bounds", bounds);
        // Shell bounds already use display coordinates, including parented dialog shells.
        Rectangle displayBounds = control instanceof Shell
                ? new Rectangle(bounds.x, bounds.y, bounds.width, bounds.height)
                : control.getDisplay().map(control.getParent(), null, bounds);
        result.put("displayBounds", displayBounds);
        if (control instanceof Composite composite) result.put("clientArea", composite.getClientArea());
        result.put("enabled", control.getEnabled());
        result.put("effectiveEnabled", control.isEnabled());
        result.put("visible", control.isVisible());
        result.put("focus", control.getDisplay().getFocusControl() == control);
        result.put("style", control.getStyle());
        result.put("parent", identity(control.getParent()));
        return result;
    }

    private static Integer identity(Control control) {
        return control == null ? null : System.identityHashCode(control);
    }

    void beginCapture() {
        captureStartNanos = timestamp("captureCallStart");
        state.put("snapshotToCaptureGapNanos", captureStartNanos - snapshotEndNanos);
    }

    void endCapture() {
        state.put("captureCallDurationNanos", timestamp("captureCallEnd") - captureStartNanos);
    }

    private long timestamp(String phase) {
        long nanos = System.nanoTime();
        state.put(phase + "Nanos", nanos);
        state.put(phase + "UtcEpochMillis", System.currentTimeMillis());
        return nanos;
    }

    // Hashing, environment lookup and serialization happen after the original capture.
    void log(Path image) {
        state.put("image", image.toString());
        state.put("schema", "sandbox.jface.capture-state/v1");
        state.put("stateAndPixelsAtomic", false);
        state.put("captureTimingIncludesUiQueueAndCall", true);
        state.put("timingIncludesObservationOverhead", true);
        state.put("swtVersion", SWT.getVersion());
        var swtBundle = Platform.getBundle("org.eclipse.swt");
        state.put("swtBundleVersion", swtBundle == null ? null : swtBundle.getVersion().toString());
        for (String suffix : List.of("gtk.version", "gtk.theme", "gdk.backend", "deviceZoom")) {
            state.put("swt." + suffix, System.getProperty("org.eclipse.swt.internal." + suffix));
        }
        state.put("javaVersion", System.getProperty("java.version"));
        try {
            state.put("screenshotSha256", HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(image))));
        } catch (IOException | NoSuchAlgorithmException failure) {
            state.put("hashError", failure.toString());
        }
        System.out.println("JFACE_CAPTURE_STATE " + json(state));
    }

    private static String json(Object value) {
        if (value == null || value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream().map(entry -> json(entry.getKey()) + ":" + json(entry.getValue()))
                    .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream().map(JFaceCaptureState::json)
                    .collect(java.util.stream.Collectors.joining(",", "[", "]"));
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (char character : value.toString().toCharArray()) {
            if (character == '\"' || character == '\\') escaped.append('\\').append(character);
            else if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
            else escaped.append(character);
        }
        return escaped.append('\"').toString();
    }
}
