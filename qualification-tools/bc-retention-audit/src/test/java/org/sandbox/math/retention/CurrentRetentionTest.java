package org.sandbox.math.retention;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Opt-in, exact-source audit. A skipped method is never evidence of current retention. */
@EnabledIfSystemProperty(named="retention.currentRepo",matches=".+")
class CurrentRetentionTest {
    private static Path path(String key) { return Path.of(System.getProperty("retention."+key)).toAbsolutePath().normalize(); }
    @ParameterizedTest @CsvSource({"bc-strict,146","bc-mathematical,149"})
    void retainsEveryPublishedBaselineRegion(String mode,int expectedBaseline) throws Exception {
        Path repo=path("currentRepo"), baselineRoot=path("baselineRoot"), baseline=baselineRoot.resolve(mode);
        Path current=repo.resolve("qa/math-optimization/target").resolve(mode), profile=repo.resolve("qa/math-optimization/profiles").resolve(mode+".properties");
        Path sources=path("sources"), binary=path("binary"), zip=path("baselineZip");
        String head=System.getProperty("retention.expectedHead"), tree=System.getProperty("retention.expectedTree");
        RetentionAudit.verifyGit(repo,head,tree);
        RetentionAudit.verifyBaseline(zip,"a37d6bf5ab2962184b051c806d6a7382e040816d0c4224547f127f8aa1f7e1ed",baselineRoot);
        RetentionAudit.requireHash(sources,"b37ac84b1d5435ab7b8d166c16ab9f75e09f68f8ec50479bae433939b241b03f");
        RetentionAudit.requireHash(binary,"986b0fb92ec10e0c66b43e036ce0077e6150cfaecd1db9fb92b56672e157afe5");
        var old=RetentionAudit.report(baseline); var now=RetentionAudit.report(current);
        for(String key:List.of("sourcesSha256","binarySha256","targetJava","javaRuntime","selectedEntries","parsedFiles","parseErrorFiles"))
            assertEquals(old.path(key),now.path(key),"Changed pinned scan input/coverage: "+key);
        assertEquals(3136,now.path("selectedEntries").size()); assertEquals(3126,now.path("parsedFiles").asInt()); assertEquals(10,now.path("parseErrorFiles").asInt());
        assertEquals(System.getProperty("java.runtime.version"),now.path("javaRuntime").asText());
        assertEquals("17",now.path("targetJava").asText());
        RetentionAudit.verifyProfile((ObjectNode)old.path("options"),(ObjectNode)now.path("options"),profile,now.path("profileSha256").asText());
        assertEquals(parseErrors(old),parseErrors(now),"Changed failed source entries or diagnostics");
        assertEquals(jars(old.path("runtimeInputs")),jars(now.path("runtimeInputs")),"External runtime JAR versions/bytes changed");
        var runtime=(ObjectNode)now.path("runtimeInputs");
        for(String type:List.of("org.sandbox.jdt.internal.corext.fix.math.MathReviewReport","org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis","org.sandbox.math.qa.ProjectMathScan"))
            assertEquals(repo.resolve("qa/math-optimization/target/classes").toString(),runtime.path("loaded:"+type).asText(),"Foreign compiled adapter origin");
        Path sdk=repo.resolve("sandbox_math_cleanup/lib/regelsuche-optimization-sdk.jar");
        assertEquals(sdk.toString(),runtime.path("loaded:de.regelsuche.sdk.optimization.ComputationOptimizer").asText());
        RetentionAudit.requireHash(sdk,"ba0293dedb3fde9ca2f3042dcef8ae4d6bb2d576adedff8ba7a2b4d186f1cbe1");
        var before=RetentionAudit.readIndex(baseline,sources,false); var after=RetentionAudit.readIndex(current,sources,true);
        assertEquals(expectedBaseline,before.size()); var result=RetentionAudit.compare(before,after);
        result.put("schema","sandbox.bc-retention-audit/v1"); result.put("profile",mode); result.put("head",head); result.put("tree",tree);
        result.put("baselineReviewSha256",RetentionAudit.hash(baseline.resolve("review.json"))); result.put("currentReviewSha256",RetentionAudit.hash(current.resolve("review.json")));
        result.put("baselineProfileSha256",old.path("profileSha256").asText()); result.put("currentProfileSha256",now.path("profileSha256").asText());
        result.put("sourceArchiveSha256",now.path("sourcesSha256").asText()); result.put("binarySha256",now.path("binarySha256").asText());
        result.put("sdkSha256",RetentionAudit.hash(sdk)); result.put("selectedEntries",3136); result.put("parsedFiles",3126); result.put("parseErrorFiles",10);
        result.set("currentRuntimeInputs",runtime); result.put("baselineZipSha256",RetentionAudit.hash(zip));
        RetentionAudit.verifyRuntime(runtime); RetentionAudit.verifyGit(repo,head,tree);
        Path output=path("output"); Files.createDirectories(output); Path directory=Files.createDirectory(output.resolve(mode));
        Files.copy(baseline.resolve("review.json"),directory.resolve("baseline-review.json")); Files.copy(current.resolve("review.json"),directory.resolve("current-review.json"));
        RetentionAudit.JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("retention.json").toFile(),result);
        var summary=RetentionAudit.JSON.createObjectNode();
        for(String key:List.of("schema","profile","head","tree","baselineRegions","currentRegions","retained","lost","costsUnchanged","baselineReviewSha256","currentReviewSha256","baselineProfileSha256","currentProfileSha256","sdkSha256","selectedEntries","parsedFiles","parseErrorFiles")) summary.set(key,result.path(key));
        for(String key:List.of("additions","replacementChanges","costChanges","proofChanges","otherEvidenceChanges")) summary.put(key,result.path(key).size());
        RetentionAudit.JSON.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("summary.json").toFile(),summary);
        System.out.println(summary);
        assertTrue(result.path("costsUnchanged").asBoolean(),"Retained-region cost changes require explicit review; see retention.json");
    }
    private static Map<String,String> jars(JsonNode runtime) {
        var result=new TreeMap<String,String>();
        runtime.properties().forEach(e->{
            if(e.getKey().startsWith("loaded:") || !e.getKey().endsWith(".jar") || e.getKey().endsWith("/regelsuche-optimization-sdk.jar")) return;
            String filename=Path.of(e.getKey()).getFileName().toString(); assertNull(result.put(filename,e.getValue().asText()),"Duplicate runtime JAR basename");
        });
        return result;
    }
    private static Map<String,JsonNode> parseErrors(ObjectNode report) {
        var result=new TreeMap<String,JsonNode>(); for(var file:report.path("files")) if("PARSE_ERROR".equals(file.path("status").asText())) result.put(file.path("path").asText(),file.path("parseErrors")); return result;
    }
}
