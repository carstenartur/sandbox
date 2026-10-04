/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Analysis;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Diagnostic;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.Replacement;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis.VerifiedRegion;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Machine-readable proposed source and independently verified mathematical evidence. */
record MathematicalReport(int schemaVersion,String project,String mode,Map<String,String> requestedOptions,
  Map<String,String> configProperties,String sdkSha256,String adapterBundleSha256,List<FileReport> files,
  String status,Boolean rollbackComplete,List<String> failures) {
 MathematicalReport { requestedOptions=Map.copyOf(requestedOptions);configProperties=Map.copyOf(configProperties);files=List.copyOf(files);failures=List.copyOf(failures); }
 MathematicalReport(int schemaVersion,String project,String mode,Map<String,String> requestedOptions,
   Map<String,String> configProperties,String sdkSha256,String adapterBundleSha256,List<FileReport> files) {
  this(schemaVersion,project,mode,requestedOptions,configProperties,sdkSha256,adapterBundleSha256,files,"SUCCESS",null,List.of());
 }
 void write(Path path) throws IOException {
  Path absolute=path.toAbsolutePath();Files.createDirectories(absolute.getParent());
  Path temporary=Files.createTempFile(absolute.getParent(),"mathematics-report-",".json");
  try {
   new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(),this);
   try { Files.move(temporary,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
   catch(AtomicMoveNotSupportedException unsupported) { Files.move(temporary,absolute,StandardCopyOption.REPLACE_EXISTING); }
  } finally { Files.deleteIfExists(temporary); }
 }
 static FileReport planned(String path,String source,Map<String,String> compilerOptions,MathCleanUpOptions options,
   MathematicalEnvironment.Snapshot environment,Analysis analysis,boolean applied) {
  if(!analysis.matches(source,compilerOptions)) throw new IllegalArgumentException("STALE_ANALYSIS: report does not match source and compiler settings");
  Document document=new Document(source);
  try { analysis.newEdit().apply(document); } catch(BadLocationException invalid) { throw new IllegalArgumentException("Invalid verified replacement range",invalid); }
  String replacement=document.get();
  return new FileReport(path,options.targetJava(),options.toMap(),MathematicalEnvironment.digest(source),MathematicalEnvironment.digest(replacement),
    source,replacement,compilerOptions,environment.digest(),analysis.diagnostics(),analysis.replacements(),analysis.evidence(),applied,
    applied?MathematicalEnvironment.digest(replacement):MathematicalEnvironment.digest(source),applied?"REPLACEMENT":"ORIGINAL");
 }
 record FileReport(String path,int targetJava,Map<String,String> options,String sourceSha256,String afterSha256,
   String original,String replacement,Map<String,String> compilerOptions,String environmentDigest,List<Diagnostic> diagnostics,
   List<Replacement> changes,List<VerifiedRegion> evidence,boolean applied,String observedSourceSha256,String applicationStatus) {
  FileReport { options=Map.copyOf(options);compilerOptions=Map.copyOf(compilerOptions);diagnostics=List.copyOf(diagnostics);changes=List.copyOf(changes);evidence=List.copyOf(evidence); }
  FileReport withObserved(String source) {
   String hash=source==null?null:MathematicalEnvironment.digest(source);
   String state=source==null?"UNAVAILABLE":source.equals(original)?"ORIGINAL":source.equals(replacement)?"REPLACEMENT":"OTHER";
   return new FileReport(path,targetJava,options,sourceSha256,afterSha256,original,replacement,compilerOptions,environmentDigest,diagnostics,changes,evidence,
     source!=null && !source.equals(original),hash,state);
  }
 }
}
