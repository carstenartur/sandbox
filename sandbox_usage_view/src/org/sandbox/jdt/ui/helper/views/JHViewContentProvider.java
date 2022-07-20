/*******************************************************************************
 * Copyright (c) 2020 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer - initial API and implementation
 *******************************************************************************/
package org.sandbox.jdt.ui.helper.views;

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.internal.resources.File;
import org.eclipse.core.internal.resources.WorkspaceRoot;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.core.JavaElement;
//import org.eclipse.jdt.jeview.views.JEAttribute;
//import org.eclipse.jdt.jeview.views.JERoot;
//import org.eclipse.jdt.jeview.views.JavaElement;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

public class JHViewContentProvider implements IStructuredContentProvider {

	//	protected static final JEAttribute[] EMPTY = new JEAttribute[0];

	@Override
	public Object[] getElements(Object inputElement) {
		VarVisitor visitor= new VarVisitor(this);
		//		if (inputElement instanceof JEAttribute) {
		//			JEAttribute jeAttribute = (JEAttribute) inputElement;
		//			if (jeAttribute instanceof JERoot) {
		//				JERoot wrappedObject = (JERoot) jeAttribute;
		//				JEAttribute ja = wrappedObject.getChildren()[0];
		//				JavaElement label = (JavaElement) ja;
		if (inputElement instanceof List) {
			List list= (List) inputElement;
			if (list.size() == 1) {

				Object object= list.get(0);
				if (object == null) {
					return new Object[0];
				}
				if (object instanceof WorkspaceRoot) {
					WorkspaceRoot root= (WorkspaceRoot) object;
					System.err.println(root.getName());

				} else if (object instanceof File) {
					File file= (File) object;
					System.err.println(file.getName());
				} else if (object instanceof JavaElement) {
					IJavaElement javaElement= (IJavaElement) object;
					if (javaElement instanceof ICompilationUnit) {
						// now create the AST for the ICompilationUnits
						CompilationUnit parse= parse((ICompilationUnit) javaElement);
						parse.accept(visitor);
					} else if (javaElement instanceof IJavaProject) {
						// now create the AST for the ICompilationUnits
						IJavaProject jproject= (IJavaProject) javaElement;
						try {
							Arrays.asList(jproject.getAllPackageFragmentRoots()).parallelStream().forEach(pfr -> {
								extracted(visitor, pfr);
							});
						} catch (JavaModelException e) {
							e.printStackTrace();
						}
					} else if (javaElement instanceof IPackageFragment) {
						IPackageFragment pf= (IPackageFragment) javaElement;
						extracted(visitor, pf);
					} else if (javaElement instanceof IPackageFragmentRoot) {
						IPackageFragmentRoot pf= (IPackageFragmentRoot) javaElement;
						extracted(visitor, pf);
					}
				}
			}
		}
		for (IVariableBinding binding : visitor.getVars()) {
			System.out.println("Var name: " + binding.getName() + " Return type: " + binding.toString());
		}
		return visitor.getVars().toArray();
	}

	private void extracted(VarVisitor visitor, IPackageFragmentRoot pfr) {
		try {
			Arrays.asList(pfr.getJavaProject().getPackageFragments()).stream().forEach(pf -> {
				try {
					if (pf.containsJavaResources()) {
						extracted(visitor, pf);
					}
				} catch (JavaModelException e) {
					e.printStackTrace();
				}
			});
		} catch (JavaModelException e1) {
			e1.printStackTrace();
		}
	}

	private void extracted(VarVisitor visitor, IPackageFragment pf) {
		try {
			for (ICompilationUnit unit : pf.getCompilationUnits()) {
				// now create the AST for the ICompilationUnits
				CompilationUnit parse= parse(unit);
				parse.accept(visitor);
			}
		} catch (JavaModelException e1) {
			e1.printStackTrace();
		}
	}

	private static CompilationUnit parse(ICompilationUnit unit) {
		ASTParser parser= ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(unit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null); // parse
	}

	@Override
	public void dispose() {
		// do nothing
	}

	@Override
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		// do nothing
	}
}