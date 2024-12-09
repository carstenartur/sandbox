package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.Set;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public class JunitHolder {
	public ASTNode minv;
	public String minvname;
	public Set<ASTNode> nodesprocessed;
	public String value;
	public MethodInvocation method;
	public int count;

	public Annotation getAnnotation() {
		return (Annotation) minv;
	}

	public MethodInvocation getMethodInvocation() {
		return (MethodInvocation) minv;
	}

	public ImportDeclaration getImportDeclaration() {
		return (ImportDeclaration) minv;
	}

	public FieldDeclaration getFieldDeclaration() {
		return (FieldDeclaration) minv;
	}

	public TypeDeclaration getTypeDeclaration() {
		return (TypeDeclaration) minv;
	}
}