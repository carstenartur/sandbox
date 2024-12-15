package org.sandbox.jdt.internal.corext.fix.helper;

/*-
 * #%L
 * Sandbox junit cleanup
 * %%
 * Copyright (C) 2024 hammer
 * %%
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 * 
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 * #L%
 */

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
