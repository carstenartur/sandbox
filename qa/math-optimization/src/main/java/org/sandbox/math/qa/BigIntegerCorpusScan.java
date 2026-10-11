/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.stream.*;
import java.io.*;
import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.text.Document;
import org.sandbox.jdt.internal.corext.fix.math.*;
/** Manual corpus diagnostic; production verification and JUnit remain authoritative. */
public final class BigIntegerCorpusScan {
 public static void main(String[] args) throws Exception {
  if(args.length != 4 || !Set.of("strict", "mathematical").contains(args[3])) throw new IllegalArgumentException("Usage: <binary.jar> <sources.jar> <fresh-output> <strict|mathematical>");
  if(Files.exists(Path.of(args[2]))) throw new IllegalArgumentException("Use a fresh output directory");
  Path binary=Path.of(args[0]), archive=Path.of(args[1]), out=Path.of(args[2]); Files.createDirectories(out);
  var settings=new HashMap<String,String>(); JavaCore.setComplianceOptions("17",settings);
  var map=new HashMap<>(MathCleanUpOptions.defaults(17).toMap()); map.put(MathCleanUpOptions.CLEANUP,"true"); map.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"true"); map.put(MathCleanUpOptions.WORK_BUDGET,"1000000"); map.put(MathCleanUpOptions.MAX_STATES,"20000");
  map.put(MathCleanUpOptions.KINDS,"BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,BIG_INTEGER");
  if(args[3].equals("strict")) map.put(MathCleanUpOptions.MATHEMATICAL_OPT_IN,"false");
  var options=MathCleanUpOptions.parse(map,17);
  var counts=new TreeMap<String,Integer>(); var reasons=new TreeMap<String,Integer>(); int files=0,errors=0,regions=0,changes=0;
  try(var jar=new JarFile(archive.toFile()); var calls=Files.newBufferedWriter(out.resolve("calls.tsv")); var diagnostics=Files.newBufferedWriter(out.resolve("diagnostics.tsv")); var accepted=Files.newBufferedWriter(out.resolve("accepted.txt"))) {
   calls.write("file\tline\tmethod\tstatement\tcall\tresult\n"); diagnostics.write("file\tline\tcode\tmessage\n");
   for(var entry:jar.stream().filter(e->e.getName().endsWith(".java")).sorted(Comparator.comparing(JarEntry::getName)).toList()) {
    String source; try(var stream=jar.getInputStream(entry)){source=new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
    var parser=ASTParser.newParser(AST.getJLSLatest());parser.setUnitName(entry.getName());parser.setSource(source.toCharArray());parser.setEnvironment(new String[]{binary.toString()},new String[0],null,true);parser.setResolveBindings(true);parser.setCompilerOptions(settings);
    CompilationUnit ast=(CompilationUnit)parser.createAST(null); if(Arrays.stream(ast.getProblems()).anyMatch(p->p.isError())){errors++;continue;} files++;
    var ex=new JavaComputationExtractor().extractRuntime(ast,source,options);regions+=ex.regions().size();
    var env=new MathematicalAnalysis.SourceEnvironment(entry.getName(),List.of(binary.toString()),List.of(),List.of());
    var result=MathematicalAnalysis.analyze(ast,source,options,new NullProgressMonitor(),-1,0,env);
    changes+=result.replacements().size();
    for(var d:result.diagnostics()){reasons.merge(d.code(),1,Integer::sum);diagnostics.write(entry.getName()+"\t"+ast.getLineNumber(d.offset())+"\t"+d.code()+"\t"+d.message().replace('\n',' ')+"\n");}
    if(result.changed()) {
     Document doc=new Document(source);var undo=result.newEdit().apply(doc,org.eclipse.text.edits.TextEdit.CREATE_UNDO); String generated=doc.get();undo.apply(doc);if(!doc.get().equals(source))throw new AssertionError("undo");
     Path f=out.resolve("generated").resolve(entry.getName());Files.createDirectories(f.getParent());Files.writeString(f,generated);
     for(var e:result.evidence())accepted.write(entry.getName()+":"+ast.getLineNumber(e.offset())+"\nBEFORE\n"+e.original()+"\nAFTER\n"+e.replacement()+"\n");
    }
    ast.accept(new ASTVisitor(){@Override public boolean visit(MethodInvocation call){var m=call.resolveMethodBinding();if(m==null||!m.getDeclaringClass().getQualifiedName().equals("java.math.BigInteger"))return true;
     counts.merge(m.getName(),1,Integer::sum); ASTNode s=call;while(s.getParent()!=null&&!(s instanceof Statement)&&!(s instanceof FieldDeclaration))s=s.getParent();int pos=call.getStartPosition();
     String status=result.evidence().stream().filter(e->e.offset()<=pos&&pos<e.offset()+e.length()).map(e->"ACCEPTED").findFirst().orElse(null);
     if(status==null)status=result.diagnostics().stream().filter(d->d.offset()<=pos&&pos<d.offset()+d.length()).map(d->d.code()).collect(Collectors.joining(","));
     if(status.isEmpty())status=ex.regions().stream().anyMatch(r->r.start()<=pos&&pos<r.start()+r.length())?"REGION_NO_DIRECT_DIAGNOSTIC":"NO_REGION";
     try{calls.write(entry.getName()+"\t"+ast.getLineNumber(pos)+"\t"+m.getName()+"\t"+s.getClass().getSimpleName()+"\t"+call.toString().replaceAll("\\s+"," ")+"\t"+status+"\n");}catch(IOException e){throw new UncheckedIOException(e);}return true;}});
    if(files%50==0)System.out.println("files="+files+" changes="+changes);
   }
  }
  String summary="files="+files+" parseErrors="+errors+" regions="+regions+" replacements="+changes+"\nMETHODS\n"+counts+"\nDIAGNOSTICS\n"+reasons+"\n";Files.writeString(out.resolve("summary.txt"),summary);System.out.print(summary);
 }
}
