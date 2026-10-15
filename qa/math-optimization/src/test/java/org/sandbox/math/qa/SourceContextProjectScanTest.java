/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import com.fasterxml.jackson.databind.ObjectMapper;

class SourceContextProjectScanTest {
    @TempDir Path directory;
    @Test void selectedScanUsesOtherSourceDefinitionsWithoutCountingThemAsScanned() throws Exception {
        Path binary=archive("binary.jar", List.of()), sources=archive("sources.jar", List.of(
            new Source("sample/Constants.java", "package sample; import java.math.BigInteger; public class Constants { public static final BigInteger Q = new BigInteger(\"1fffffff\",16); }"),
            new Source("sample/Bridge.java", "package sample; import java.math.BigInteger; public class Bridge { public static final BigInteger P = Constants.Q; }"),
            new Source("sample/Sample.java", """
                package sample;
                import java.math.BigInteger;
                public class Sample {
                    private static final BigInteger P = Bridge.P;
                    public byte[] calculate(byte digit) {
                        byte[] bytes = new byte[48];
                        bytes[47] = digit;
                        BigInteger x = new BigInteger(1, bytes);
                        if (x.compareTo(P) >= 0) { throw new IllegalArgumentException("range"); }
                        BigInteger result = x.multiply(x).mod(P).multiply(x).mod(P)
                            .add(BigInteger.valueOf(4)).mod(P);
                        return result.toByteArray();
                    }
                }
                """)));
        Path output=directory.resolve("result");
        Path profile=profile();
        String sourceHash=CorpusFiles.sha256(sources),binaryHash=CorpusFiles.sha256(binary);
        ProjectMathScan.main(new String[]{binary.toString(),sources.toString(),profile.toString(),output.toString(),sourceHash,"17","sample/Sample.java"});
        var report=new ObjectMapper().readTree(output.resolve("review.json").toFile());
        assertEquals("COMPLETED",report.path("status").asText());
        assertEquals(1,report.path("parsedFiles").asInt());
        assertEquals(0,report.path("parseErrorFiles").asInt());
        assertEquals(1,report.path("acceptedRegions").asInt());
        assertEquals(2,report.path("files").get(0).path("sourceDependencies").size());
        String generated=Files.readString(output.resolve("generated/sample/Sample.java"));
        assertFalse(generated.contains("getClass()"),generated);
        assertTrue(generated.contains(".add(BigInteger.valueOf(4)).mod(P)"),generated);
        assertTrue(generated.contains("result reference identity may change"),"Ordinary Apply keeps the existing contract documentation");
        String review=Files.readString(output.resolve("review-only/sample/Sample.java"));
        String original=Files.readString(output.resolve("original/sample/Sample.java"));
        assertEquals(original.replace("x.multiply(x).mod(P).multiply(x).mod(P)","x.multiply(x).multiply(x)"),review);
        assertTrue(report.path("reviewOnlyWarning").asText().contains("contract"));
        assertTrue(report.path("files").get(0).path("reviewOnlySourceSha256").asText().matches("[0-9a-f]{64}"));
        assertEquals(sourceHash,CorpusFiles.sha256(sources));assertEquals(binaryHash,CorpusFiles.sha256(binary));
    }
    @Test void aChangedOrAdditionalSnapshotFileCannotBeQualified() throws Exception {
        Path root=Files.createDirectory(directory.resolve("snapshot")), source=root.resolve("Sample.java");
        Files.writeString(source,"class Sample {}\n");
        var hashes=java.util.Map.of(source.toAbsolutePath().normalize().toString(),CorpusFiles.sha256(source));
        assertDoesNotThrow(() -> ProjectMathScan.verifySourceSnapshot(root,hashes));
        Files.writeString(source,"class Other {}\n");
        assertThrows(java.io.IOException.class,() -> ProjectMathScan.verifySourceSnapshot(root,hashes));
        Files.writeString(source,"class Sample {}\n");
        Files.writeString(root.resolve("New.java"),"class New {}\n");
        assertThrows(java.io.IOException.class,() -> ProjectMathScan.verifySourceSnapshot(root,hashes));
    }
    private Path profile() throws Exception {
        var values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());
        values.put(MathCleanUpOptions.CLEANUP,"true");values.put(MathCleanUpOptions.KINDS,"BIG_INTEGER,INT,BYTE");
        values.put(MathCleanUpOptions.WORK_BUDGET,"1000000");values.put(MathCleanUpOptions.MAX_STATES,"20000");
        values.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"true");values.put(MathCleanUpOptions.EXPLANATIONS,"NONE");
        var properties=new Properties();properties.putAll(values);Path result=directory.resolve("profile.properties");
        try(var stream=Files.newOutputStream(result)){properties.store(stream,"Explicit test profile");}
        return result;
    }
    private record Source(String path,String text) { }
    private Path archive(String name,List<Source> sources) throws Exception {
        Path path=directory.resolve(name);
        try(var output=new JarOutputStream(Files.newOutputStream(path))) {
            for(var source:sources){output.putNextEntry(new JarEntry(source.path()));output.write(source.text().getBytes(StandardCharsets.UTF_8));output.closeEntry();}
        }
        return path;
    }
}
