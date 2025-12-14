/*******************************************************************************
 * Copyright (c) 2021 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.PrefixExpression;

public class TreeUtilities {

    /** (1) Prüft, ob es sich um einen zusammengesetzten Zuweisungsoperator handelt. */
    public static boolean isCompoundAssignment(Assignment.Operator operator) {
        return operator == Assignment.Operator.PLUS_ASSIGN
            || operator == Assignment.Operator.MINUS_ASSIGN
            || operator == Assignment.Operator.TIMES_ASSIGN
            || operator == Assignment.Operator.DIVIDE_ASSIGN
            || operator == Assignment.Operator.BIT_AND_ASSIGN
            || operator == Assignment.Operator.BIT_OR_ASSIGN
            || operator == Assignment.Operator.BIT_XOR_ASSIGN
            || operator == Assignment.Operator.REMAINDER_ASSIGN
            || operator == Assignment.Operator.LEFT_SHIFT_ASSIGN
            || operator == Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN
            || operator == Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN;
    }

    /** (2) Prüft, ob es sich um eine Prä- oder Postfix-Inkrement-/Dekrement-Operation handelt. */
    public static boolean isPreOrPostfixOp(PrefixExpression.Operator operator) {
        return operator == PrefixExpression.Operator.INCREMENT
            || operator == PrefixExpression.Operator.DECREMENT;
    }
}
