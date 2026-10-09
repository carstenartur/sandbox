package example;

import java.nio.charset.Charset;

/** Intentionally retains two before-forms for the cleanup walkthrough. */
public final class EncodingExample {
    private EncodingExample() {
    }

    public static Charset utf8() {
        return Charset.forName("UTF-8");
    }

    public static void main(String[] args) {
        String text = "\u00e4";
        System.out.println(text.getBytes(utf8()).length + ":" + text.getBytes(latin1()).length);
    }

    // The import and both uses must be proposed together.
    // Existing project configuration must remain unchanged.
    public static Charset latin1() {
        return Charset.forName("ISO-8859-1");
    }
}
