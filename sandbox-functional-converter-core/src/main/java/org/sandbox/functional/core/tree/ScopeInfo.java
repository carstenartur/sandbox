/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.functional.core.tree;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks variable scope information for loop analysis.
 * 
 * <p>This class maintains information about variables in scope, including
 * which variables are modified and which are effectively final. This is
 * crucial for determining if a loop can be safely converted to use lambda
 * expressions, which require effectively final variables.</p>
 * 
 * @since 1.0.0
 */
public final class ScopeInfo {
    private final Set<String> outerScopeVariables;
    private final Set<String> modifiedVariables;
    private final Set<String> localVariables;
    
    /**
     * Creates a new empty scope info.
     */
    public ScopeInfo() {
        this.outerScopeVariables = new HashSet<>();
        this.modifiedVariables = new HashSet<>();
        this.localVariables = new HashSet<>();
    }
    
    private ScopeInfo(Set<String> outerScopeVariables, Set<String> modifiedVariables, Set<String> localVariables) {
        this.outerScopeVariables = new HashSet<>(outerScopeVariables);
        this.modifiedVariables = new HashSet<>(modifiedVariables);
        this.localVariables = new HashSet<>(localVariables);
    }
    
    /**
     * Creates a child scope that inherits this scope's variables.
     * 
     * <p>The child scope will see all variables from outer scope plus
     * this scope's local variables. Modifications are tracked across scopes.</p>
     * 
     * @return a new ScopeInfo representing a child scope
     */
    public ScopeInfo createChildScope() {
        Set<String> childOuter = new HashSet<>(outerScopeVariables);
        childOuter.addAll(localVariables);
        return new ScopeInfo(childOuter, new HashSet<>(modifiedVariables), new HashSet<>());
    }
    
    /**
     * Adds a local variable to this scope.
     * 
     * @param name the variable name
     */
    public void addLocalVariable(String name) { 
        localVariables.add(name); 
    }
    
    /**
     * Marks a variable as modified.
     * 
     * @param name the variable name
     */
    public void addModifiedVariable(String name) { 
        modifiedVariables.add(name); 
    }
    
    /**
     * Checks if a variable is effectively final (not modified).
     * 
     * @param variableName the variable name to check
     * @return true if the variable is not in the modified set
     */
    public boolean isEffectivelyFinal(String variableName) {
        return !modifiedVariables.contains(variableName);
    }
    
    /**
     * Gets an unmodifiable view of outer scope variables.
     * 
     * @return unmodifiable set of outer scope variables
     */
    public Set<String> getOuterScopeVariables() { 
        return Collections.unmodifiableSet(outerScopeVariables); 
    }
    
    /**
     * Gets an unmodifiable view of modified variables.
     * 
     * @return unmodifiable set of modified variables
     */
    public Set<String> getModifiedVariables() { 
        return Collections.unmodifiableSet(modifiedVariables); 
    }
    
    /**
     * Gets an unmodifiable view of local variables.
     * 
     * @return unmodifiable set of local variables
     */
    public Set<String> getLocalVariables() { 
        return Collections.unmodifiableSet(localVariables); 
    }
}
