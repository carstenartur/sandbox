package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.junit.jupiter.api.Test;

class MathematicalEnvironmentTest {
 @Test void foregroundBindingKeyDoesNotReadBinaryResources() throws Exception {
  var entry=(org.eclipse.jdt.core.IClasspathEntry)Proxy.newProxyInstance(IJavaElement.class.getClassLoader(),new Class<?>[]{org.eclipse.jdt.core.IClasspathEntry.class},
   (proxy,method,args)->switch(method.getName()){case "getEntryKind"->org.eclipse.jdt.core.IClasspathEntry.CPE_LIBRARY;case "toString"->"unreadable library";default->throw new AssertionError("Foreground path lookup: "+method);});
  var project=(org.eclipse.jdt.core.IJavaProject)Proxy.newProxyInstance(IJavaElement.class.getClassLoader(),new Class<?>[]{org.eclipse.jdt.core.IJavaProject.class},
   (proxy,method,args)->switch(method.getName()) {case "getHandleIdentifier"->"=Foreground";case "exists"->true;case "getOptions"->Map.of();case "getOutputLocation"->org.eclipse.core.runtime.IPath.fromPortableString("/Foreground/bin");case "getRawClasspath","getResolvedClasspath"->new org.eclipse.jdt.core.IClasspathEntry[]{entry};default->throw new AssertionError(method);});
  var field=MathematicalEnvironment.class.getDeclaredField("listening");field.setAccessible(true);boolean prior=field.getBoolean(null);field.setBoolean(null,true);
  try { assertEquals(MathematicalEnvironment.captureStructure(project),MathematicalEnvironment.captureStructure(project)); }
  finally { field.setBoolean(null,prior); }
 }
 @Test void binaryRelativePathsArePartOfTheSnapshot(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  var binary=directory.resolve("Before.class");java.nio.file.Files.write(binary,new byte[]{1});String before=MathematicalEnvironment.binaryFingerprint(directory);
  java.nio.file.Files.move(binary,directory.resolve("After.class"));assertNotEquals(before,MathematicalEnvironment.binaryFingerprint(directory));
 }
 @Test void missingAndSymbolicBinaryEntriesFailClosed(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  assertThrows(java.io.IOException.class,()->MathematicalEnvironment.binaryFingerprint(directory.resolve("missing.jar")));
  var actual=directory.resolve("actual.jar");java.nio.file.Files.write(actual,new byte[]{1});var link=directory.resolve("alias.jar");java.nio.file.Files.createSymbolicLink(link,actual);
  assertThrows(java.io.IOException.class,()->MathematicalEnvironment.binaryFingerprint(link));assertThrows(java.io.IOException.class,()->MathematicalEnvironment.binaryFingerprint(directory));
 }
 @Test void bodyOnlyFineGrainedSourceDeltasDoNotInvalidateOtherFiles() {
  assertFalse(MathematicalEnvironment.affectsBindings(delta(IJavaElement.COMPILATION_UNIT,IJavaElementDelta.CHANGED,
    IJavaElementDelta.F_CONTENT|IJavaElementDelta.F_FINE_GRAINED)));
 }
 @Test void memberSignaturesAndImportsInvalidateSavedBindings() {
  for(int type:new int[]{IJavaElement.METHOD,IJavaElement.FIELD,IJavaElement.IMPORT_DECLARATION})
   assertTrue(MathematicalEnvironment.affectsBindings(delta(type,IJavaElementDelta.CHANGED,IJavaElementDelta.F_CONTENT)));
 }
 @Test void addedTypesAndArchiveClasspathChangesInvalidateSavedBindings() {
  assertTrue(MathematicalEnvironment.affectsBindings(delta(IJavaElement.TYPE,IJavaElementDelta.ADDED,0)));
  for(int flag:new int[]{IJavaElementDelta.F_CLASSPATH_CHANGED,IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED,IJavaElementDelta.F_SUPER_TYPES})
   assertTrue(MathematicalEnvironment.affectsBindings(delta(IJavaElement.JAVA_PROJECT,IJavaElementDelta.CHANGED,flag)));
 }
 @Test void changedBinaryInAClasspathDirectoryInvalidatesBindings() {
  assertTrue(MathematicalEnvironment.affectsBindings(delta(IJavaElement.CLASS_FILE,IJavaElementDelta.CHANGED,IJavaElementDelta.F_CONTENT)));
 }
 @Test void mapDigestIsOrderIndependentButValuesAndKeysAreUnambiguous() {
  assertEquals(MathematicalEnvironment.digestOptions(Map.of("a","1","b","2")),MathematicalEnvironment.digestOptions(Map.of("b","2","a","1")));
  assertNotEquals(MathematicalEnvironment.digestOptions(Map.of("a","bc")),MathematicalEnvironment.digestOptions(Map.of("ab","c")));
 }
 @Test void privateWorkingCopyDescendantsDoNotInvalidatePrimaryBindings() {
  org.eclipse.jdt.core.ICompilationUnit privateUnit=(org.eclipse.jdt.core.ICompilationUnit)Proxy.newProxyInstance(
   IJavaElement.class.getClassLoader(),new Class<?>[]{org.eclipse.jdt.core.ICompilationUnit.class},
   (proxy,method,args)->switch(method.getName()) {case "isWorkingCopy"->true;case "equals"->false;default->null;});
  IJavaElement method=(IJavaElement)Proxy.newProxyInstance(IJavaElement.class.getClassLoader(),new Class<?>[]{IJavaElement.class},
   (proxy,called,args)->switch(called.getName()){case "getAncestor"->privateUnit;case "getElementType"->IJavaElement.METHOD;default->null;});
  IJavaElementDelta change=(IJavaElementDelta)Proxy.newProxyInstance(IJavaElementDelta.class.getClassLoader(),new Class<?>[]{IJavaElementDelta.class},
   (proxy,called,args)->switch(called.getName()){case "getElement"->method;case "getKind"->IJavaElementDelta.CHANGED;case "getFlags"->IJavaElementDelta.F_CONTENT;default->null;});
  assertFalse(MathematicalEnvironment.affectsBindings(change));
 }
 @Test void binaryContentChangesInvalidateEvenWhenMetadataIsPreserved(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  var jar=directory.resolve("dependency.jar");java.nio.file.Files.write(jar,new byte[]{1,2,3});var timestamp=java.nio.file.Files.getLastModifiedTime(jar);
  String before=MathematicalEnvironment.binaryFingerprint(jar);java.nio.file.Files.write(jar,new byte[]{3,2,1});java.nio.file.Files.setLastModifiedTime(jar,timestamp);
  assertNotEquals(before,MathematicalEnvironment.binaryFingerprint(jar));
 }
 @Test void nestedClassContentsAreBoundWithoutRelyingOnDirectoryTimestamps(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
  var pack=java.nio.file.Files.createDirectories(directory.resolve("example"));var binary=pack.resolve("Dependency.class");java.nio.file.Files.write(binary,new byte[]{1,2,3});
  var timestamp=java.nio.file.Files.getLastModifiedTime(directory);String before=MathematicalEnvironment.binaryFingerprint(directory);
  java.nio.file.Files.write(binary,new byte[]{3,2,1});java.nio.file.Files.setLastModifiedTime(directory,timestamp);
  assertNotEquals(before,MathematicalEnvironment.binaryFingerprint(directory));
 }
 static IJavaElementDelta delta(int type,int kind,int flags) {
  IJavaElement element=(IJavaElement)Proxy.newProxyInstance(IJavaElement.class.getClassLoader(),new Class<?>[]{IJavaElement.class},
   (proxy,method,args)->method.getName().equals("getElementType")?type:null);
  return (IJavaElementDelta)Proxy.newProxyInstance(IJavaElementDelta.class.getClassLoader(),new Class<?>[]{IJavaElementDelta.class},
   (proxy,method,args)->switch(method.getName()) {
    case "getElement"->element;case "getKind"->kind;case "getFlags"->flags;
    case "getAffectedChildren"->new IJavaElementDelta[0];default->null;
   });
 }
}
