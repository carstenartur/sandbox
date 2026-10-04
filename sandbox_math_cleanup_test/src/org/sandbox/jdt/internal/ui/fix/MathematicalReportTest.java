package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

class MathematicalReportTest {
 @Test void observedPartialSourceMustNotBeReportedAsUnapplied() throws Exception {
  String source="class C { int r = x + 0; }";int offset=source.indexOf("x + 0");
  var analysis=new MathematicalAnalysis.Analysis(List.of(new MathematicalAnalysis.Replacement(offset,5,"x","verified")),List.of(),MathematicalEnvironment.digest(source),Map.of());
  var file=MathematicalReport.planned("C.java",source,Map.of(),MathCleanUpOptions.defaults(17),new MathematicalEnvironment.Snapshot("environment",Map.of()),analysis,false);
  var observed=file.withObserved("partial source");assertTrue(observed.applied());assertEquals("OTHER",observed.applicationStatus());assertEquals(MathematicalEnvironment.digest("partial source"),observed.observedSourceSha256());
  var restored=file.withObserved(source);assertFalse(restored.applied());assertEquals("ORIGINAL",restored.applicationStatus());
  var unavailable=file.withObserved(null);assertEquals("UNAVAILABLE",unavailable.applicationStatus());assertNull(unavailable.observedSourceSha256());
 }
 @Test void analysisReportHashesTheCompleteProposedFileWithoutClaimingApplication() throws Exception {
  String source="class C { int r = x + 0; }";int offset=source.indexOf("x + 0");
  var analysis=new MathematicalAnalysis.Analysis(List.of(new MathematicalAnalysis.Replacement(offset,5,"x","verified")),List.of(),MathematicalEnvironment.digest(source),Map.of());
  var file=MathematicalReport.planned("src/C.java",source,Map.of(),MathCleanUpOptions.defaults(17),new MathematicalEnvironment.Snapshot("environment",Map.of()),analysis,false);
  assertEquals("class C { int r = x; }",file.replacement());assertEquals(MathematicalEnvironment.digest(file.replacement()),file.afterSha256());
  assertEquals(MathematicalEnvironment.digest(source),file.sourceSha256());assertEquals(source,file.original());assertFalse(file.applied());
 }
 @Test void staleSourceCannotCreateConvincingEvidence() {
  var analysis=new MathematicalAnalysis.Analysis(List.of(),List.of(),"wrong digest",Map.of());
  assertThrows(IllegalArgumentException.class,()->MathematicalReport.planned("C.java","source",Map.of(),MathCleanUpOptions.defaults(17),new MathematicalEnvironment.Snapshot("environment",Map.of()),analysis,false));
 }
 @Test void jsonCarriesTheExactNormalizedOptionsAndStructuredArrays(@TempDir Path temporary) throws Exception {
  var options=MathCleanUpOptions.defaults(17).toMap();String source="class C {}";
  var analysis=new MathematicalAnalysis.Analysis(List.of(),List.of(new MathematicalAnalysis.Diagnostic("NO_CHANGE","unchanged",0,source.length())),MathematicalEnvironment.digest(source),Map.of());
  var file=MathematicalReport.planned("C.java",source,Map.of(),MathCleanUpOptions.defaults(17),new MathematicalEnvironment.Snapshot("environment",Map.of()),analysis,false);
  var report=new MathematicalReport(1,"Example","analysis",options,options,"sdk","adapter",List.of(file));
  Path output=temporary.resolve("report.json");report.write(output);
  var json=new ObjectMapper().readTree(Files.readString(output));
  assertEquals(1,json.path("schemaVersion").asInt());assertEquals(9,json.path("requestedOptions").size());
  assertEquals(json.path("requestedOptions"),json.path("configProperties"));assertEquals(json.path("requestedOptions"),json.path("files").get(0).path("options"));
  assertTrue(json.path("files").get(0).path("changes").isArray());assertTrue(json.path("files").get(0).path("evidence").isArray());
  assertEquals("NO_CHANGE",json.path("files").get(0).path("diagnostics").get(0).path("code").asText());
 }
}
