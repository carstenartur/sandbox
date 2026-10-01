/* Copyright (c) 2026 Carsten Hammer and others. SPDX-License-Identifier: EPL-2.0 */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Applies one experimental reporting-boundary correction to the pinned Apache source.
 * The Apache license header and every unrelated method remain unchanged.
 * No generated source is added to the normal Sandbox product.
 */
class PrepareAdapter {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Expected input and output Java paths");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        byte[] bytes = Files.readAllBytes(input);
        MessageDigest git = MessageDigest.getInstance("SHA-1");
        git.update(("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8));
        String blob = HexFormat.of().formatHex(git.digest(bytes));
        if (!blob.equals("6efd5efc84a0b647365d96b61fbe60d8966f979e")) {
            throw new IllegalStateException("Unexpected upstream RunListenerAdapter blob: " + blob);
        }
        String original = new String(bytes, StandardCharsets.UTF_8);
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
        Files.createDirectories(output.getParent());
        Files.writeString(output, result, StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.getBytes(StandardCharsets.UTF_8)));
        Files.writeString(output.resolveSibling("patch-provenance.txt"),
                "upstreamGitBlob=" + blob + "\npatchedSourceSHA256=" + sha + "\n");
        System.out.println("Applied experimental report-owner patch; upstream=" + blob + "; outputSHA256=" + sha);
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
