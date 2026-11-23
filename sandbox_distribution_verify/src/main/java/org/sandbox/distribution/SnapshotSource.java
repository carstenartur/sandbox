/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.distribution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/** Single-source Java entry point used before the distribution reactor is built. */
final class SnapshotSource {
    static String qualifier(long height, String sha) throws IOException {
        if (height < 1 || height > 999_999_999_999L || !sha.matches("[0-9a-f]{40}")) {
            throw new IOException("Invalid snapshot identity " + height + "/" + sha);
        }
        return String.format(Locale.ROOT, "v%012d-g%s", height, sha);
    }

    record Revision(String sha, long height) {}

    static Revision revision(Path checkout, String sha) throws Exception {
        if (!sha.matches("[0-9a-f]{40}")) throw new IOException("Invalid source SHA " + sha);
        long height = Long.parseLong(git(checkout, "rev-list", "--first-parent", "--count", sha));
        qualifier(height, sha);
        return new Revision(sha, height);
    }

    static void requireLineage(Path checkout, Revision ancestor, Revision descendant) throws Exception {
        long distance = descendant.height() - ancestor.height();
        if (distance < 0 || !git(checkout, "rev-parse", descendant.sha() + '~' + distance).equals(ancestor.sha())) {
            throw new IOException("Source " + ancestor.sha() + " is outside first-parent ancestry of " + descendant.sha());
        }
    }

    static Revision selected(Path checkout, String expected) throws Exception {
        if (!"true".equals(git(checkout, "rev-parse", "--is-inside-work-tree"))
                || !"false".equals(git(checkout, "rev-parse", "--is-shallow-repository"))
                || !git(checkout, "rev-parse", "HEAD").equals(expected)) throw new IOException("Expected full checkout at " + expected);
        String modified = git(checkout, "diff", "--name-only", "HEAD", "--");
        if (!modified.isEmpty()) throw new IOException("Tracked source differs from " + expected + ": " + modified);
        var source = revision(checkout, expected);
        requireLineage(checkout, source, revision(checkout, git(checkout, "rev-parse", "refs/remotes/origin/main")));
        return source;
    }

    static String git(Path checkout, String... args) throws Exception {
        var cmd = new java.util.ArrayList<>(List.of("git"));
        cmd.addAll(List.of(args));
        Process p = new ProcessBuilder(cmd).directory(checkout.toFile()).redirectErrorStream(true).start();
        String output;
        try (var stream = p.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).strip();
        }
        if (p.waitFor() != 0) throw new IOException("Git command failed: " + cmd + ": " + output);
        return output;
    }

    public static void main(String[] args) throws Exception {
        var checkout = Path.of(".");
        var source = selected(checkout, args[0]);
        System.out.println("SUPPORT_QUALIFIER=" + qualifier(source.height(), source.sha()));
        System.out.println("SOURCE_EPOCH=" + git(checkout, "show", "-s", "--format=%ct", args[0]));
    }

    private SnapshotSource() {}
}
