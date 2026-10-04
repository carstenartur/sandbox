import java.nio.file.Files;
import java.nio.file.Path;

class CaptureMathReceipts {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        Class<?> runner = Class.forName("org.sandbox.math.qa.MathCorpusRunner");
        for (String name : new String[] { "implementationReceipt", "repositoryReceipt" }) {
            var method = runner.getDeclaredMethod(name);
            method.setAccessible(true);
            String file = name.equals("implementationReceipt")
                    ? "implementation.properties" : "sandbox-source.properties";
            Files.writeString(output.resolve(file), (String) method.invoke(null));
        }
    }
}
