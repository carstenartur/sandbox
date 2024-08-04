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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.core.SourceType;
//import org.eclipse.jdt.jeview.views.JEResource;
//import org.eclipse.jdt.jeview.views.JERoot;
//import org.eclipse.jdt.jeview.views.JavaElement;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.ContributionItemFactory;
import org.eclipse.ui.keys.IBindingService;
import org.eclipse.ui.part.IShowInSource;
import org.eclipse.ui.part.IShowInTarget;
import org.eclipse.ui.part.ShowInContext;
import org.eclipse.ui.part.ViewPart;
import org.sandbox.jdt.ui.helper.views.colum.AbstractColumn;
import org.sandbox.jdt.ui.helper.views.colum.DeclaringMethodColumn;
import org.sandbox.jdt.ui.helper.views.colum.DeprecatedColumn;
import org.sandbox.jdt.ui.helper.views.colum.NameColumn;
import org.sandbox.jdt.ui.helper.views.colum.PackageColumn;
import org.sandbox.jdt.ui.helper.views.colum.QualifiednameColumn;

public class JavaHelperView extends ViewPart implements IShowInSource, IShowInTarget {

	Logger logger= PlatformUI.getWorkbench().getService(org.eclipse.e4.core.services.log.Logger.class);
	TableViewer tableViewer;
	private Table table;
	//	private JERoot fInput;
	private Action fRefreshAction;
	private Action fResetAction;
	private Action fElementAtAction;
	private Action fPropertiesAction;
	//	private Action fFocusAction;

	private Action fCodeSelectAction;

	public JavaHelperView() {
	}

	@Override
	public void createPartControl(Composite parent) {
		parent.setLayout(new GridLayout(1, false));

		tableViewer= new TableViewer(parent, SWT.BORDER | SWT.FULL_SELECTION);
		tableViewer.setColumnProperties(new String[] {});
		tableViewer.setUseHashlookup(true);
		table= tableViewer.getTable();
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		table.setHeaderVisible(true);
		table.setHeaderBackground(parent.getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND));
		table.setLinesVisible(true);
		tableViewer.setContentProvider(new JHViewContentProvider());
		// This will create the columns for the table
		AbstractColumn.addColumn(tableViewer, new NameColumn());
		AbstractColumn.addColumn(tableViewer, new QualifiednameColumn());
		AbstractColumn.addColumn(tableViewer, new PackageColumn());
		AbstractColumn.addColumn(tableViewer, new DeprecatedColumn());
		AbstractColumn.addColumn(tableViewer, new DeclaringMethodColumn());

		tableViewer.setComparator(AbstractColumn.getComparator());
		reset();
		makeActions();
		hookContextMenu();
		getSite().setSelectionProvider(new JHViewSelectionProvider(tableViewer));
		contributeToActionBars();
		// tableViewer.addSelectionChangedListener(event -> fCopyAction.setEnabled(!
		// event.getSelection().isEmpty()));
		// tableViewer.addSelectionChangedListener(event -> fCopyAction.setEnabled(!
		// event.getSelection().isEmpty()));
	}

	private void contributeToActionBars() {
		IActionBars bars= getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
		bars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), fRefreshAction);
		// bars.setGlobalActionHandler(ActionFactory.COPY.getId(), fCopyAction);
		bars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), fPropertiesAction);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(fCodeSelectAction);
		manager.add(fElementAtAction);
		manager.add(fResetAction);
		manager.add(fRefreshAction);
		manager.add(new Separator());
		// fDrillDownAdapter.addNavigationActions(manager);
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(fCodeSelectAction);
		manager.add(fElementAtAction);
		// manager.add(fCreateFromHandleAction);
		manager.add(fResetAction);
		// manager.add(fLogDeltasAction);
		manager.add(new Separator());
		manager.add(fRefreshAction);
	}

	private void makeActions() {
		fCodeSelectAction= new Action("Set Input from Editor (&codeSelect)", JHPluginImages.IMG_SET_FOCUS_CODE_SELECT) { //$NON-NLS-1$
			@Override
			public void run() {
				IEditorPart editor= getSite().getPage().getActiveEditor();
				if (editor == null) {
					setEmptyInput();
					return;
				}
				IEditorInput input= editor.getEditorInput();
				ISelectionProvider selectionProvider= editor.getSite().getSelectionProvider();
				if (input == null || selectionProvider == null) {
					setEmptyInput();
					return;
				}
				ISelection selection= selectionProvider.getSelection();
				if (!(selection instanceof ITextSelection)) {
					setEmptyInput();
					return;
				}
				IJavaElement javaElement= input.getAdapter(IJavaElement.class);
				if (javaElement == null) {
					setEmptyInput();
					return;
				}

				IJavaElement[] resolved;
				try {
					resolved= codeResolve(javaElement, (ITextSelection) selection);
				} catch (JavaModelException e) {
					setEmptyInput();
					return;
				}
				if (resolved.length == 0) {
					setEmptyInput();
					return;
				}

				List<IJavaElement> asList= Arrays.asList(resolved);

				setInput(asList);
			}
		};
		fCodeSelectAction.setToolTipText("Set input from current editor's selection (codeSelect)"); //$NON-NLS-1$
		//		fFocusAction = new Action() {
		//			@Override
		//			public void run() {
		//				Object selected = ((IStructuredSelection) tableViewer.getSelection()).getFirstElement();
		//				if (selected instanceof JavaElement) {
		//					setSingleInput((IJavaModel) ((JavaElement) selected).getJavaElement());
		//				} else if (selected instanceof JEResource) {
		//					setSingleInput((IJavaModel) ((JEResource) selected).getResource());
		//				}
		//			}
		//		};
		//		fFocusAction.setToolTipText("Focus on Selection");
		fPropertiesAction= new Action("&Properties", JHPluginImages.IMG_PROPERTIES) { //$NON-NLS-1$
			@Override
			public void run() {
				String viewId= IPageLayout.ID_PROP_SHEET;
				IWorkbenchPage page= getViewSite().getPage();
				IViewPart view;
				try {
					view= page.showView(viewId);
					page.activate(JavaHelperView.this);
					page.bringToTop(view);
				} catch (PartInitException e) {
					logger.error(e, "could not find Properties view"); //$NON-NLS-1$
				}
			}
		};
		fPropertiesAction.setActionDefinitionId(IWorkbenchCommandConstants.FILE_PROPERTIES);
		fResetAction= new Action("&Reset View", getJavaModelImageDescriptor()) { //$NON-NLS-1$
			@Override
			public void run() {
				reset();
			}
		};
		fResetAction.setToolTipText("Reset View to JavaModel"); //$NON-NLS-1$
		fRefreshAction= new Action("Re&fresh", JHPluginImages.IMG_REFRESH) { //$NON-NLS-1$
			@Override
			public void run() {
				BusyIndicator.showWhile(getSite().getShell().getDisplay(), () -> tableViewer.refresh());
			}
		};
		fRefreshAction.setToolTipText("Refresh"); //$NON-NLS-1$
		fRefreshAction.setActionDefinitionId("org.eclipse.ui.file.refresh"); //$NON-NLS-1$
		fElementAtAction= new Action("Set Input from Editor location (&getElementAt)", JHPluginImages.IMG_SET_FOCUS) { //$NON-NLS-1$
			@Override
			public void run() {
				IEditorPart editor= getSite().getPage().getActiveEditor();
				if (editor == null) {
					setEmptyInput();
					return;
				}
				IEditorInput input= editor.getEditorInput();
				ISelectionProvider selectionProvider= editor.getSite().getSelectionProvider();
				if (input == null || selectionProvider == null) {
					setEmptyInput();
					return;
				}
				ISelection selection= selectionProvider.getSelection();
				if (!(selection instanceof ITextSelection)) {
					setEmptyInput();
					return;
				}
				IJavaElement javaElement= input.getAdapter(IJavaElement.class);
				if (javaElement == null) {
					setEmptyInput();
					return;
				}

				IJavaElement resolved;
				try {
					resolved= getElementAtOffset(javaElement, (ITextSelection) selection);
				} catch (JavaModelException e) {
					setEmptyInput();
					return;
				}
				if (resolved == null) {
					setEmptyInput();
					return;
				}

				IResource correspondingResource= resolved.getResource();
				setSingleInput(correspondingResource);

			}

		};
		fElementAtAction.setToolTipText("Set input from current editor's selection location (getElementAt)"); //$NON-NLS-1$
	}

	void setEmptyInput() {
		setInput(Collections.<IJavaModel>emptyList());
	}

	static IJavaElement[] codeResolve(IJavaElement input, ITextSelection selection) throws JavaModelException {
		if (input instanceof ICodeAssist) {
			if (input instanceof ICompilationUnit) {
				reconcile((ICompilationUnit) input);
			}
			IJavaElement[] elements= ((ICodeAssist) input).codeSelect(selection.getOffset(), selection.getLength());
			if (elements != null && elements.length > 0) {
				return elements;
			}
		}
		return new IJavaElement[0];
	}

	static IJavaElement getElementAtOffset(IJavaElement input, ITextSelection selection) throws JavaModelException {
		if (input instanceof ICompilationUnit cunit) {
			reconcile(cunit);
			IJavaElement ref= cunit.getElementAt(selection.getOffset());
			if (ref == null) {
				return input;
			}
			return ref;
		}
		if (input instanceof IClassFile) {
			IJavaElement ref= ((IClassFile) input).getElementAt(selection.getOffset());
			if (ref != null) {
				return ref;
			}
		}
		return input;
	}

	//	private void addFocusActionOrNot(IMenuManager manager) {
	//		if (tableViewer.getSelection() instanceof IStructuredSelection) {
	//			IStructuredSelection structuredSelection = (IStructuredSelection) tableViewer.getSelection();
	//			if (structuredSelection.size() == 1) {
	//				Object first = structuredSelection.getFirstElement();
	//				if (first instanceof JavaElement) {
	//					IJavaElement javaElement = ((JavaElement) first).getJavaElement();
	//					if (javaElement != null) {
	//						String name = javaElement.getElementName();
	//						fFocusAction.setText("Fo&cus On '" + name + '\'');
	//						manager.add(fFocusAction);
	//					}
	//				} else if (first instanceof JEResource) {
	//					IResource resource = ((JEResource) first).getResource();
	//					if (resource != null) {
	//						String name = resource.getName();
	//						fFocusAction.setText("Fo&cus On '" + name + '\'');
	//						manager.add(fFocusAction);
	//					}
	//				}
	//			}
	//		}
	//	}

	/* see JavaModelUtil.reconcile((ICompilationUnit) input) */
	static void reconcile(ICompilationUnit unit) throws JavaModelException {
		synchronized (unit) {
			unit.reconcile(ICompilationUnit.NO_AST, false /* don't force problem detection */,
					null /* use primary owner */, null /* no progress monitor */);
		}
	}

	private ImageDescriptor getJavaModelImageDescriptor() {
		JavaElementLabelProvider lp= new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_SMALL_ICONS);
		Image modelImage= lp.getImage(getJavaModel());
		ImageDescriptor modelImageDescriptor= ImageDescriptor.createFromImage(modelImage);
		lp.dispose();
		return modelImageDescriptor;
	}

	void reset() {
		setSingleInput(getJavaModel());
	}

	private IResource getJavaModel() {
		//		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		return ResourcesPlugin.getWorkspace().getRoot();
		//		return null;
	}

	private void hookContextMenu() {
		MenuManager menuMgr= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this::fillContextMenu);
		Menu menu= menuMgr.createContextMenu(tableViewer.getControl());
		tableViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, tableViewer);
	}

	void fillContextMenu(IMenuManager manager) {
		//		addFocusActionOrNot(manager);
		manager.add(fResetAction);
		manager.add(fRefreshAction);
		manager.add(new Separator());

		if (!getSite().getSelectionProvider().getSelection().isEmpty()) {
			MenuManager showInSubMenu= new MenuManager(getShowInMenuLabel());
			IWorkbenchWindow workbenchWindow= getSite().getWorkbenchWindow();
			showInSubMenu.add(ContributionItemFactory.VIEWS_SHOW_IN.create(workbenchWindow));
			manager.add(showInSubMenu);
		}
		// addElementActionsOrNot(manager);
		manager.add(new Separator());

		// manager.add(fCopyAction);
		manager.add(new Separator());

		// fDrillDownAdapter.addNavigationActions(manager);
		manager.add(new Separator());
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		manager.add(new Separator());
		// addCompareActionOrNot(manager);
		manager.add(fPropertiesAction);
	}

	private String getShowInMenuLabel() {
		String keyBinding= null;

		IBindingService bindingService= PlatformUI.getWorkbench().getAdapter(IBindingService.class);
		if (bindingService != null) {
			keyBinding= bindingService
					.getBestActiveBindingFormattedFor(IWorkbenchCommandConstants.NAVIGATE_SHOW_IN_QUICK_MENU);
		}

		if (keyBinding == null) {
			keyBinding= ""; //$NON-NLS-1$
		}

		return "Sho&w In" + '\t' + keyBinding; //$NON-NLS-1$
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	@Override
	public void setFocus() {
		tableViewer.getControl().setFocus();
	}

	@Override
	public boolean show(ShowInContext context) {
		ISelection selection= context.getSelection();
		if (selection instanceof IStructuredSelection structuredSelection) {
			if (structuredSelection.size() >= 1) {
				List<Object> input= new ArrayList<>();
				for (Object item : structuredSelection) {
					if (item instanceof IJavaElement || item instanceof IResource
							|| item instanceof IJarEntryResource) {
						input.add(item);
					}
				}
				if (input.size() > 0) {
					setInput(input);
					return true;
				}
			}
		}

		Object input= context.getInput();
		if (input instanceof IEditorInput) {
			SourceType elementOfInput= (SourceType) getElementOfInput((IEditorInput) context.getInput());
			if (elementOfInput != null) {
				//				setSingleInput(elementOfInput);
				return true;
			}
		}

		return false;
	}

	void setSingleInput(IResource iResource) {
		setInput(Collections.singletonList(iResource));
	}

	Object getElementOfInput(IEditorInput input) {
		Object adapted= input.getAdapter(IClassFile.class);
		if (adapted != null) {
			return adapted;
		}

		if (input instanceof IFileEditorInput) {
			IFile file= ((IFileEditorInput) input).getFile();
			IJavaElement javaElement= JavaCore.create(file);
			if (javaElement != null) {
				return javaElement;
			}
			return file;
		}
		if (input instanceof IStorageEditorInput) {
			try {
				return ((IStorageEditorInput) input).getStorage();
			} catch (CoreException e) {
			}
		}
		return null;
	}

	@Override
	public ShowInContext getShowInContext() {
		IWorkbenchPartSite site= getSite();
		if (site == null) {
			return null;
		}
		ISelectionProvider selectionProvider= site.getSelectionProvider();
		if (selectionProvider == null) {
			return null;
		}
		return new ShowInContext(null, selectionProvider.getSelection());
	}

	void setInput(List<?> javaElementsOrResources) {
		//		fInput = new JERoot(javaElementsOrResources);
		//		tableViewer.setInput(fInput);
		tableViewer.setInput(javaElementsOrResources);
		JHViewContentProvider tcp= (JHViewContentProvider) tableViewer.getContentProvider();
		Object[] elements= tcp.getElements(javaElementsOrResources);
		if (elements.length > 0) {
			tableViewer.setSelection(new StructuredSelection(elements[0]));
			// if (elements.length == 1) {
			// tableViewer.setExpandedState(elements[0], true);
			// }
		}
		// fDrillDownAdapter.reset();
	}

	@Override
	public <T> T getAdapter(Class<T> adapter) {
		// if (adapter == IPropertySheetPage.class) {
		// return (T) getPropertySheetPage();
		// }
		return super.getAdapter(adapter);
	}

}
