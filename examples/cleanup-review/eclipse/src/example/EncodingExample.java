package example;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Intentionally retains two before-forms for the cleanup walkthrough. */
public final class EncodingExample {
    private EncodingExample() {
    }

    public static Charset utf8() {
        return StandardCharsets.UTF_8;
    }

    public static void main(String[] args) {
        String text = "\u00e4";
        System.out.println(text.getBytes(utf8()).length + ":" + text.getBytes(latin1()).length);
    }

    // The import and both uses must be proposed together.
    // Existing project configuration must remain unchanged.
    public static Charset latin1() {
        return StandardCharsets.ISO_8859_1;
    }
}
