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
import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Status;
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
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.Viewer;

/**
 * Content provider for the variable table viewer that extracts variable bindings
 * from Java compilation units using AST parsing.
 */
public class JHViewContentProvider implements IStructuredContentProvider {
	
	private static final ILog logger = UsageViewPlugin.getDefault().getLog();

	@Override
	public Object[] getElements(Object inputElement) {
		VariableBindingVisitor variableVisitor = new VariableBindingVisitor();
		
		if (inputElement instanceof List list) {
			if (list.size() == 1) {
				Object object = list.get(0);
				if (object == null) {
					return new Object[0];
				}
				if (object instanceof WorkspaceRoot root) {
					logger.log(new Status(Status.INFO, UsageViewPlugin.PLUGIN_ID, "Processing workspace root: " + root.getName()));
				} else if (object instanceof File file) {
					logger.log(new Status(Status.INFO, UsageViewPlugin.PLUGIN_ID, "Processing file: " + file.getName()));
				} else if (object instanceof JavaElement) {
					IJavaElement javaElement = (IJavaElement) object;
					if (javaElement instanceof ICompilationUnit compilationUnit) {
						// now create the AST for the ICompilationUnits
						CompilationUnit astRoot = parseCompilationUnit(compilationUnit);
						variableVisitor.process(astRoot);
					} else if (javaElement instanceof IJavaProject javaProject) {
						// now create the AST for the ICompilationUnits
						try {
							Arrays.asList(javaProject.getAllPackageFragmentRoots()).parallelStream().forEach(packageRoot -> {
								processPackageFragmentRoot(variableVisitor, packageRoot);
							});
						} catch (JavaModelException e) {
							logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process package fragment roots", e));
						}
					} else if (javaElement instanceof IPackageFragment packageFragment) {
						processPackageFragment(variableVisitor, packageFragment);
					} else if (javaElement instanceof IPackageFragmentRoot packageRoot) {
						processPackageFragmentRoot(variableVisitor, packageRoot);
					}
				}
			}
		}
		// Debug logging removed - use Eclipse Logger for debugging if needed
		return variableVisitor.getVariableBindings().toArray();
	}

	private void processPackageFragmentRoot(VariableBindingVisitor variableVisitor, IPackageFragmentRoot packageRoot) {
		try {
			Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).stream().forEach(packageFragment -> {
				try {
					if (packageFragment.containsJavaResources()) {
						processPackageFragment(variableVisitor, packageFragment);
					}
				} catch (JavaModelException e) {
					logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process package fragment", e));
				}
			});
		} catch (JavaModelException e1) {
			logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to get package fragments from root", e1));
		}
	}

	private void processPackageFragment(VariableBindingVisitor variableVisitor, IPackageFragment packageFragment) {
		try {
			for (ICompilationUnit compilationUnit : packageFragment.getCompilationUnits()) {
				// now create the AST for the ICompilationUnits
				CompilationUnit astRoot = parseCompilationUnit(compilationUnit);
				variableVisitor.process(astRoot);
			}
		} catch (JavaModelException e1) {
			logger.log(new Status(Status.ERROR, UsageViewPlugin.PLUGIN_ID, "Failed to process compilation units", e1));
		}
	}

	private static CompilationUnit parseCompilationUnit(ICompilationUnit compilationUnit) {
		ASTParser parser = ASTParser.newParser(AST.JLS_Latest);
		parser.setKind(ASTParser.K_COMPILATION_UNIT);
		parser.setSource(compilationUnit);
		parser.setResolveBindings(true);
		return (CompilationUnit) parser.createAST(null);
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