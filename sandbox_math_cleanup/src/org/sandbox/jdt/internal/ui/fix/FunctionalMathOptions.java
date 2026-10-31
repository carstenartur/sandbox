/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.ui.fix;

import java.util.HashMap;
import java.util.Map;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.osgi.framework.Bundle;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** An explicit whole-file transaction; ordinary math and converter profiles remain independent. */
public record FunctionalMathOptions(Map<String,String> requested,Map<String,String> persisted) {
 public static final String ENABLED="cleanup.loop_conversion.with_mathematics";
 public FunctionalMathOptions { requested=Map.copyOf(requested);persisted=Map.copyOf(persisted); }
 static FunctionalMathOptions capture(IJavaProject project,Map<String,String> requested) {
  return new FunctionalMathOptions(relevant(requested),persisted(project));
 }
 public static boolean enabled(Map<String,String> values) { return "true".equals(values.get(ENABLED)); }
 public static boolean converterAvailable() {
  Bundle bundle=Platform.getBundle("sandbox_functional_converter");
  return bundle!=null && (bundle.getState() & (Bundle.RESOLVED|Bundle.STARTING|Bundle.ACTIVE))!=0;
 }
 public static void validate(Map<String,String> values,MathCleanUpOptions math,boolean available) {
  String flag=values.getOrDefault(ENABLED,"false");
  if(!flag.equals("true") && !flag.equals("false")) throw new IllegalArgumentException("Invalid combined cleanup option");
  // JDT validates every registered cleanup, including inactive ones. Keep a
  // saved opt-in dormant while the mathematics master is disabled.
  if(!enabled(values) || !math.enabled()) return;
  if(!available) throw new IllegalArgumentException("FUNCTIONAL_CONVERTER_UNAVAILABLE: install the Functional Converter to use combined cleanup");
  if(!("true".equals(values.get("cleanup.loop_conversion.enabled"))
    || "true".equals(values.get("cleanup.functionalloop")) || "true".equals(values.get("cleanup.use_functional_loop_v2"))))
   throw new IllegalArgumentException("Combined cleanup requires both mathematics and loop conversion to be enabled");
  if(math.safety()!=SafetyProfile.PRESERVE_JAVA || math.mathematicalOptIn())
   throw new IllegalArgumentException("Combined cleanup requires Preserve Java behavior without mathematical mode");
 }
 boolean current(IJavaProject project,Map<String,String> values) {
  return requested.equals(relevant(values)) && persisted.equals(persisted(project)) && converterAvailable();
 }
 private static Map<String,String> persisted(IJavaProject project) {
  return relevant(CleanUpPreferenceUtil.loadOptions(new ProjectScope(project.getProject())));
 }
 private static Map<String,String> relevant(Map<String,String> values) {
  var result=new HashMap<String,String>();
  if(values!=null) values.forEach((key,value)->{
   if(key.equals(MathCleanUpOptions.CLEANUP) || key.startsWith(MathCleanUpOptions.CLEANUP+".")
     || key.startsWith("cleanup.loop_conversion.") || key.startsWith("cleanup.functionalloop")
     || key.equals("cleanup.use_functional_loop_v2")) result.put(key,value);
  });
  return Map.copyOf(result);
 }
}
