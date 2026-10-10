/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 at https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import java.nio.file.FileSystems;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.OptimizationGoal;
import de.regelsuche.sdk.optimization.SafetyProfile;

/** Immutable explicit numerical contract, independent of the host Java level. */
public record MathCleanUpOptions(boolean enabled, Set<NumericKind> kinds, SafetyProfile safety,
        OptimizationGoal goal, long workBudget, int maxStates, boolean checkedOptIn,
        int targetJava, List<String> exclusions, boolean mathematicalOptIn) {
 public MathCleanUpOptions(boolean enabled, Set<NumericKind> kinds, SafetyProfile safety,
        OptimizationGoal goal, long workBudget, int maxStates, boolean checkedOptIn,
        int targetJava, List<String> exclusions) {
  this(enabled,kinds,safety,goal,workBudget,maxStates,checkedOptIn,targetJava,exclusions,false);
 }
 public static final String CLEANUP = "cleanup.mathematics";
 public static final String KINDS = "cleanup.mathematics.numericKinds";
 public static final String SAFETY = "cleanup.mathematics.safetyProfile";
 public static final String GOAL = "cleanup.mathematics.goal";
 public static final String WORK_BUDGET = "cleanup.mathematics.workBudget";
 public static final String MAX_STATES = "cleanup.mathematics.maxStates";
 public static final String CHECKED_OPT_IN = "cleanup.mathematics.checkedOptIn";
 public static final String EXCLUSIONS = "cleanup.mathematics.exclusions";
 public static final String UNDERFLOW = "cleanup.mathematics.underflowChecks";
 public static final String MATHEMATICAL_OPT_IN = "cleanup.mathematics.mathematicalOptIn";
 public static final String MATHEMATICAL_WARNING = "Mathematical mode may change overflow behavior and BigInteger result reference identity. Proven local ranges and contract changes are documented in Javadoc.";
 public static final String CHECKED_WARNING = "CHECKED_THROW changes the Java contract: numerical violations may throw ArithmeticException. Both the original and replacement operations must be checked.";
 private static final Set<String> KEYS= Set.of(CLEANUP,KINDS,SAFETY,GOAL,WORK_BUDGET,MAX_STATES,CHECKED_OPT_IN,EXCLUSIONS,UNDERFLOW,MATHEMATICAL_OPT_IN);

 public MathCleanUpOptions {
  Objects.requireNonNull(kinds,"kinds"); Objects.requireNonNull(safety,"safety");
  Objects.requireNonNull(goal,"goal"); Objects.requireNonNull(exclusions,"exclusions");
  if(kinds.isEmpty()) throw new IllegalArgumentException("Select at least one numeric kind");
  kinds=Set.copyOf(kinds);
  if(workBudget<1 || workBudget>100_000_000 || maxStates<1 || maxStates>1_000_000)
   throw new IllegalArgumentException("Work budget must be 1..100000000; maximum states must be 1..1000000");
  if(targetJava<8 || targetJava>25) throw new IllegalArgumentException("Unsupported target Java: "+targetJava);
  if(safety==SafetyProfile.CHECKED_THROW && !checkedOptIn) throw new IllegalArgumentException(CHECKED_WARNING);
  if(mathematicalOptIn && safety!=SafetyProfile.PRESERVE_JAVA)
   throw new IllegalArgumentException("Mathematical mode cannot be combined with checked or fallback safety profiles");
  exclusions=List.copyOf(exclusions);
  for(String exclusion:exclusions) {
   if(exclusion.isBlank() || exclusion.indexOf(';')>=0 || exclusion.indexOf('\n')>=0 || exclusion.indexOf('\r')>=0)
    throw new IllegalArgumentException("Invalid exclusion: "+exclusion);
   FileSystems.getDefault().getPathMatcher("glob:"+exclusion);
  }
 }

 public static MathCleanUpOptions defaults(int targetJava) {
  return new MathCleanUpOptions(false,Set.of(NumericKind.BIG_INTEGER),SafetyProfile.PRESERVE_JAVA,
    OptimizationGoal.LOWER_ESTIMATED_RUNTIME,100_000,2000,false,targetJava,List.of());
 }

 public static MathCleanUpOptions parse(Map<String,String> values,int targetJava) {
  Objects.requireNonNull(values,"values");
  for(var entry:values.entrySet()) {
   String key=entry.getKey();
   if(key!=null && (key.equals(CLEANUP) || key.startsWith(CLEANUP+"."))
      && (!KEYS.contains(key) || entry.getValue()==null))
    throw new IllegalArgumentException("Unknown or null mathematics option: "+key);
  }
  var defaults=defaults(targetJava);
  if(bool(values,UNDERFLOW,false)) throw new IllegalArgumentException("UNSUPPORTED_UNDERFLOW_POLICY: no tiny-and-inexact detector is implemented");
  var kinds=EnumSet.noneOf(NumericKind.class);
  for(String token:values.getOrDefault(KINDS,"BIG_INTEGER").split(",",-1)) {
   if(!kinds.add(NumericKind.valueOf(token.trim()))) throw new IllegalArgumentException("Duplicate numeric kind: "+token);
  }
  String paths=values.getOrDefault(EXCLUSIONS,"");
  return new MathCleanUpOptions(bool(values,CLEANUP,false),kinds,
    SafetyProfile.valueOf(values.getOrDefault(SAFETY,defaults.safety.name())),
    OptimizationGoal.valueOf(values.getOrDefault(GOAL,defaults.goal.name())),
    Long.parseLong(values.getOrDefault(WORK_BUDGET,Long.toString(defaults.workBudget))),
    Integer.parseInt(values.getOrDefault(MAX_STATES,Integer.toString(defaults.maxStates))),
    bool(values,CHECKED_OPT_IN,false),targetJava,
    paths.isEmpty()?List.of():Arrays.stream(paths.split(";",-1)).map(String::trim).toList(),
    bool(values,MATHEMATICAL_OPT_IN,false));
 }

 public Map<String,String> toMap() {
  Map<String,String> values=new LinkedHashMap<>();
  values.put(CLEANUP,Boolean.toString(enabled));
  values.put(KINDS,kinds.stream().sorted().map(Enum::name).collect(Collectors.joining(",")));
  values.put(SAFETY,safety.name());values.put(GOAL,goal.name());
  values.put(WORK_BUDGET,Long.toString(workBudget));values.put(MAX_STATES,Integer.toString(maxStates));
  values.put(CHECKED_OPT_IN,Boolean.toString(checkedOptIn));values.put(EXCLUSIONS,String.join(";",exclusions));
  values.put(UNDERFLOW,"false");values.put(MATHEMATICAL_OPT_IN,Boolean.toString(mathematicalOptIn));return Map.copyOf(values);
 }

 private static boolean bool(Map<String,String> values,String key,boolean fallback) {
  String value=values.get(key);if(value==null)return fallback;
  return switch(value) {case "true" -> true;case "false" -> false;
   default -> throw new IllegalArgumentException("Invalid boolean for "+key+": "+value);};
 }
}
