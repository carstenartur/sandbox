package example;

import java.nio.charset.Charset;

/**
 * Intentionally contains the conservative before-form used by the PR review
 * workflow smoke test. The workflow should suggest StandardCharsets.UTF_8.
 */
public final class ExplicitEncodingExample {
    private ExplicitEncodingExample() {
    }

    public static Charset utf8() {
        return Charset.forName("UTF-8");
    }
}
