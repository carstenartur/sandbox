/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;

import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ExpressionStatement;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.sandbox.functional.core.model.FunctionalExpression;

/**
 * Source attachments for one extracted ULR model and its cleanup AST snapshot.
 * Operation order and lowering decisions belong to the model, not this context.
 * Identity keys distinguish identical-looking functions at different source sites.
 */
public record JdtStreamContext(ExpressionStatement statement, Expression source,
        ITypeBinding elementType, Map<FunctionalExpression, Expression> functions) {

    public JdtStreamContext {
        functions = Collections.unmodifiableMap(new IdentityHashMap<>(functions));
    }

    public Expression expression(FunctionalExpression function) {
        return Objects.requireNonNull(functions.get(function), "Function is not attached to this AST snapshot"); //$NON-NLS-1$
    }

    public ITypeBinding functionType(FunctionalExpression function) {
        return expression(function).resolveTypeBinding();
    }

    public ITypeBinding inputType(FunctionalExpression function) {
        return functionType(function).getFunctionalInterfaceMethod().getParameterTypes()[0];
    }

    public ITypeBinding outputType(FunctionalExpression function) {
        return functionType(function).getFunctionalInterfaceMethod().getReturnType();
    }
}
