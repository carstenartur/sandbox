package org.sandbox.math.retention;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.fasterxml.jackson.databind.node.ObjectNode;

class RetentionAuditTest {
    @TempDir Path temp;
    private static ObjectNode node(String json) throws Exception { return (ObjectNode) RetentionAudit.JSON.readTree(json); }
    private static RetentionAudit.Region region(String replacement, int cost) throws Exception {
        var evidence = node("{\"offset\":8,\"length\":4,\"original\":\"x+x;\",\"proof\":{\"sourceHash\":\"same\"},\"cost\":{\"sourceScore\":3,\"candidateScore\":"+cost+"}}");
        evidence.put("replacement", replacement);
        return new RetentionAudit.Region("A.java", 8, 4, "source", evidence);
    }
    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
    private Path file(String name, String text) throws Exception {
        Path p=temp.resolve(name); Files.createDirectories(p.getParent()); Files.writeString(p,text); return p;
    }
    @Test void retainsExactRegionAndReportsAddedRegion() throws Exception {
        var a=region("x*2;",2); var b=new RetentionAudit.Region("B.java",2,4,"other",a.evidence());
        var result=RetentionAudit.compare(Map.of("A.java:8:4",a),Map.of("A.java:8:4",a,"B.java:2:4",b));
        assertEquals(1,result.path("retained").asInt()); assertEquals(1,result.path("additions").size());
        assertEquals(0,result.path("replacementChanges").size()); assertEquals(0,result.path("costChanges").size());
    }
    @Test void rejectsDroppedBaselineRegion() throws Exception {
        var a=region("x*2;",2); var b=new RetentionAudit.Region("B.java",8,4,"other",a.evidence());
        assertThrows(IllegalStateException.class,()->RetentionAudit.compare(Map.of("A.java:8:4",a),Map.of("B.java:8:4",b)));
    }
    @Test void rejectsChangedOriginalSourceIdentity() throws Exception {
        var a=region("x*2;",2); var drift=new RetentionAudit.Region(a.path(),a.offset(),a.length(),"wrong-source",a.evidence());
        assertThrows(IllegalStateException.class,()->RetentionAudit.compare(Map.of("A.java:8:4",a),Map.of("A.java:8:4",drift)));
    }
    @Test void rejectsChangedOriginalSpanEvenIfIdentityKeyIsReused() throws Exception {
        var a=region("x*2;",2); var b=region("x*2;",2); ((ObjectNode)b.evidence()).put("original","y+y;");
        assertThrows(IllegalStateException.class,()->RetentionAudit.compare(Map.of("A.java:8:4",a),Map.of("A.java:8:4",b)));
    }
    @Test void exposesReplacementCostAndProofChangesWithoutCallingThemIdentical() throws Exception {
        var a=region("x*2;",2); var b=region("x<<1;",1); ((ObjectNode)b.evidence().path("proof")).put("checker","new");
        var result=RetentionAudit.compare(Map.of("A.java:8:4",a),Map.of("A.java:8:4",b));
        assertEquals(1,result.path("replacementChanges").size()); assertEquals(1,result.path("costChanges").size()); assertEquals(1,result.path("proofChanges").size());
        assertFalse(result.path("costsUnchanged").asBoolean());
    }
    @Test void rejectsSourceBytesThatNoLongerMatchReceipt() throws Exception {
        Path p=file("source.java","original"); String sha=hash(p); RetentionAudit.requireHash(p,sha);
        Files.writeString(p,"changed"); assertThrows(IllegalStateException.class,()->RetentionAudit.requireHash(p,sha));
    }
    @Test void rejectsExtractedEvidenceThatDiffersFromPinnedBaselineZip() throws Exception {
        Path evidence=file("baseline/bc-strict/evidence/A.java.json","{\"proof\":\"original\"}");
        Path zip=temp.resolve("baseline.zip");
        try(var out=new ZipOutputStream(Files.newOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("bc-strict/evidence/A.java.json")); out.write(Files.readAllBytes(evidence)); out.closeEntry();
        }
        String zipSha=hash(zip); RetentionAudit.verifyBaseline(zip,zipSha,temp.resolve("baseline"));
        Files.writeString(evidence,"{\"proof\":\"forged\"}");
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyBaseline(zip,zipSha,temp.resolve("baseline")));
    }
    @Test void rejectsRuntimeBytecodeDrift() throws Exception {
        Path p=file("classes/p/A.class","class bytes"); var inputs=node("{}").put(p.toString(),hash(p)).put("loaded:p.A",p.getParent().getParent().toString());
        RetentionAudit.verifyRuntime(inputs); Files.writeString(p,"different bytes");
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyRuntime(inputs));
    }
    @Test void rejectsUnreceiptedLoadedClass() throws Exception {
        Path p=file("classes/p/A.class","class bytes"); var inputs=node("{}").put("loaded:p.A",p.getParent().getParent().toString());
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyRuntime(inputs));
    }
    @Test void acceptsOnlyExplicitSafeNewProfileDefaultsAndKindReordering() throws Exception {
        var old=node("{\"cleanup.mathematics.numericKinds\":\"INT,LONG\",\"cleanup.mathematics.workBudget\":\"1000\"}");
        var now=old.deepCopy().put("cleanup.mathematics.numericKinds","LONG,INT").put("cleanup.mathematics.tailRecursion","false").put("cleanup.mathematics.valueRuntime","UNSPECIFIED");
        Path profile=file("profile.properties","cleanup.mathematics.numericKinds=LONG,INT\ncleanup.mathematics.workBudget=1000\ncleanup.mathematics.tailRecursion=false\ncleanup.mathematics.valueRuntime=UNSPECIFIED\n");
        RetentionAudit.verifyProfile(old,now,profile,hash(profile));
        now.put("cleanup.mathematics.workBudget","1001");
        Files.writeString(profile,Files.readString(profile).replace("1000","1001"));
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyProfile(old,now,profile,hash(profile)));
    }
    @Test void rejectsNewTailConsentRatherThanDiscardingItsProfileKey() throws Exception {
        var old=node("{\"cleanup.mathematics.workBudget\":\"1000\"}");
        var now=old.deepCopy().put("cleanup.mathematics.tailRecursion","true");
        Path profile=file("profile.properties","cleanup.mathematics.workBudget=1000\ncleanup.mathematics.tailRecursion=true\n");
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyProfile(old,now,profile,hash(profile)));
    }
    @Test void rejectsChangedProfileFileEvenWhenOptionsReceiptIsUnchanged() throws Exception {
        var options=node("{\"cleanup.mathematics.workBudget\":\"1000\"}");
        Path profile=file("profile.properties","cleanup.mathematics.workBudget=1000\n"); String sha=hash(profile);
        Files.writeString(profile,"cleanup.mathematics.workBudget=2000\n");
        assertThrows(IllegalStateException.class,()->RetentionAudit.verifyProfile(options,options,profile,sha));
    }
    @Test void rejectsWrongCheckoutIdentityAndTrackedSourceDrift() throws Exception {
        Path repo=Files.createDirectory(temp.resolve("repo"));
        try(var git=Git.init().setDirectory(repo.toFile()).call()) {
            Files.writeString(repo.resolve("A.java"),"class A {}",StandardCharsets.UTF_8);
            git.add().addFilepattern("A.java").call(); var c=git.commit().setMessage("fixture").setAuthor("Audit","audit@example.invalid").call();
            RetentionAudit.verifyGit(repo,c.name(),c.getTree().name());
            assertThrows(IllegalStateException.class,()->RetentionAudit.verifyGit(repo,"0".repeat(40),c.getTree().name()));
            assertThrows(IllegalStateException.class,()->RetentionAudit.verifyGit(repo,c.name(),"0".repeat(40)));
            Files.writeString(repo.resolve("A.java"),"class Changed {}");
            assertThrows(IllegalStateException.class,()->RetentionAudit.verifyGit(repo,c.name(),c.getTree().name()));
        }
    }
}
