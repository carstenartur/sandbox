package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;

public class JunitHolder {
	public Annotation minv;
	public String minvname;
	public Set<ASTNode> nodesprocessed;
	public String value;
}