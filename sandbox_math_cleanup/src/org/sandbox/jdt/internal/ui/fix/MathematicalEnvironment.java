/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Status;
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
  return capture(project,null);
 }
 static Snapshot capture(IJavaProject project,IProgressMonitor monitor) throws CoreException {
  return capture(project,true,monitor);
 }
 /** Foreground cache key only; full binary freshness remains a worker/apply guard. */
 static Snapshot captureStructure(IJavaProject project) throws CoreException {
  return capture(project,false,null);
 }
 private static Snapshot capture(IJavaProject project,boolean binaryContents,IProgressMonitor monitor) throws CoreException {
  cancelled(monitor);
  start();
  StringBuilder content=new StringBuilder();
  Map<String,Long> revisions=new HashMap<>();
  appendProject(project,content,new HashSet<>(),revisions,binaryContents,monitor);
  cancelled(monitor);
  return new Snapshot(digest(content.toString()),Map.copyOf(revisions));
 }
 private static void appendProject(IJavaProject project,StringBuilder content,Set<String> visited,Map<String,Long> revisions,boolean binaryContents,IProgressMonitor monitor) throws CoreException {
  cancelled(monitor);
  String handle=project.getHandleIdentifier();
  if(!visited.add(handle)) return;
  revisions.put(handle,REVISIONS.getOrDefault(handle,0L));
  append(content,handle);append(content,Boolean.toString(project.exists()));
  append(content,digestOptions(project.getOptions(true)));
  append(content,project.getOutputLocation().toPortableString());
  for(IClasspathEntry entry:project.getRawClasspath()) { cancelled(monitor);append(content,"raw");appendEntry(entry,content,binaryContents,monitor); }
  cancelled(monitor);
  for(IClasspathEntry entry:project.getResolvedClasspath(true)) {
   cancelled(monitor);append(content,"resolved");appendEntry(entry,content,binaryContents,monitor);
   if(entry.getEntryKind()==IClasspathEntry.CPE_PROJECT) {
    IJavaProject dependency=JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject(entry.getPath().lastSegment()));
    if(dependency.exists()) appendProject(dependency,content,visited,revisions,binaryContents,monitor);
   }
  }
 }
 private static void appendEntry(IClasspathEntry entry,StringBuilder content,boolean binaryContents,IProgressMonitor monitor) throws CoreException {
  append(content,entry.toString());
  if(!binaryContents || entry.getEntryKind()!=IClasspathEntry.CPE_LIBRARY) return;
  IResource resource=ResourcesPlugin.getWorkspace().getRoot().findMember(entry.getPath());
  Path path=resource!=null && resource.getLocation()!=null ? resource.getLocation().toFile().toPath() : entry.getPath().toFile().toPath();
  append(content,path.toAbsolutePath().normalize().toString());
  try {
   append(content,binaryFingerprint(path,monitor));
  } catch(OperationCanceledException cancelled) { throw cancelled;
  } catch(IOException | RuntimeException unavailable) { throw new CoreException(Status.error("Cannot verify classpath contents: "+path,unavailable)); }
 }
 static String binaryFingerprint(Path path) throws IOException {
  return binaryFingerprint(path,null);
 }
 static String binaryFingerprint(Path path,IProgressMonitor monitor) throws IOException {
  cancelled(monitor);
  if(Files.isSymbolicLink(path)) throw new IOException("Symbolic classpath roots cannot be verified: "+path);
  BasicFileAttributes attributes=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
  if(attributes.isRegularFile()) return fileFingerprint(path,monitor);
  if(!attributes.isDirectory()) throw new IOException("Unsupported classpath entry: "+path);
  StringBuilder content=new StringBuilder();
  List<Path> entries=directoryEntries(path,monitor);
  for(Path entry:entries) {
   cancelled(monitor);
   BasicFileAttributes item=Files.readAttributes(entry,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   if(item.isSymbolicLink() || !(item.isDirectory() || item.isRegularFile())) throw new IOException("Unsupported binary entry: "+entry);
   append(content,path.relativize(entry).toString().replace(java.io.File.separatorChar,'/'));
   append(content,item.isDirectory()?"directory":fileFingerprint(entry,monitor));
  }
  if(!entries.equals(directoryEntries(path,monitor))) throw new IOException("Classpath directory changed during verification: "+path);
  cancelled(monitor);
  return digest(content.toString());
 }
 private static List<Path> directoryEntries(Path root,IProgressMonitor monitor) throws IOException {
  cancelled(monitor);
  try(var paths=Files.walk(root)) { return paths.peek(path->cancelled(monitor)).filter(path->!path.equals(root)).sorted(java.util.Comparator.comparing(path->{cancelled(monitor);return root.relativize(path).toString();})).toList(); }
  catch(java.io.UncheckedIOException failure) { throw failure.getCause(); }
 }
 private static String fileFingerprint(Path path,IProgressMonitor monitor) throws IOException {
  cancelled(monitor);
  BasicFileAttributes before=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
  if(!before.isRegularFile()) throw new IOException("Not a regular binary file: "+path);
  try {
   MessageDigest hash=MessageDigest.getInstance("SHA-256");
   try(var input=Files.newInputStream(path,LinkOption.NOFOLLOW_LINKS)) {
    byte[] buffer=new byte[65536];for(;;) { cancelled(monitor);int count=input.read(buffer);cancelled(monitor);if(count==-1)break;hash.update(buffer,0,count); }
   }
   BasicFileAttributes after=Files.readAttributes(path,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
   if(!after.isRegularFile() || before.size()!=after.size() || !before.lastModifiedTime().equals(after.lastModifiedTime())
     || !java.util.Objects.equals(before.fileKey(),after.fileKey())) throw new IOException("Binary changed during verification: "+path);
   cancelled(monitor);return HexFormat.of().formatHex(hash.digest());
  } catch(NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
 }
 private static void cancelled(IProgressMonitor monitor) { if(monitor!=null && monitor.isCanceled()) throw new OperationCanceledException(); }
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
   return matches(project,null);
  }
  boolean matches(IJavaProject project,IProgressMonitor monitor) {
   try { return equals(capture(project,monitor)); } catch(CoreException unavailable) { return false; }
  }
 }
}
