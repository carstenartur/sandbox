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

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;

public class TreeUtilities {

	public static boolean isPreOrPostfixOp(ASTNode correspondingTree) {
		if(ASTNodes.hasOperator((PostfixExpression) correspondingTree, PostfixExpression.Operator.INCREMENT,PostfixExpression.Operator.DECREMENT)) {
			return true;
		}
		return ASTNodes.hasOperator((PrefixExpression) correspondingTree, PrefixExpression.Operator.INCREMENT,PrefixExpression.Operator.DECREMENT);
	}

	public static boolean isCompoundAssignementAssignement(ASTNode correspondingTree) {
		return ASTNodes.hasOperator((Assignment) correspondingTree, Assignment.Operator.BIT_AND_ASSIGN,
				Assignment.Operator.BIT_OR_ASSIGN,
				Assignment.Operator.PLUS_ASSIGN,
				Assignment.Operator.MINUS_ASSIGN,
				Assignment.Operator.TIMES_ASSIGN,
				Assignment.Operator.DIVIDE_ASSIGN,
				Assignment.Operator.REMAINDER_ASSIGN,
				Assignment.Operator.LEFT_SHIFT_ASSIGN,
				Assignment.Operator.RIGHT_SHIFT_SIGNED_ASSIGN,
				Assignment.Operator.RIGHT_SHIFT_UNSIGNED_ASSIGN);
	}
}
