package example;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Both conversions intentionally use their before-form. The cleanup commit
 * must contain both conversions and their new StandardCharsets import.
 */
public final class GroupedEncodingExample {
    private GroupedEncodingExample() {
    }

    public static Charset utf8() {
        return StandardCharsets.UTF_8;
    }

    public static String describe() {
        return "One suggestion must include both conversions and their import.";
    }

    public static Charset latin1() {
        return StandardCharsets.ISO_8859_1;
    }
}
