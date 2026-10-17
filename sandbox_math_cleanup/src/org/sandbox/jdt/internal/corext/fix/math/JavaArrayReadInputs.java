/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.math;

import de.regelsuche.ast.Expr;
import de.regelsuche.sdk.optimization.JavaExpressions;
import de.regelsuche.sdk.optimization.NumericKind;
import de.regelsuche.sdk.optimization.NumericOperation;
import de.regelsuche.sdk.optimization.SourceEvaluationTrace.Occurrence;
import java.util.List;
import java.util.Set;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.Statement;

/** Compatibility entry point for ordered source captures and their total numeric prefix. */
final class JavaArrayReadInputs {
    private static final Set<NumericOperation> TOTAL_OPERATIONS = Set.of(NumericOperation.ADD,
            NumericOperation.SUBTRACT, NumericOperation.MULTIPLY, NumericOperation.NEGATE, NumericOperation.NOT,
            NumericOperation.AND, NumericOperation.OR, NumericOperation.XOR, NumericOperation.SHIFT_LEFT,
            NumericOperation.SHIFT_RIGHT, NumericOperation.UNSIGNED_SHIFT_RIGHT);

    private JavaArrayReadInputs() { }

    static boolean integral(NumericKind kind) {
        return kind != null && kind.integral() && kind != NumericKind.BIG_INTEGER;
    }

    static boolean total(NumericKind kind, Expr expression) {
        if (!integral(kind)) return false;
        var cast = JavaExpressions.castSourceKind(expression);
        return cast.isPresent() ? integral(cast.get())
                : TOTAL_OPERATIONS.contains(JavaExpressions.operationOf(expression).orElse(null));
    }

    static boolean totalPrefix(List<Occurrence> trace, List<JavaComputationRegion.OutputBinding> outputs) {
        return outputs.stream().allMatch(o -> integral(o.declaredKind()))
                && trace.stream().allMatch(o -> total(o.evaluatedKind(), o.expression()));
    }

    static boolean requiresBoundary(Statement statement, Set<String> regionalLocals, boolean totalPrefix) {
        return JavaSourceReadShape.requiresBoundary(statement, regionalLocals, totalPrefix);
    }

    static boolean supported(Expression read, Set<String> regionalLocals) {
        return JavaSourceReadShape.supported(read, regionalLocals);
    }
}
