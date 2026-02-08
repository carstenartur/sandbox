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
package org.sandbox.jdt.triggerpattern.encoding;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jdt.core.dom.ASTNode;
import org.sandbox.jdt.triggerpattern.cleanup.MatchHolder;

/**
 * Holder class for encoding-related pattern matches.
 * 
 * <p>Implements {@link MatchHolder} for type-safe access to match data
 * in declarative rewrite operations.</p>
 * 
 * @since 1.2.5
 */
public class EncodingHolder implements MatchHolder {
    
    private ASTNode minv;
    private Map<String, Object> bindings = new HashMap<>();
    
    @Override
    public ASTNode getMinv() {
        return minv;
    }
    
    public void setMinv(ASTNode minv) {
        this.minv = minv;
    }
    
    @Override
    public Map<String, Object> getBindings() {
        return bindings;
    }
    
    public void setBindings(Map<String, Object> bindings) {
        this.bindings = bindings != null ? bindings : new HashMap<>();
    }
}
