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
package org.sandbox.functional.core.builder;

import java.util.HashSet;
import java.util.Set;
import org.sandbox.functional.core.model.*;
import org.sandbox.functional.core.renderer.StringRenderer;
import org.sandbox.functional.core.terminal.*;
import org.sandbox.functional.core.transformer.LoopModelTransformer;

/**
 * Generates stream code strings from a LoopModel.
 * 
 * <p>This is a convenience wrapper around {@link LoopModelTransformer} 
 * with {@link StringRenderer}.</p>
 */
public class StreamCodeBuilder {
    
    private final LoopModel model;
    private final LoopModelTransformer<String> transformer;
    
    public StreamCodeBuilder(LoopModel model) {
        this.model = model;
        this.transformer = new LoopModelTransformer<>(new StringRenderer());
    }
    
    public String build() {
        return transformer.transform(model);
    }
    
    public boolean canBuild() {
        return transformer.canTransform(model);
    }
    
    public Set<String> getRequiredImports() {
        Set<String> imports = new HashSet<>();
        
        if (model.getSource() != null) {
            switch (model.getSource().getType()) {
                case ARRAY -> imports.add("java.util.Arrays");
                case ITERABLE -> imports.add("java.util.stream.StreamSupport");
                case INT_RANGE -> imports.add("java.util.stream.IntStream");
                default -> {}
            }
        }
        
        if (model.getTerminal() instanceof CollectTerminal c) {
            if (c.collectorType() != CollectTerminal.CollectorType.TO_LIST) {
                imports.add("java.util.stream.Collectors");
            }
        }
        
        return imports;
    }
}
