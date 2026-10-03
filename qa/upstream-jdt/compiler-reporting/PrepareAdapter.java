/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Source-checked investigation patches; not installed in the Sandbox product. */
class PrepareAdapter {
    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4) throw new IllegalArgumentException("Expected adapter input/output and optional reporter input/output");
        String original = readPinned(Path.of(args[0]), "6efd5efc84a0b647365d96b61fbe60d8966f979e");
        String result = replaceOnce(original,
                "            runListener.testSetStarting(createReportEntry(testIdentifier));",
                "            if (isReportContainer(testIdentifier)) {\n"
                + "                runListener.testSetStarting(createReportEntry(testIdentifier));\n"
                + "            }");
        int start = result.indexOf("    public void executionFinished(");
        int end = result.indexOf("    private Integer computeElapsedTime(");
        if (start < 0 || end <= start) throw new IllegalStateException("Missing executionFinished boundary");
        String finish = result.substring(start, end);
        String completion = "                    } else {\n                        runListener.testSetCompleted(";
        if (occurrences(finish, completion) != 2) throw new IllegalStateException("Unexpected completion branches");
        finish = finish.replace(completion,
                "                    } else if (isReportContainer(testIdentifier)) {\n                        runListener.testSetCompleted(");
        finish = replaceOnce(finish, "if (isClass || isRootContainer)",
                "if (isReportContainer(testIdentifier) || isRootContainer)");
        String helper = "    // Match the report owner selected by toClassMethodName(). Nested class\n"
                + "    // and class-template completion events must not flush its whole history.\n"
                + "    private boolean isReportContainer(TestIdentifier testIdentifier) {\n"
                + "        return testIdentifier.equals(findTopParent(testIdentifier));\n"
                + "    }\n\n";
        result = result.substring(0, start) + finish + helper + result.substring(end);
        result = replaceOnce(result, "            runListener.testSetStarting(report);",
                "            if (isReportContainer(testIdentifier)) {\n                runListener.testSetStarting(report);\n            }");
        result = replaceOnce(result, "            runListener.testSetCompleted(report);",
                "            if (isReportContainer(testIdentifier)) {\n                runListener.testSetCompleted(report);\n            }");
        write(Path.of(args[1]), result, "6efd5efc84a0b647365d96b61fbe60d8966f979e");
        if (args.length == 4) {
            String reporter = readPinned(Path.of(args[2]), "17547db9bcbd964bcf8729eff12b0624f2ddf067");
            String loop = "        for (Map<String, List<WrappedReportEntry>> methodStats : classMethodStatistics.values()) {\n";
            // The no-rerun serializer writes every entry. Its suite totals must count
            // those same entries, not conflate repeated invocations with retry histories.
            String counts = loop + "            if (rerunFailingTestsCount == 0) {\n"
                    + "                for (List<WrappedReportEntry> runs : methodStats.values()) {\n"
                    + "                    for (WrappedReportEntry run : runs) {\n"
                    + "                        actualTestCount++;\n"
                    + "                        switch (run.getReportEntryType()) {\n"
                    + "                            case ERROR: errors++; break;\n"
                    + "                            case FAILURE: failures++; break;\n"
                    + "                            case SKIPPED: skipped++; break;\n"
                    + "                            default: break;\n"
                    + "                        }\n"
                    + "                    }\n"
                    + "                }\n"
                    + "                continue;\n"
                    + "            }\n";
            write(Path.of(args[3]), replaceOnce(reporter, loop, counts), "17547db9bcbd964bcf8729eff12b0624f2ddf067");
        }
    }
    private static String readPinned(Path path, String expected) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        MessageDigest git = MessageDigest.getInstance("SHA-1");
        git.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
        String blob = HexFormat.of().formatHex(git.digest(bytes));
        if (!blob.equals(expected)) throw new IllegalStateException("Unexpected upstream blob for " + path + ": " + blob);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private static void write(Path path, String result, String sourceBlob) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(path, result, StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(path.resolveSibling(path.getFileName() + ".provenance"), "upstreamGitBlob=" + sourceBlob + "\npatchedSourceSHA256=" + sha + "\n");
        System.out.println("Patched " + path.getFileName() + "; source=" + sourceBlob + "; SHA256=" + sha);
    }
    private static String replaceOnce(String source, String old, String replacement) {
        if (occurrences(source, old) != 1) throw new IllegalStateException("Expected one occurrence of: " + old);
        return source.replace(old, replacement);
    }
    private static int occurrences(String source, String text) {
        int count = 0;
        for (int from = 0; (from = source.indexOf(text, from)) >= 0; from += text.length()) count++;
        return count;
    }
}
