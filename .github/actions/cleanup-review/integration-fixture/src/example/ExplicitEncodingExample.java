package example;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Intentionally contains the conservative before-form used by the PR review
 * workflow smoke test. The workflow should suggest StandardCharsets.UTF_8.
 */
public final class ExplicitEncodingExample {
    private ExplicitEncodingExample() {
    }

    public static Charset utf8() {
        return StandardCharsets.UTF_8;
    }

    /** Keep the import outside this PR's diff context to exercise full-patch review. */
    public static String describe() {
        return "Existing file: the conversion and its import must remain together.";
    }
}
