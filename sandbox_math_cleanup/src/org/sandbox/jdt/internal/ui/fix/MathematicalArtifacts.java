/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;
import org.eclipse.core.runtime.FileLocator;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

/** Artifact receipts bind actual classloader resources, including Tycho's development mode. */
final class MathematicalArtifacts {
 private MathematicalArtifacts() { }
 static Receipt capture(boolean requireLoadedClassIdentity) throws IOException {
  Bundle bundle=FrameworkUtil.getBundle(MathematicalApplication.class);
  if(bundle==null) throw new IOException("The mathematics application is not running in its installed bundle");
  Path archive=FileLocator.getBundleFile(bundle).toPath();
  byte[] sdk;
  var sdkEntry=bundle.getEntry("lib/regelsuche-optimization-sdk.jar");
  if(sdkEntry==null) throw new IOException("The installed adapter does not contain its private SDK artifact");
  try(InputStream input=sdkEntry.openStream()) { sdk=input.readAllBytes(); }
  if(!Files.isRegularFile(archive)) throw new IOException("Artifact qualification requires a packaged adapter JAR, not a development directory");
  List<String> matchedClasses=new ArrayList<>();
  if(requireLoadedClassIdentity) {
   ClassLoader loader=MathematicalApplication.class.getClassLoader();
   try(JarFile jar=new JarFile(archive.toFile())) {
    var packagedSdk=jar.getJarEntry("lib/regelsuche-optimization-sdk.jar");
    if(packagedSdk==null) throw new IOException("The packaged adapter does not contain its private SDK artifact");
    try(InputStream input=jar.getInputStream(packagedSdk)) {
     if(!Arrays.equals(sdk,input.readAllBytes())) throw new IOException("Loaded SDK entry differs from packaged adapter JAR");
    }
    for(var entry:jar.stream().filter(item->item.getName().endsWith(".class") && item.getName().startsWith("org/sandbox/")).toList()) {
     byte[] packaged;try(InputStream input=jar.getInputStream(entry)) { packaged=input.readAllBytes(); }
     verifyResource(loader,entry.getName(),packaged);matchedClasses.add(entry.getName());
    }
   }
   if(matchedClasses.isEmpty()) throw new IOException("No adapter classes were available for qualification");
   verifyResource(loader,"lib/regelsuche-optimization-sdk.jar",sdk);
   int sdkClasses=0;
   try(ZipInputStream zip=new ZipInputStream(new java.io.ByteArrayInputStream(sdk))) {
    for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()) {
     if(entry.getName().startsWith("de/regelsuche/") && entry.getName().endsWith(".class")) {
      verifyResource(loader,entry.getName(),zip.readAllBytes());sdkClasses++;
     }
    }
   }
   if(sdkClasses==0) throw new IOException("No SDK classes were available for qualification");
  }
  return new Receipt(MathematicalEnvironment.digest(sdk),MathematicalEnvironment.digest(Files.readAllBytes(archive)),"sha256-jar-bytes",archive.toString(),List.copyOf(matchedClasses));
 }
 private static void verifyResource(ClassLoader loader,String name,byte[] expected) throws IOException {
  Class<?> loaded=null;
  if(name.endsWith(".class")) {
   try { loaded=Class.forName(name.substring(0,name.length()-6).replace('/','.'),false,loader); }
   catch(ClassNotFoundException | LinkageError unavailable) { throw new IOException("Cannot load qualified class: "+name,unavailable); }
  }
  try(InputStream resource=loaded==null?loader.getResourceAsStream(name):loaded.getResourceAsStream("/"+name)) {
   if(resource==null || !Arrays.equals(expected,resource.readAllBytes())) throw new IOException("Loaded resource differs from packaged artifact: "+name);
  }
 }
 record Receipt(String sdkSha256,String adapterBundleSha256,String adapterBundleHashFormat,String archive,List<String> matchedClasses) { }
}
