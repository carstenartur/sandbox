package org.sandbox.math.retention;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;
import org.eclipse.jgit.api.Git;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Independent verification of source-bound ProjectMathScan retention receipts. */
public final class RetentionAudit {
    static final ObjectMapper JSON = new ObjectMapper();
    record Region(String path, int offset, int length, String sourceSha, JsonNode evidence) { }
    private RetentionAudit() { }
    static ObjectNode compare(Map<String,Region> baseline, Map<String,Region> current) throws Exception {
        var result=JSON.createObjectNode();
        var added=result.putArray("additions"); var replacement=result.putArray("replacementChanges");
        var cost=result.putArray("costChanges"); var proof=result.putArray("proofChanges");
        var other=result.putArray("otherEvidenceChanges"); var retained=result.putArray("retainedIds");
        var lost=new TreeSet<>(baseline.keySet()); lost.removeAll(current.keySet());
        require(lost.isEmpty(),"Lost baseline regions: "+lost);
        for(var entry:new TreeMap<>(baseline).entrySet()) {
            String id=entry.getKey(); var a=entry.getValue(); var b=current.get(id);
            require(a.path().equals(b.path()) && a.offset()==b.offset() && a.length()==b.length()
                    && a.sourceSha().equals(b.sourceSha()) && a.evidence().path("original").equals(b.evidence().path("original")),
                    "Changed source identity/span: "+id);
            retained.add(id);
            change(replacement,id,a.evidence().path("replacement"),b.evidence().path("replacement"));
            change(cost,id,a.evidence().path("cost"),b.evidence().path("cost"));
            change(proof,id,a.evidence().path("proof"),b.evidence().path("proof"));
            var oldRest=((ObjectNode)a.evidence()).deepCopy().without(List.of("offset","length","original","replacement","cost","proof"));
            var newRest=((ObjectNode)b.evidence()).deepCopy().without(List.of("offset","length","original","replacement","cost","proof"));
            change(other,id,oldRest,newRest);
        }
        for(String id:new TreeSet<>(current.keySet())) if(!baseline.containsKey(id)) {
            var region=current.get(id); var row=added.addObject(); row.put("id",id); row.put("sourceSha256",region.sourceSha()); row.set("evidence",region.evidence());
        }
        result.put("baselineRegions",baseline.size()); result.put("currentRegions",current.size());
        result.put("retained",baseline.size()); result.put("lost",0); result.put("costsUnchanged",cost.isEmpty());
        return result;
    }
    private static void change(ArrayNode changes,String id,JsonNode before,JsonNode after) {
        if(before.equals(after)) return;
        var row=changes.addObject(); row.put("id",id); row.set("before",before); row.set("after",after);
    }
    static String hash(Path path) throws Exception {
        require(Files.isRegularFile(path) && !Files.isSymbolicLink(path),"Not a regular immutable input: "+path);
        var digest=MessageDigest.getInstance("SHA-256");
        try(var input=Files.newInputStream(path)) { byte[] bytes=new byte[65536]; for(int n;(n=input.read(bytes))>=0;) digest.update(bytes,0,n); }
        return HexFormat.of().formatHex(digest.digest());
    }
    static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    static void requireHash(Path path,String expected) throws Exception {
        require(expected.matches("[0-9a-f]{64}") && expected.equals(hash(path)),"Hash mismatch: "+path);
    }
    static void verifyRuntime(ObjectNode inputs) throws Exception {
        require(!inputs.isEmpty(),"Empty runtime inputs");
        for(var entry:inputs.properties()) {
            if(entry.getKey().startsWith("loaded:")) continue;
            Path file=Path.of(entry.getKey()); require(file.isAbsolute(),"Relative runtime input: "+file);
            requireHash(file,entry.getValue().asText());
        }
        for(var entry:inputs.properties()) {
            if(!entry.getKey().startsWith("loaded:")) continue;
            Path origin=Path.of(entry.getValue().asText()); require(origin.isAbsolute(),"Relative loaded origin");
            Path loaded=Files.isDirectory(origin) ? origin.resolve(entry.getKey().substring(7).replace('.','/')+".class") : origin;
            require(inputs.has(loaded.toString()),"Loaded class/JAR is not fingerprinted: "+loaded);
            requireHash(loaded,inputs.path(loaded.toString()).asText());
            if(Files.isDirectory(origin)) try(var files=Files.walk(origin)) {
                for(Path file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class")).toList())
                    require(inputs.has(file.toString()),"Unreceipted runtime class: "+file);
            }
        }
    }
    static Map<String,String> normalized(ObjectNode options) {
        var values=new TreeMap<String,String>(); options.properties().forEach(e->values.put(e.getKey(),e.getValue().asText()));
        values.putIfAbsent("cleanup.mathematics.tailRecursion","false");
        values.putIfAbsent("cleanup.mathematics.valueRuntime","UNSPECIFIED");
        String kinds=values.get("cleanup.mathematics.numericKinds");
        if(kinds!=null) {
            var tokens=Arrays.asList(kinds.split(",",-1)); var unique=new TreeSet<>(tokens);
            require(!unique.contains("") && unique.size()==tokens.size(),"Invalid numeric kinds");
            values.put("cleanup.mathematics.numericKinds",String.join(",",unique));
        }
        return values;
    }
    static Properties properties(Path path) throws Exception {
        var result=new Properties(); try(var reader=Files.newBufferedReader(path)) { result.load(reader); } return result;
    }
    static void verifyProfile(ObjectNode baseline,ObjectNode current,Path profile,String profileSha) throws Exception {
        requireHash(profile,profileSha); var actual=JSON.createObjectNode();
        properties(profile).forEach((key,value)->actual.put(key.toString(),value.toString()));
        require(normalized(actual).equals(normalized(current)),"Current options differ from hashed profile");
        require(normalized(baseline).equals(normalized(current)),"Baseline contract/budget differs from current profile");
    }
    static void verifyGit(Path repository,String head,String tree) throws Exception {
        try(var git=Git.open(repository.toFile())) {
            require(head.matches("[0-9a-f]{40}") && head.equals(git.getRepository().resolve("HEAD").name()),"Git HEAD mismatch");
            require(tree.matches("[0-9a-f]{40}") && tree.equals(git.getRepository().resolve("HEAD^{tree}").name()),"Git tree mismatch");
            var status=git.status().call();
            require(status.getAdded().isEmpty() && status.getChanged().isEmpty() && status.getRemoved().isEmpty()
                    && status.getMissing().isEmpty() && status.getModified().isEmpty() && status.getConflicting().isEmpty(),"Tracked source differs from Git HEAD");
        }
    }
    static void verifyBaseline(Path zip,String expected,Path extracted) throws Exception {
        requireHash(zip,expected); var expectedFiles=new TreeSet<String>();
        try(var archive=new ZipFile(zip.toFile())) {
            for(var entry:archive.stream().filter(e->!e.isDirectory()
                    && (e.getName().startsWith("bc-strict/") || e.getName().startsWith("bc-mathematical/"))).toList()) {
                String name=entry.getName(); Path file=extracted.resolve(name).normalize();
                require(file.startsWith(extracted.normalize()) && expectedFiles.add(name),"Unsafe/duplicate baseline ZIP entry");
                try(var input=archive.getInputStream(entry)) { requireHash(file,hash(input.readAllBytes())); }
            }
        }
        var actualFiles=new TreeSet<String>();
        for(String mode:List.of("bc-strict","bc-mathematical")) if(Files.exists(extracted.resolve(mode)))
            try(var files=Files.walk(extracted.resolve(mode))) {
                for(Path file:files.filter(Files::isRegularFile).toList()) actualFiles.add(extracted.relativize(file).toString());
            }
        require(!expectedFiles.isEmpty() && actualFiles.equals(expectedFiles),"Extracted baseline inventory differs from pinned ZIP");
    }
    static ObjectNode report(Path root) throws Exception { return (ObjectNode)JSON.readTree(root.resolve("review.json").toFile()); }
    static Map<String,Region> readIndex(Path root,Path archive,boolean current) throws Exception {
        var report=report(root); var manifest=properties(root.resolve("benchmark.properties"));
        require("sandbox.project-math-scan/v1".equals(report.path("schema").asText()),"Unexpected report schema");
        require("sandbox.generated-corpus-fixture/v1".equals(manifest.getProperty("schema")),"Unexpected fixture schema");
        String status=report.path("status").asText();
        require(Set.of("COMPLETED","COMPLETED_WITH_PARSE_ERRORS").contains(status)
                && Files.readString(root.resolve("COMPLETED")).startsWith(status+"\n"),"Scan did not complete");
        requireHash(root.resolve("review.json"),manifest.getProperty("reviewSha256",""));
        for(String key:List.of("binarySha256","sourcesSha256","profileSha256","targetJava"))
            require(report.path(key).asText().equals(manifest.getProperty(key)),"Manifest/report mismatch: "+key);
        requireHash(archive,report.path("sourcesSha256").asText());
        if(current) {
            verifyRuntime((ObjectNode)report.path("runtimeInputs"));
            var input=JSON.readTree(root.resolve("inputs.json").toFile());
            for(var field:input.properties()) require(field.getValue().equals(report.path(field.getKey())),"Changed initial input: "+field.getKey());
        }
        var result=new TreeMap<String,Region>(); var selected=new TreeSet<String>();
        for(var entry:report.path("selectedEntries")) require(selected.add(relative(entry.asText())),"Duplicate selected entry");
        var entries=new TreeMap<String,byte[]>();
        try(var jar=new JarFile(archive.toFile())) {
            for(var entry:jar.stream().filter(e->!e.isDirectory() && e.getName().endsWith(".java")).toList()) {
                String name=relative(entry.getName()); try(var stream=jar.getInputStream(entry)) { require(entries.put(name,stream.readAllBytes())==null,"Duplicate archive source"); }
            }
        }
        require(selected.equals(entries.keySet()),"Incomplete or different full-corpus selection");
        require(report.path("sourceContextFiles").asInt()==entries.size(),"Source context count mismatch");
        if(current) {
            Path context=root.resolve("source-context"); var actual=new TreeSet<String>();
            try(var files=Files.walk(context)) { for(var file:files.filter(Files::isRegularFile).toList()) actual.add(context.relativize(file).toString()); }
            require(actual.equals(entries.keySet()),"Source context file set differs from archive");
            for(var entry:entries.entrySet()) requireHash(context.resolve(entry.getKey()),hash(entry.getValue()));
        }
        var visited=new HashSet<String>(); int parsed=0,errors=0,accepted=0;
        for(var row:report.path("files")) {
            String path=relative(row.path("path").asText()); require(visited.add(path) && entries.containsKey(path),"Missing/duplicate source row: "+path);
            String sourceSha=hash(entries.get(path)); require(sourceSha.equals(row.path("sourceSha256").asText()),"Archive/source identity mismatch: "+path);
            for(var dependency:row.path("sourceDependencies")) {
                String identity=dependency.path("identity").asText(), marker="/source-context/"; int at=identity.indexOf(marker);
                require(at>=0,"Dependency is not archive-bound: "+identity); String dependencyPath=relative(identity.substring(at+marker.length()));
                require(entries.containsKey(dependencyPath) && hash(entries.get(dependencyPath)).equals(dependency.path("sha256").asText()),"Dependency archive mismatch: "+dependencyPath);
                if(current) require(Path.of(identity).toAbsolutePath().normalize().equals(root.resolve("source-context").resolve(dependencyPath).toAbsolutePath().normalize()),"Foreign dependency context");
            }
            if("PARSE_ERROR".equals(row.path("status").asText())) {
                errors++; require(!row.path("parseErrors").isEmpty() && row.path("acceptedRegions").asInt()==0,"Malformed parse-error row"); continue;
            }
            parsed++; require("ANALYZED".equals(row.path("status").asText()) && row.path("parseErrors").isEmpty(),"Malformed analyzed row");
            int count=row.path("acceptedRegions").asInt(-1); require(count>=0,"Missing accepted count"); accepted+=count;
            if(count==0) continue;
            requireHash(root.resolve("original").resolve(path),sourceSha);
            requireHash(root.resolve("generated").resolve(path),row.path("afterSha256").asText());
            require(sourceSha.equals(manifest.getProperty("original."+path)) && row.path("afterSha256").asText().equals(manifest.getProperty("generated."+path)),"Fixture source digest differs: "+path);
            var evidence=JSON.readTree(root.resolve("evidence").resolve(path+".json").toFile());
            require(evidence.isArray() && evidence.size()==count && row.path("reviewCandidates").size()==count,"Evidence/count mismatch: "+path);
            String source=new String(entries.get(path),StandardCharsets.UTF_8); var expectedCandidates=new HashSet<String>();
            for(var candidate:row.path("reviewCandidates")) require(expectedCandidates.add(candidate.path("offset").asInt()+":"+candidate.path("length").asInt()),"Duplicate review candidate");
            for(var e:evidence) {
                int offset=e.path("offset").asInt(-1),length=e.path("length").asInt(-1);
                require(offset>=0 && length>0 && offset<=source.length()-length && source.substring(offset,offset+length).equals(e.path("original").asText()),"Original source span mismatch: "+path+":"+offset);
                require(expectedCandidates.remove(offset+":"+length),"Evidence has no unique review candidate");
                String id=path+":"+offset+":"+length;
                require(result.put(id,new Region(path,offset,length,sourceSha,e))==null,"Duplicate evidence region: "+id);
            }
            require(expectedCandidates.isEmpty(),"Unmatched review candidates");
            var edits=new ArrayList<JsonNode>(); evidence.forEach(edits::add);
            String review=render(source,edits); require(hash(review.getBytes(StandardCharsets.UTF_8)).equals(row.path("reviewOnlySourceSha256").asText()),"Review source reconstruction mismatch: "+path);
            if(current) requireHash(root.resolve("review-only").resolve(path),row.path("reviewOnlySourceSha256").asText());
            row.path("omittedReviewDocumentation").forEach(edits::add);
            require(hash(render(source,edits).getBytes(StandardCharsets.UTF_8)).equals(row.path("afterSha256").asText()),"Ordinary Apply reconstruction mismatch: "+path);
        }
        require(visited.equals(selected),"Report source set differs from selection");
        require(parsed==report.path("parsedFiles").asInt() && errors==report.path("parseErrorFiles").asInt()
                && accepted==report.path("acceptedRegions").asInt() && accepted==result.size(),"Aggregate count mismatch");
        return result;
    }
    private static String render(String source,List<JsonNode> edits) {
        var ordered=new ArrayList<>(edits); ordered.sort(Comparator.comparingInt((JsonNode n)->n.path("offset").asInt()).reversed());
        var result=new StringBuilder(source); int previous=source.length();
        for(var edit:ordered) {
            int start=edit.path("offset").asInt(-1),length=edit.path("length").asInt(-1);
            require(start>=0 && length>=0 && start<=previous-length && edit.path("replacement").isTextual(),"Overlapping or malformed replacement");
            result.replace(start,start+length,edit.path("replacement").asText()); previous=start;
        }
        return result.toString();
    }
    private static String relative(String value) {
        require(!value.isBlank() && value.endsWith(".java") && !value.contains("\\") && !value.contains(":") && !value.startsWith("/")
                && !value.contains("//") && Arrays.stream(value.split("/")).noneMatch(p->p.equals(".") || p.equals("..")),"Unsafe archive entry: "+value); return value;
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
}
