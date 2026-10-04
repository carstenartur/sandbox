package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.junit.jupiter.api.Test;

class MathematicalEnvironmentTest {
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
