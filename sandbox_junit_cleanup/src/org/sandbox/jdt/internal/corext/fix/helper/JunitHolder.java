package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.MethodInvocation;

public class JunitHolder {
	public ASTNode minv;
	public String minvname;
	public Set<ASTNode> nodesprocessed;
	public String value;
	public MethodInvocation method;
	public int count;

	public Annotation getAnnotation() {
		return (Annotation)minv;
	}

	public MethodInvocation getMethodInvocation() {
		return (MethodInvocation)minv;
	}
}