/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.sandbox.jdt.internal.corext.fix.math.*;
import org.sandbox.jdt.triggerpattern.test.policy.PinnedGitRepository;
import de.regelsuche.sdk.optimization.*;

/** Network corpus gate, explicitly separate from the unchanged SWT 3.135.0 default baseline. */
@EnabledIfSystemProperty(named="sandbox.math.swt.current",matches="true")
class SwtCurrentSourceRefinementTest {
    private static final String COMMIT="221d1de66c33292400c91cbd17e3121f72b04ea3";
    private static final String REMOTE="https://github.com/eclipse-platform/eclipse.platform.swt.git";
    private static final String P2="https://download.eclipse.org/eclipse/updates/4.42-I-builds/I20261029-2300/plugins/";
    private static final String VERSION="3.136.0.v20261029-1803";
    private static final List<String> CLASSES=List.of("graphics/ImageData", "custom/StyledTextRenderer",
            "internal/image/JPEGDecoder", "internal/image/JPEGFileFormat", "internal/image/PNGFileFormat", "internal/image/TIFFDirectory");
    @TempDir Path temporary;

    @Test @Timeout(180) void exactUpstreamSourcesCompileExecuteAndUndoThroughOrdinaryApply() throws Exception {
        Path binary=acquire("org.eclipse.swt.gtk.linux.x86_64_"+VERSION+".jar","6c2b69d1c2096d9915df958536aeb6a7efec8210901e3b54b322d8423dbf0119");
        Path sources=acquire("org.eclipse.swt.gtk.linux.x86_64.source_"+VERSION+".jar","7bbb0b52c43152f2b8c1faeee458f1e24fb04f8a9d516c3409cd509e6415dcbc");
        String ref="refs/heads/master";
        var originals=new LinkedHashMap<String,String>();
        Path sourceRoot=Files.createDirectories(temporary.resolve("sourcepath"));
        try(var checkout=PinnedGitRepository.cloneAtCommit(temporary.resolve("checkout"),URI.create(REMOTE),ref,COMMIT);
                var archive=new JarFile(sources.toFile())) {
            assertEquals(COMMIT,checkout.headCommit());
            System.out.printf("SWT_CURRENT upstream=%s ref=%s commit=%s%n",REMOTE,ref,COMMIT);
            for(String relative:CLASSES) {
                String entry="org/eclipse/swt/"+relative+".java";
                String folder=relative.startsWith("custom/")?"Eclipse SWT Custom Widgets":"Eclipse SWT";
                String upstream=checkout.readString("bundles/org.eclipse.swt/"+folder+"/common/"+entry);
                assertEquals(upstream,read(archive,entry),"P2 archive and exact upstream checkout must agree: "+entry);
                originals.put(entry,upstream);
            }
            String swt=read(archive,"org/eclipse/swt/SWT.java");
            assertEquals(checkout.readString("bundles/org.eclipse.swt/Eclipse SWT/common/org/eclipse/swt/SWT.java"),swt);
            Path helper=sourceRoot.resolve("org/eclipse/swt/SWT.java"); Files.createDirectories(helper.getParent()); Files.writeString(helper,swt);
        }
        var generated=new LinkedHashMap<String,String>();
        var scan=new ArrayList<Map<String,Object>>();
        for(var entry:originals.entrySet()) {
            var environment=new MathematicalAnalysis.SourceEnvironment(entry.getKey(),List.of(binary.toString()),List.of(sourceRoot.toString()),List.of());
            var ast=parse(entry.getValue(),environment);
            var result=MathematicalAnalysis.analyze(ast,entry.getValue(),options(),new NullProgressMonitor(),-1,0,environment);
            assertFalse(result.diagnostics().stream().anyMatch(d->Set.of("CANCELLED","STALE_AST_SOURCE","COMBINED_EDITS_INVALID").contains(d.code())),result.diagnostics().toString());
            var document=new Document(entry.getValue()); var undo=result.newEdit().apply(document,TextEdit.CREATE_UNDO);
            generated.put(entry.getKey(),document.get()); undo.apply(document); assertEquals(entry.getValue(),document.get());
            System.out.printf("SWT_CURRENT wholeFile=%s regions=%d accepted=%d parseErrors=0 source=%s generated=%s%n",entry.getKey(),
                    new JavaComputationExtractor().extractRuntime(ast,entry.getValue(),options()).regions().size(),result.replacements().size(),
                    hash(entry.getValue().getBytes(StandardCharsets.UTF_8)),hash(generated.get(entry.getKey()).getBytes(StandardCharsets.UTF_8)));
            if(!entry.getKey().contains("ImageData") && !entry.getKey().contains("JPEGDecoder"))
                assertTrue(result.changed(),"Required ordinary whole-file positive: "+entry.getKey());
            scan.add(Map.of("entry",entry.getKey(),"accepted",result.replacements().size(),"evidence",result.evidence(),
                    "sourceSha256",hash(entry.getValue().getBytes(StandardCharsets.UTF_8)),
                    "generatedSha256",hash(generated.get(entry.getKey()).getBytes(StandardCharsets.UTF_8))));
            if(entry.getKey().endsWith("TIFFDirectory.java")) {
                assertTrue(generated.get(entry.getKey()).contains("int lengthDirectory = 2 + 12 * numberEntries + 4;"));
                assertFalse(generated.get(entry.getKey()).contains("int startG = _math"),generated.get(entry.getKey()));
            }
            if(entry.getKey().endsWith("StyledTextRenderer.java"))
                assertTrue(result.evidence().stream().anyMatch(e->e.sourceNumericBranch()!=null
                        && e.original().contains("newVerticalScrollOffset")),"Ordinary Apply must replay the redundant renderer assignment branch");
        }
        try(var before=new IsolatedSwt(compile(originals,binary),binary); var after=new IsolatedSwt(compile(generated,binary),binary)) {
            for(String relative:CLASSES) {
                String name="org.eclipse.swt."+relative.replace('/','.');
                assertNotSame(before.loadClass(name),after.loadClass(name),"Every complete source class must load in its own compiled version");
            }
            pixels(before,after); dct(before,after); paeth(before,after); padding(before,after);
        }
        String entry="org/eclipse/swt/graphics/ImageData.java",original=originals.get(entry);
        int selection=original.indexOf("3 - (x % 4)"); assertTrue(selection>0);
        var environment=new MathematicalAnalysis.SourceEnvironment(entry,List.of(binary.toString()),List.of(sourceRoot.toString()),List.of());
        var selected=MathematicalAnalysis.analyze(parse(original,environment),original,options(),new NullProgressMonitor(),selection,"3 - (x % 4)".length(),environment);
        assertTrue(selected.changed(),selected.diagnostics().stream().filter(d->d.offset()<=selection
                && (long)d.offset()+d.length()>selection).toList().toString());
        var selectedDocument=new Document(original); var undo=selected.newEdit().apply(selectedDocument,TextEdit.CREATE_UNDO);
        assertTrue(selectedDocument.get().substring(selection-25,selection+80).contains("x & 3"),selectedDocument.get().substring(selection-25,selection+80));
        generated.put(entry,selectedDocument.get()); undo.apply(selectedDocument); assertEquals(original,selectedDocument.get());
        assertFalse(selected.sourceDependencyReceipts().isEmpty(),"Actual SWT.error source must be fingerprinted");
        System.out.printf("SWT_CURRENT selection=ImageData.getPixel accepted=%d dependencies=%s%n",selected.replacements().size(),selected.sourceDependencyReceipts());
        try(var before=new IsolatedSwt(compile(Map.of(entry,original),binary),binary);
                var after=new IsolatedSwt(compile(Map.of(entry,generated.get(entry)),binary),binary)) {
            pixels(before,after);
        }
        Path output=Files.createDirectories(Path.of("qa/math-optimization/target/swt-current-patch"));
        for(var item:generated.entrySet()) { Path file=output.resolve(item.getKey()); Files.createDirectories(file.getParent()); Files.writeString(file,item.getValue()); }
        new com.fasterxml.jackson.databind.ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(output.resolve("receipt.json").toFile(),
                Map.of("upstream",REMOTE,"ref",ref,"commit",COMMIT,"sourceArchiveSha256",hash(Files.readAllBytes(sources)),
                        "binaryArchiveSha256",hash(Files.readAllBytes(binary)),"wholeFile",scan,"getPixelSelection",selected,
                        "scope","Static cost estimates and complete-class equivalence; no measured runtime claim"));
        try(var bytes=Files.newOutputStream(output.resolve("swt-source-refinements.patch"));var diff=new org.eclipse.jgit.diff.DiffFormatter(bytes)) {
            for(var item:generated.entrySet()) {
                if(item.getValue().equals(originals.get(item.getKey()))) continue;
                String folder=item.getKey().contains("/custom/")?"Eclipse SWT Custom Widgets":"Eclipse SWT";
                String path="bundles/org.eclipse.swt/"+folder+"/common/"+item.getKey();
                String a=org.eclipse.jgit.util.QuotedString.GIT_PATH.quote("a/"+path),b=org.eclipse.jgit.util.QuotedString.GIT_PATH.quote("b/"+path);
                bytes.write(("diff --git "+a+" "+b+"\n--- "+a+"\n+++ "+b+"\n").getBytes(StandardCharsets.UTF_8));
                var old=new org.eclipse.jgit.diff.RawText(originals.get(item.getKey()).getBytes(StandardCharsets.UTF_8));
                var fresh=new org.eclipse.jgit.diff.RawText(item.getValue().getBytes(StandardCharsets.UTF_8));
                diff.format(new org.eclipse.jgit.diff.HistogramDiff().diff(org.eclipse.jgit.diff.RawTextComparator.DEFAULT,old,fresh),old,fresh);
                diff.flush();
            }
        }
        Path application=temporary.resolve("patch-application");
        for(var item:originals.entrySet()) {
            String folder=item.getKey().contains("/custom/")?"Eclipse SWT Custom Widgets":"Eclipse SWT";
            Path file=application.resolve("bundles/org.eclipse.swt/"+folder+"/common/"+item.getKey());
            Files.createDirectories(file.getParent()); Files.writeString(file,item.getValue());
        }
        try(var git=Git.init().setDirectory(application.toFile()).call();var patch=Files.newInputStream(output.resolve("swt-source-refinements.patch"))) {
            git.apply().setPatch(patch).call();
            for(var item:generated.entrySet()) {
                String folder=item.getKey().contains("/custom/")?"Eclipse SWT Custom Widgets":"Eclipse SWT";
                assertEquals(item.getValue(),Files.readString(application.resolve("bundles/org.eclipse.swt/"+folder+"/common/"+item.getKey())));
            }
        }
        Path helper=sourceRoot.resolve("org/eclipse/swt/SWT.java"); Files.writeString(helper,Files.readString(helper)+"\n");
        assertFalse(selected.matches(original,selected.compilerOptions()));
        assertThrows(IllegalArgumentException.class,selected::newEdit,"Stale SWT.error source must reject Apply");
    }
    private void pixels(ClassLoader before,ClassLoader after) throws Exception {
        var random=new Random(0x5127);
        for(int depth:new int[]{1,2,4,8,16,24,32}) for(int size:new int[]{0,1,3,64}) {
            byte[] data=new byte[size]; random.nextBytes(data);
            for(int x:new int[]{-1,0,1,3,4,7,8,9}) for(int y:new int[]{-1,0,2,3}) {
                Object a=image(before,depth,data.clone()),b=image(after,depth,data.clone());
                assertEquals(invoke(a.getClass().getMethod("getPixel",int.class,int.class),a,x,y),invoke(b.getClass().getMethod("getPixel",int.class,int.class),b,x,y));
            }
        }
    }
    private static Object image(ClassLoader loader,int depth,byte[] data) throws Exception {
        Class<?> type=loader.loadClass("org.eclipse.swt.graphics.ImageData"); Object image=construct(type);
        type.getField("width").setInt(image,9); type.getField("height").setInt(image,3); type.getField("depth").setInt(image,depth);
        type.getField("bytesPerLine").setInt(image,3); type.getField("data").set(image,data); return image;
    }
    private static void dct(ClassLoader before,ClassLoader after) throws Exception {
        Class<?> a=before.loadClass("org.eclipse.swt.internal.image.JPEGFileFormat"),b=after.loadClass(a.getName());
        Object old=construct(a),fresh=construct(b); var random=new Random(0xDC7);
        for(String name:List.of("forwardDCT","inverseDCT")) for(int test=0;test<40;test++) {
            int[] first=new int[test==0?0:test==1?63:64];
            for(int i=0;i<first.length;i++) first[i]=test<4?(test==2?0:Integer.MAX_VALUE):random.nextInt();
            int[] second=first.clone(); Method am=method(a,name,int[].class),bm=method(b,name,int[].class);
            assertEquals(invoke(am,old,(Object)first),invoke(bm,fresh,(Object)second)); assertArrayEquals(first,second,name);
        }
    }
    private static void paeth(ClassLoader before,ClassLoader after) throws Exception {
        Object a=png(before),b=png(after); Method am=method(a.getClass(),"filterRow",byte[].class,byte[].class,int.class),bm=method(b.getClass(),"filterRow",byte[].class,byte[].class,int.class);
        var random=new Random(0xFAE7);
        for(int offset:new int[]{1,3,4}) {
            setHeaderOffset(a,offset); setHeaderOffset(b,offset);
            for(boolean alias:new boolean[]{false,true}) for(int size:new int[]{0,1,2,4,19,128}) for(int round=0;round<20;round++) {
                byte[] first=new byte[size],previous=new byte[round==0?Math.max(0,size-1):size]; random.nextBytes(first); random.nextBytes(previous);
                byte[] second=first.clone(),previousSecond=previous.clone();
                if(alias) { previous=first; previousSecond=second; }
                assertEquals(invoke(am,a,first,previous,4),invoke(bm,b,second,previousSecond,4));
                assertArrayEquals(first,second); assertArrayEquals(previous,previousSecond);
            }
        }
    }
    private static Object png(ClassLoader loader) throws Exception {
        Object png=construct(loader.loadClass("org.eclipse.swt.internal.image.PNGFileFormat"));
        Class<?> header=loader.loadClass("org.eclipse.swt.internal.image.PngIhdrChunk");
        var constructor=header.getDeclaredConstructor(byte[].class); constructor.setAccessible(true);
        Object value=constructor.newInstance((Object)new byte[33]); var field=png.getClass().getDeclaredField("headerChunk"); field.setAccessible(true); field.set(png,value); return png;
    }
    private static void setHeaderOffset(Object png,int bytes) throws Exception {
        var field=png.getClass().getDeclaredField("headerChunk"); field.setAccessible(true); Object header=field.get(png);
        var depth=header.getClass().getDeclaredField("bitDepth"); depth.setAccessible(true); depth.setByte(header,(byte)8);
        var color=header.getClass().getDeclaredField("colorType"); color.setAccessible(true); color.setByte(header,(byte)(bytes==1?0:bytes==3?2:6));
    }
    private static void padding(ClassLoader before,ClassLoader after) throws Exception {
        var a=method(before.loadClass("org.eclipse.swt.graphics.ImageData"),"convertPad",byte[].class,int.class,int.class,int.class,int.class,int.class);
        var b=method(after.loadClass(a.getDeclaringClass().getName()),"convertPad",byte[].class,int.class,int.class,int.class,int.class,int.class);
        int[][] cases={{9,2,2,4,1},{9,2,2,1,4},{0,0,2,4,1},{-1,1,2,4,1},{9,-1,2,4,1},{9,1,2,0,1},{9,1,2,1,0},
                {9,1,2,-1,4},{Integer.MAX_VALUE,2,32,4,1},{1,Integer.MIN_VALUE,8,1,4},{9,2,2,0,0},{9,2,2,-1,-1}};
        for(int[] values:cases) for(int size:new int[]{0,2,32}) {
            byte[] first=new byte[size],second=first.clone(); Object[] av={first,values[0],values[1],values[2],values[3],values[4]},bv={second,values[0],values[1],values[2],values[3],values[4]};
            assertEquals(invokeArray(a,av,first),invokeArray(b,bv,second),Arrays.toString(values)); assertArrayEquals(first,second);
        }
    }
    private static String invokeArray(Method method,Object[] arguments,byte[] input) throws Exception {
        try { Object value=method.invoke(null,arguments); return (value==input?"identity:":"copy:")+Arrays.toString((byte[])value); }
        catch(InvocationTargetException failure) { return failure.getCause().getClass().getName(); }
    }
    private static String invoke(Method method,Object receiver,Object... arguments) throws Exception {
        try { return String.valueOf(method.invoke(receiver,arguments)); }
        catch(InvocationTargetException failure) { return failure.getCause().getClass().getName(); }
    }
    private static Object construct(Class<?> type) throws Exception { var constructor=type.getDeclaredConstructor(); constructor.setAccessible(true); return constructor.newInstance(); }
    private static Method method(Class<?> type,String name,Class<?>... parameters) throws Exception { Method method=type.getDeclaredMethod(name,parameters); method.setAccessible(true); return method; }
    private Path compile(Map<String,String> files,Path binary) throws Exception {
        Path output=Files.createTempDirectory(temporary,"classes-"); var arguments=new ArrayList<>(List.of("--release","21","-cp",binary.toString(),"-d",output.toString()));
        for(var entry:files.entrySet()) { Path file=output.resolve(entry.getKey()); Files.createDirectories(file.getParent()); Files.writeString(file,entry.getValue()); arguments.add(file.toString()); }
        assertEquals(0,ToolProvider.getSystemJavaCompiler().run(null,null,null,arguments.toArray(String[]::new))); return output;
    }
    private Path acquire(String filename,String expected) throws Exception {
        Path path=Path.of("qa/math-optimization/target",filename);
        if(!Files.exists(path)) {
            var connection=URI.create(P2+filename).toURL().openConnection(); connection.setConnectTimeout(30_000); connection.setReadTimeout(30_000);
            try(var input=connection.getInputStream()) {
                byte[] bytes=input.readNBytes(20_000_001); assertTrue(bytes.length<=20_000_000); Files.write(path,bytes);
            }
        }
        assertEquals(expected,hash(Files.readAllBytes(path))); return path.toAbsolutePath();
    }
    private static CompilationUnit parse(String source,MathematicalAnalysis.SourceEnvironment environment) {
        var parser=ASTParser.newParser(AST.getJLSLatest()); parser.setSource(source.toCharArray()); parser.setUnitName(environment.unitName()); parser.setResolveBindings(true);
        parser.setEnvironment(environment.classpath().toArray(String[]::new),environment.sourcepath().toArray(String[]::new),null,true);
        var compiler=new HashMap<String,String>(); JavaCore.setComplianceOptions("21",compiler); parser.setCompilerOptions(compiler);
        var ast=(CompilationUnit)parser.createAST(null); for(var problem:ast.getProblems()) assertFalse(problem.isError(),problem.toString()); return ast;
    }
    private static MathCleanUpOptions options() { return new MathCleanUpOptions(true,Set.of(NumericKind.BYTE,NumericKind.SHORT,NumericKind.CHAR,NumericKind.INT,NumericKind.LONG),
            SafetyProfile.PRESERVE_JAVA,OptimizationGoal.LOWER_ESTIMATED_RUNTIME,1_000_000,20_000,false,21,List.of()); }
    private static String read(JarFile archive,String entry) throws IOException { try(var input=archive.getInputStream(archive.getJarEntry(entry))) { return new String(input.readAllBytes(),StandardCharsets.UTF_8); } }
    private static String hash(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static final class IsolatedSwt extends ClassLoader implements AutoCloseable {
        private final Path classes; private final JarFile dependency;
        IsolatedSwt(Path classes,Path binary) throws IOException { super(ClassLoader.getPlatformClassLoader()); this.classes=classes; dependency=new JarFile(binary.toFile()); }
        @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
            if(!name.startsWith("org.eclipse.swt.")) throw new ClassNotFoundException(name);
            String entry=name.replace('.','/')+".class";
            try { byte[] bytes;
                if(Files.isRegularFile(classes.resolve(entry))) bytes=Files.readAllBytes(classes.resolve(entry));
                else { var item=dependency.getJarEntry(entry); if(item==null) throw new ClassNotFoundException(name); try(var input=dependency.getInputStream(item)){bytes=input.readAllBytes();} }
                return defineClass(name,bytes,0,bytes.length);
            } catch(IOException failure) { throw new ClassNotFoundException(name,failure); }
        }
        @Override public void close() throws IOException { dependency.close(); }
    }
}
