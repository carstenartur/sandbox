/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.jdt.internal.corext.fix.math;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeCalculationPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = {"+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>="})
    void compoundAssignmentsCountTheirImplicitArithmeticOperation(String operator) {
        assertEquals(1, RuntimeCalculationPolicy.arithmeticOperations("value " + operator + " right;"));
    }

    @Test void simpleAssignmentCountsOnlyItsExplicitArithmetic() {
        assertEquals(0, RuntimeCalculationPolicy.arithmeticOperations("value = right;"));
        assertEquals(1, RuntimeCalculationPolicy.arithmeticOperations("value = left ^ right;"));
    }

    @Test void factoringAnIncomingCompoundAssignmentReducesFourOperationsToThree() {
        assertEquals(4, RuntimeCalculationPolicy.arithmeticOperations("value ^= (a & mask) ^ (b & mask);"));
        assertEquals(3, RuntimeCalculationPolicy.arithmeticOperations("value = value ^ ((a ^ b) & mask);"));
    }
}
