/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUp;
import org.sandbox.jdt.internal.ui.fix.UseFunctionalCallCleanUpCore;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava22;

/** Runs in the converter-only reactor, which does not install the optional math host. */
class FunctionalMathIndependenceTest {
 @RegisterExtension AbstractEclipseJava context=new EclipseJava22();
 @Test void nativeWrapperPreviewReadsLiveMasterAndSourceOptions() {
  var options=new HashMap<>(Map.of("cleanup.loop_conversion.enabled","false","cleanup.loop_conversion.target_format","stream",
    "cleanup.loop_conversion.from.enhanced_for","true","cleanup.loop_conversion.from.iterator_while","true","cleanup.loop_conversion.from.classic_for","true"));
  var cleanup=new UseFunctionalCallCleanUp(options);
  assertEquals(0,cleanup.getStepDescriptions().length);assertFalse(cleanup.getPreview().contains("IntStream.range"));
  options.put("cleanup.loop_conversion.enabled","true");
  String preview=cleanup.getPreview();
  assertTrue(preview.contains("items.forEach("),preview);assertTrue(preview.contains("items.stream().forEach("),preview);
  assertTrue(preview.contains("IntStream.range"),preview);assertEquals(1,cleanup.getStepDescriptions().length);
  options.put("cleanup.loop_conversion.from.classic_for","false");
  assertFalse(cleanup.getPreview().contains("IntStream.range"));
  options.put("cleanup.loop_conversion.from.enhanced_for","false");options.put("cleanup.loop_conversion.from.iterator_while","false");
  assertEquals(0,cleanup.getStepDescriptions().length);assertFalse(cleanup.getPreview().contains(".forEach("));
 }
 @Test void nativeWrapperPreviewReadsLiveTargetOptions() {
  var options=new HashMap<>(Map.of("cleanup.loop_conversion.enabled","true","cleanup.loop_conversion.target_format","stream",
    "cleanup.loop_conversion.from.enhanced_for","true","cleanup.loop_conversion.from.iterator_while","true","cleanup.loop_conversion.from.stream","true"));
  var cleanup=new UseFunctionalCallCleanUp(options);String initial=cleanup.getPreview();
  for(String target:new String[]{"enhanced_for","iterator_while"}) {
   options.put("cleanup.loop_conversion.target_format",target);
   assertEquals(new UseFunctionalCallCleanUp(new HashMap<>(options)).getPreview(),cleanup.getPreview(),target);
   assertNotEquals(initial,cleanup.getPreview());assertEquals(1,cleanup.getStepDescriptions().length);
  }
 }
 @Test void staleCombinedProfileNeverSuppressesTheStandaloneConverter() throws Exception {
  assertNull(Platform.getBundle("sandbox_math_cleanup"));
  String source="package test1; class Sample { void run(java.util.List<String> values){ for(String value:values) System.out.println(value); } }";
  var unit=context.getSourceFolder().createPackageFragment("test1",false,null).createCompilationUnit("Sample.java",source,false,null);
  var parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);
  var ast=(CompilationUnit)parser.createAST(null);
  var cleanup=new UseFunctionalCallCleanUpCore(Map.of("cleanup.functionalloop","true",
    "cleanup.mathematics","true","cleanup.loop_conversion.with_mathematics","true"));
  var fix=cleanup.createFix(new CleanUpContext(unit,ast));assertNotNull(fix);
  String preview=fix.createChange(new NullProgressMonitor()).getPreviewContent(new NullProgressMonitor());
  assertTrue(preview.contains(".forEach("),preview);assertEquals(source,unit.getSource());
 }
}
