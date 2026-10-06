/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Strict arguments for the registered analysis-only-by-default application. */
record MathematicalArguments(String project, Path configuration, Path report, boolean apply, boolean acceptChecked) {
 static MathematicalArguments parse(String[] args) {
  Map<String,String> values=new HashMap<>();
  Set<String> flags=new HashSet<>();
  for(int index=0;index<args.length;index++) {
   String key=args[index];
   if(Set.of("--apply","--accept-checked").contains(key)) {
    if(!flags.add(key)) throw new IllegalArgumentException("Duplicate argument: "+key);
   } else if(Set.of("--project","--config","--report").contains(key)) {
    if(values.containsKey(key) || index+1==args.length || args[index+1].startsWith("--") || args[index+1].isBlank())
     throw new IllegalArgumentException("Missing or duplicate value: "+key);
    values.put(key,args[++index]);
   } else throw new IllegalArgumentException("Unknown argument: "+key);
  }
  for(String key:Set.of("--project","--config","--report"))
   if(!values.containsKey(key)) throw new IllegalArgumentException("Required argument: "+key);
  String project=values.get("--project");
  if(project.equals(".") || project.equals("..") || project.indexOf('/')>=0 || project.indexOf('\\')>=0)
   throw new IllegalArgumentException("--project must name one existing workspace project");
  Path configuration=Path.of(values.get("--config")),report=Path.of(values.get("--report"));
  if(configuration.toAbsolutePath().normalize().equals(report.toAbsolutePath().normalize()))
   throw new IllegalArgumentException("The report must not overwrite the configuration");
  return new MathematicalArguments(project,configuration,report,flags.contains("--apply"),flags.contains("--accept-checked"));
 }
}
