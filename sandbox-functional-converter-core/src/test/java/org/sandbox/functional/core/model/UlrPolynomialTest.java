/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.functional.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.sandbox.functional.core.model.UlrExpression.BinaryOperator.ADD;
import static org.sandbox.functional.core.model.UlrExpression.BinaryOperator.MULTIPLY;
import static org.sandbox.functional.core.model.UlrExpression.BinaryOperator.SUBTRACT;
import static org.sandbox.functional.core.model.UlrExpression.Overflow.WRAP;
import static org.sandbox.functional.core.model.UlrExpression.Type.INT32;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sandbox.functional.core.model.UlrExpression.Binary;
import org.sandbox.functional.core.model.UlrExpression.BinaryOperator;
import org.sandbox.functional.core.model.UlrExpression.Read;
import org.sandbox.functional.core.model.UlrExpression.Unary;
import org.sandbox.functional.core.model.UlrExpression.UnaryOperator;

class UlrPolynomialTest {
    private static final Read INDEX = new Read("index", INT32);
    private static final Read TOTAL = new Read("total", INT32);
    private static final Read OTHER_STATE = new Read("other-state", INT32);
    private static final Set<String> STATES = Set.of(TOTAL.binding(), OTHER_STATE.binding());

    @Test void recognizesIndependentAdditionAndSubtractionInUpdateOrder() {
        var increment = update(ADD, TOTAL, INDEX);
        assertEquals(increment, UlrPolynomial.additiveUpdate(TOTAL, increment, STATES).orElseThrow());
        var decrement = update(SUBTRACT, increment, INDEX);
        assertEquals(decrement, UlrPolynomial.additiveUpdate(increment, decrement, STATES).orElseThrow());
    }

    @Test void retainsCoupledAndMultiplicativeUpdatesAsStateTransitions() {
        var other = new Unary(UnaryOperator.NEGATE, OTHER_STATE, INT32, WRAP);
        assertTrue(UlrPolynomial.additiveUpdate(TOTAL, update(ADD, TOTAL, other), STATES).isEmpty());
        assertTrue(UlrPolynomial.additiveUpdate(TOTAL, update(MULTIPLY, TOTAL, INDEX), STATES).isEmpty());
    }

    private static Binary update(BinaryOperator operator, UlrExpression left, UlrExpression right) {
        return new Binary(operator, left, right, INT32, WRAP);
    }
}
