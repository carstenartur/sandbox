/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;

/** Binding-context snapshots without keeping ASTs or scanning source text. */
final class MathematicalEnvironment {
 private static final AtomicLong SEQUENCE=new AtomicLong();
 private static final Map<String,Long> REVISIONS=new ConcurrentHashMap<>();
 private static final IElementChangedListener LISTENER=event->recordChanges(event.getDelta());
 private static boolean listening;
 private MathematicalEnvironment() { }

 static synchronized void start() {
  if(!listening) { JavaCore.addElementChangedListener(LISTENER,ElementChangedEvent.POST_CHANGE|ElementChangedEvent.POST_RECONCILE);listening=true; }
 }
 static synchronized void stop() {
  if(listening) { JavaCore.removeElementChangedListener(LISTENER);listening=false; }
  REVISIONS.clear();
 }
 private static void recordChanges(IJavaElementDelta delta) {
  if(privateWorkingCopy(delta.getElement())) return;
  if(affectsBindings(delta)) {
   IJavaProject project=delta.getElement().getJavaProject();
   if(project!=null) REVISIONS.put(project.getHandleIdentifier(),SEQUENCE.incrementAndGet());
  }
  for(IJavaElementDelta child:delta.getAffectedChildren()) recordChanges(child);
 }
 private static boolean privateWorkingCopy(IJavaElement element) {
  IJavaElement ancestor=element instanceof ICompilationUnit ? element : element.getAncestor(IJavaElement.COMPILATION_UNIT);
  return ancestor instanceof ICompilationUnit unit && unit.isWorkingCopy() && !unit.equals(unit.getPrimary());
 }
 static boolean affectsBindings(IJavaElementDelta delta) {
  IJavaElement element=delta.getElement();
  if(privateWorkingCopy(element)) return false;
  int type=element.getElementType(),flags=delta.getFlags();
  if(delta.getKind()!=IJavaElementDelta.CHANGED)
   return type!=IJavaElement.JAVA_MODEL && type!=IJavaElement.INITIALIZER;
  int structural=IJavaElementDelta.F_CLASSPATH_CHANGED|IJavaElementDelta.F_RESOLVED_CLASSPATH_CHANGED
    |IJavaElementDelta.F_ARCHIVE_CONTENT_CHANGED|IJavaElementDelta.F_CLASSPATH_REORDER
    |IJavaElementDelta.F_ADDED_TO_CLASSPATH|IJavaElementDelta.F_REMOVED_FROM_CLASSPATH
    |IJavaElementDelta.F_CLASSPATH_ATTRIBUTES|IJavaElementDelta.F_SUPER_TYPES
    |IJavaElementDelta.F_MODIFIERS|IJavaElementDelta.F_ANNOTATIONS
    |IJavaElementDelta.F_OPENED|IJavaElementDelta.F_CLOSED;
  if((flags&structural)!=0) return true;
  if((flags&IJavaElementDelta.F_CONTENT)==0) return false;
  // JDT reports signature changes on members; body-only fine-grained changes stay at CU level.
  if(type==IJavaElement.CLASS_FILE || type==IJavaElement.METHOD || type==IJavaElement.FIELD || type==IJavaElement.TYPE
    || type==IJavaElement.IMPORT_DECLARATION || type==IJavaElement.PACKAGE_DECLARATION) return true;
  return type==IJavaElement.COMPILATION_UNIT && (flags&IJavaElementDelta.F_FINE_GRAINED)==0;
 }
 static Snapshot capture(IJavaProject project) throws CoreException {
  start();
  StringBuilder content=new StringBuilder();
  Map<String,Long> revisions=new HashMap<>();
  appendProject(project,content,new HashSet<>(),revisions);
  return new Snapshot(digest(content.toString()),Map.copyOf(revisions));
 }
 private static void appendProject(IJavaProject project,StringBuilder content,Set<String> visited,Map<String,Long> revisions) throws CoreException {
  String handle=project.getHandleIdentifier();
  if(!visited.add(handle)) return;
  revisions.put(handle,REVISIONS.getOrDefault(handle,0L));
  append(content,handle);append(content,Boolean.toString(project.exists()));
  append(content,digestOptions(project.getOptions(true)));
  append(content,project.getOutputLocation().toPortableString());
  for(IClasspathEntry entry:project.getRawClasspath()) { append(content,"raw");appendEntry(entry,content); }
  for(IClasspathEntry entry:project.getResolvedClasspath(true)) {
   append(content,"resolved");appendEntry(entry,content);
   if(entry.getEntryKind()==IClasspathEntry.CPE_PROJECT) {
    IJavaProject dependency=JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject(entry.getPath().lastSegment()));
    if(dependency.exists()) appendProject(dependency,content,visited,revisions);
   }
  }
 }
 private static void appendEntry(IClasspathEntry entry,StringBuilder content) {
  append(content,entry.toString());
  if(entry.getEntryKind()!=IClasspathEntry.CPE_LIBRARY) return;
  IResource resource=ResourcesPlugin.getWorkspace().getRoot().findMember(entry.getPath());
  Path path=resource!=null && resource.getLocation()!=null ? resource.getLocation().toFile().toPath() : entry.getPath().toFile().toPath();
  append(content,path.toAbsolutePath().normalize().toString());
  try {
   append(content,Boolean.toString(Files.exists(path)));
   if(Files.exists(path)) { append(content,Long.toString(Files.size(path)));append(content,Files.getLastModifiedTime(path).toString()); }
  } catch(IOException unavailable) { append(content,"UNREADABLE:"+unavailable.getClass().getName()); }
 }
 static String digestOptions(Map<String,String> options) {
  StringBuilder value=new StringBuilder();new TreeMap<>(options).forEach((key,item)->{append(value,key);append(value,item);});
  return digest(value.toString());
 }
 private static void append(StringBuilder target,String value) { target.append(value.length()).append(':').append(value); }
 static String digest(String value) { return digest(value.getBytes(StandardCharsets.UTF_8)); }
 static String digest(byte[] value) {
  try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
  catch(NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
 }
 record Snapshot(String digest,Map<String,Long> revisions) {
  Snapshot { revisions=Map.copyOf(revisions); }
  boolean matches(IJavaProject project) {
   try { return equals(capture(project)); } catch(CoreException unavailable) { return false; }
  }
 }
}
