package example;

import java.nio.charset.Charset;

/**
 * Both conversions intentionally use their before-form. A new PR file exposes
 * the complete range, including the StandardCharsets import added by cleanup.
 */
public final class GroupedEncodingExample {
    private GroupedEncodingExample() {
    }

    public static Charset utf8() {
        return Charset.forName("UTF-8");
    }

    public static String describe() {
        return "One suggestion must include both conversions and their import.";
    }

    public static Charset latin1() {
        return Charset.forName("ISO-8859-1");
    }
}
