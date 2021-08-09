package org.sandbox.jdt.ui.tests.quickfix;

import java.util.Stack;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.sandbox.jdt.internal.common.ReferenceHolder;

public class ExpectationTracer extends ReferenceHolder<ASTNode, SimpleName>{

	public Stack<Integer> stack = new Stack<>();
    
	@Override
	public int hashCode() {
		return super.hashCode();
	}

	@Override
	public boolean equals(Object o) {
		return super.equals(o);
	}
}
