package org.sandbox.jdt.internal.corext.fix.helper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

public class SchemaTransformationUtils {
	public static String transform(Path schemaPath) throws Exception {
        // Lade die formatter.xsl-Datei aus dem Classpath
        try (InputStream xslStream = SchemaTransformationUtils.class.getClassLoader().getResourceAsStream("resources/formatter.xsl")) {
            if (xslStream == null) {
                throw new IllegalArgumentException("Unable to find formatter.xsl in resources.");
            }

            // Initialisiere den Transformer mit der geladenen XSL-Datei
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer(new StreamSource(xslStream));

            // Transformation ausführen
            StreamSource source = new StreamSource(schemaPath.toFile());
            Path tempOutput = Files.createTempFile("formatted-schema", ".xsd");
            StreamResult result = new StreamResult(tempOutput.toFile());

            transformer.transform(source, result);

            // Transformierten Inhalt zurückgeben
            return Files.readString(tempOutput);
        }
    }
}
