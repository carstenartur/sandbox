/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import de.regelsuche.sdk.optimization.*;

class MathCleanUpOptionsTest {
 @Test void disabledDefaultSelectsOnlyBigIntegerAndPreservesJava() {
  var options= assertDoesNotThrow(() -> MathCleanUpOptions.defaults(17));
  assertFalse(options.enabled()); assertEquals(Set.of(NumericKind.BIG_INTEGER),options.kinds());
  assertEquals(SafetyProfile.PRESERVE_JAVA,options.safety());
  assertEquals(OptimizationGoal.LOWER_ESTIMATED_RUNTIME,options.goal());
  assertEquals(100000,options.workBudget());assertEquals(2000,options.maxStates());
  assertFalse(options.checkedOptIn());assertEquals(17,options.targetJava());assertEquals(List.of(),options.exclusions());
 }
 @Test void kindsSafetyAndGoalAreIndependentAndRoundTrip() {
  for(var kind:NumericKind.values()) for(var safety:SafetyProfile.values()) for(var goal:OptimizationGoal.values()) {
   var map=new HashMap<String,String>();map.put(MathCleanUpOptions.CLEANUP,"true");map.put(MathCleanUpOptions.KINDS,kind.name());
   map.put(MathCleanUpOptions.SAFETY,safety.name());map.put(MathCleanUpOptions.GOAL,goal.name());
   map.put(MathCleanUpOptions.CHECKED_OPT_IN,Boolean.toString(safety==SafetyProfile.CHECKED_THROW));
   var value=assertDoesNotThrow(() -> MathCleanUpOptions.parse(map,21));
   assertTrue(value.enabled());assertEquals(Set.of(kind),value.kinds());assertEquals(safety,value.safety());assertEquals(goal,value.goal());
   assertEquals(value,assertDoesNotThrow(() -> MathCleanUpOptions.parse(value.toMap(),21)));
  }
 }
 @Test void unknownNamesEnumsBooleansAndNullValuesAreRejected() {
  for(var pair:List.of(Map.entry("cleanup.mathematics.typo","true"),Map.entry(MathCleanUpOptions.KINDS,"BIG_DECIMAL"),Map.entry(MathCleanUpOptions.KINDS,"INT,INT"),Map.entry(MathCleanUpOptions.KINDS,""),Map.entry(MathCleanUpOptions.KINDS,"INT,"),Map.entry(MathCleanUpOptions.SAFETY,"FAST"),Map.entry(MathCleanUpOptions.GOAL,"SPEED"),Map.entry(MathCleanUpOptions.CLEANUP,"yes"),Map.entry(MathCleanUpOptions.CHECKED_OPT_IN,"TRUE"))) {
   assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(Map.of(pair.getKey(),pair.getValue()),17),pair.toString());
  }
  var nullValue=new HashMap<String,String>();nullValue.put(MathCleanUpOptions.GOAL,null);
  assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(nullValue,17));
 }
 @Test void checkedContractNeedsSeparateExplicitConsentEvenWhenDisabled() {
  var map=new HashMap<String,String>();map.put(MathCleanUpOptions.SAFETY,"CHECKED_THROW");
  assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(map,17));
  map.put(MathCleanUpOptions.CHECKED_OPT_IN,"true");
  assertTrue(assertDoesNotThrow(()->MathCleanUpOptions.parse(map,17)).checkedOptIn());
 }
 @Test void budgetsAndTargetAreBounded() {
  for(String value:List.of("0","-1","100000001","9223372036854775808","work")) assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.WORK_BUDGET,value),17));
  for(String value:List.of("0","-1","1000001","2147483648")) assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.MAX_STATES,value),17));
  for(int target:List.of(7,26)) assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.defaults(target));
  var upper=assertDoesNotThrow(()->MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.WORK_BUDGET,"100000000",MathCleanUpOptions.MAX_STATES,"1000000"),25));
  assertEquals(100000000,upper.workBudget());assertEquals(1000000,upper.maxStates());
 }
 @Test void collectionsAreDefensiveAndExclusionsValidated() {
  var kinds=EnumSet.of(NumericKind.INT,NumericKind.LONG);var exclusions=new ArrayList<>(List.of("**/generated/**"));
  var value=new MathCleanUpOptions(true,kinds,SafetyProfile.PRESERVE_JAVA,OptimizationGoal.READABILITY,1,1,false,8,exclusions);
  kinds.clear();exclusions.clear();assertEquals(2,value.kinds().size());assertEquals(1,value.exclusions().size());
  assertThrows(UnsupportedOperationException.class,()->value.kinds().clear());assertThrows(UnsupportedOperationException.class,()->value.exclusions().clear());
  for(String pattern:List.of("[","a;;b","a;","a\nb")) assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.EXCLUSIONS,pattern),17));
 }
 @Test void underflowIsExplicitlyUnsupportedAndOtherCleanupKeysAreAllowed() {
  var failure=assertThrows(IllegalArgumentException.class,()->MathCleanUpOptions.parse(Map.of(MathCleanUpOptions.UNDERFLOW,"true"),17));assertTrue(failure.getMessage().contains("UNSUPPORTED_UNDERFLOW_POLICY"));
  var options=assertDoesNotThrow(()->MathCleanUpOptions.parse(Map.of("cleanup.unrelated","true",MathCleanUpOptions.UNDERFLOW,"false"),17));assertFalse(options.enabled());
 }
}
