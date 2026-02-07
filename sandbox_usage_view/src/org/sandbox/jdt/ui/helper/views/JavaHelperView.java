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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICodeAssist;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.ui.JavaUI;
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
import org.eclipse.jface.layout.TableColumnLayout;
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
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IStorageEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IWorkbenchPartReference;
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
import org.eclipse.ui.progress.IProgressService;
import org.sandbox.jdt.ui.helper.views.colum.AbstractColumn;
import org.sandbox.jdt.ui.helper.views.colum.DeclaringMethodColumn;
import org.sandbox.jdt.ui.helper.views.colum.DeprecatedColumn;
import org.sandbox.jdt.ui.helper.views.colum.NameColumn;
import org.sandbox.jdt.ui.helper.views.colum.PackageColumn;
import org.sandbox.jdt.ui.helper.views.colum.QualifiednameColumn;

public class JavaHelperView extends ViewPart implements IShowInSource, IShowInTarget {

	Logger logger= PlatformUI.getWorkbench().getService(Logger.class);
	TableViewer variableTableViewer;
	private Table variableTable;
	//	private JERoot fInput;
	private Action refreshAction;
	private Action resetAction;
	private Action setInputFromEditorLocationAction;
	private Action propertiesAction;
	//	private Action fFocusAction;

	private Action setInputFromEditorSelectionAction;
	
	private Action linkWithSelectionAction;
	
	/** When true, the view automatically updates when selections change in other views */
	private boolean linkWithSelectionEnabled = true;

	private IPartListener2 editorPartListener;

	private ISelectionListener workbenchSelectionListener;

	private IJavaElement currentJavaElementInput = null;

	@Override
	public void createPartControl(Composite parent) {
		GridLayout layout = new GridLayout(1, false);
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		layout.horizontalSpacing = 0;
		layout.verticalSpacing = 0;
		parent.setLayout(layout);

		// Create a composite to hold the table with TableColumnLayout
		Composite tableComposite = new Composite(parent, SWT.NONE);
		tableComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true));
		TableColumnLayout tableColumnLayout = new TableColumnLayout();
		tableComposite.setLayout(tableColumnLayout);

		variableTableViewer= new TableViewer(tableComposite, SWT.BORDER | SWT.FULL_SELECTION | SWT.H_SCROLL | SWT.V_SCROLL);
		variableTableViewer.setColumnProperties(new String[] {});
		variableTableViewer.setUseHashlookup(true);
		variableTable= variableTableViewer.getTable();
		variableTable.setHeaderVisible(true);
		variableTable.setHeaderBackground(parent.getDisplay().getSystemColor(SWT.COLOR_TITLE_BACKGROUND));
		variableTable.setLinesVisible(true);
		variableTableViewer.setContentProvider(new JHViewContentProvider());
		// This will create the columns for the table with proper weights
		AbstractColumn.addColumn(variableTableViewer, new NameColumn(), tableColumnLayout);
		AbstractColumn.addColumn(variableTableViewer, new QualifiednameColumn(), tableColumnLayout);
		AbstractColumn.addColumn(variableTableViewer, new PackageColumn(), tableColumnLayout);
		AbstractColumn.addColumn(variableTableViewer, new DeprecatedColumn(), tableColumnLayout);
		AbstractColumn.addColumn(variableTableViewer, new DeclaringMethodColumn(), tableColumnLayout);

		variableTableViewer.setComparator(AbstractColumn.getComparator());
		reset();
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		getSite().setSelectionProvider(new JHViewSelectionProvider(variableTableViewer));
		contributeToActionBars();
		// variableTableViewer.addSelectionChangedListener(event -> fCopyAction.setEnabled(!
		// event.getSelection().isEmpty()));
		// variableTableViewer.addSelectionChangedListener(event -> fCopyAction.setEnabled(!
		// event.getSelection().isEmpty()));
		
		// Add part listener to track editor changes
		addEditorPartListener();
		// Add selection listener to track Package Explorer selections
		addWorkbenchSelectionListener();
	}

	private void contributeToActionBars() {
		IActionBars bars= getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
		bars.setGlobalActionHandler(ActionFactory.REFRESH.getId(), refreshAction);
		// bars.setGlobalActionHandler(ActionFactory.COPY.getId(), fCopyAction);
		bars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(), propertiesAction);
	}

	private void fillLocalToolBar(IToolBarManager manager) {
		manager.add(linkWithSelectionAction);
		manager.add(new Separator());
		manager.add(setInputFromEditorSelectionAction);
		manager.add(setInputFromEditorLocationAction);
		manager.add(resetAction);
		manager.add(refreshAction);
		manager.add(new Separator());
		// fDrillDownAdapter.addNavigationActions(manager);
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(linkWithSelectionAction);
		manager.add(new Separator());
		manager.add(setInputFromEditorSelectionAction);
		manager.add(setInputFromEditorLocationAction);
		// manager.add(fCreateFromHandleAction);
		manager.add(resetAction);
		// manager.add(fLogDeltasAction);
		manager.add(new Separator());
		manager.add(refreshAction);
	}

	private void makeActions() {
		// Toggle action for linking with selection
		linkWithSelectionAction = new Action("Link with Selection", Action.AS_CHECK_BOX) { //$NON-NLS-1$
			@Override
			public void run() {
				linkWithSelectionEnabled = isChecked();
			}
		};
		linkWithSelectionAction.setToolTipText("Link with Selection - when enabled, the view automatically updates based on the current selection"); //$NON-NLS-1$
		linkWithSelectionAction.setImageDescriptor(JHPluginImages.IMG_SET_FOCUS);
		linkWithSelectionAction.setChecked(linkWithSelectionEnabled);
		
		setInputFromEditorSelectionAction= new Action("Set Input from Editor (&codeSelect)", JHPluginImages.IMG_SET_FOCUS_CODE_SELECT) { //$NON-NLS-1$
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
		setInputFromEditorSelectionAction.setToolTipText("Set input from current editor's selection (codeSelect)"); //$NON-NLS-1$
		//		fFocusAction = new Action() {
		//			@Override
		//			public void run() {
		//				Object selected = ((IStructuredSelection) variableTableViewer.getSelection()).getFirstElement();
		//				if (selected instanceof JavaElement) {
		//					setSingleInput((IJavaModel) ((JavaElement) selected).getJavaElement());
		//				} else if (selected instanceof JEResource) {
		//					setSingleInput((IJavaModel) ((JEResource) selected).getResource());
		//				}
		//			}
		//		};
		//		fFocusAction.setToolTipText("Focus on Selection");
		propertiesAction= new Action("&Properties", JHPluginImages.IMG_PROPERTIES) { //$NON-NLS-1$
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
		propertiesAction.setActionDefinitionId(IWorkbenchCommandConstants.FILE_PROPERTIES);
		resetAction= new Action("&Reset View", getJavaModelImageDescriptor()) { //$NON-NLS-1$
			@Override
			public void run() {
				reset();
			}
		};
		resetAction.setToolTipText("Reset View to JavaModel"); //$NON-NLS-1$
		refreshAction= new Action("Re&fresh", JHPluginImages.IMG_REFRESH) { //$NON-NLS-1$
			@Override
			public void run() {
				BusyIndicator.showWhile(getSite().getShell().getDisplay(), () -> variableTableViewer.refresh());
			}
		};
		refreshAction.setToolTipText("Refresh"); //$NON-NLS-1$
		refreshAction.setActionDefinitionId("org.eclipse.ui.file.refresh"); //$NON-NLS-1$
		setInputFromEditorLocationAction= new Action("Set Input from Editor location (&getElementAt)", JHPluginImages.IMG_SET_FOCUS) { //$NON-NLS-1$
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
		setInputFromEditorLocationAction.setToolTipText("Set input from current editor's selection location (getElementAt)"); //$NON-NLS-1$
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
		Menu menu= menuMgr.createContextMenu(variableTableViewer.getControl());
		variableTableViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, variableTableViewer);
	}

	/**
	 * Adds a double-click listener to the table viewer that navigates to the
	 * declaration of the selected variable binding.
	 */
	private void hookDoubleClickAction() {
		variableTableViewer.addDoubleClickListener(event -> {
			IStructuredSelection selection = (IStructuredSelection) event.getSelection();
			Object element = selection.getFirstElement();
			if (element instanceof IVariableBinding variableBinding) {
				openVariableDeclaration(variableBinding);
			}
		});
	}

	/**
	 * Opens the editor and navigates to the declaration of the given variable binding.
	 * Automatically disables linking with selection to prevent the table from refreshing
	 * when the editor is opened.
	 * 
	 * @param variableBinding the variable binding whose declaration to open
	 */
	private void openVariableDeclaration(IVariableBinding variableBinding) {
		IJavaElement javaElement = variableBinding.getJavaElement();
		if (javaElement != null) {
			// Disable linking before opening the editor to prevent table refresh
			disableLinkingWithSelection();
			try {
				JavaUI.openInEditor(javaElement, true, true);
			} catch (PartInitException | JavaModelException e) {
				logger.error(e, "Could not open editor for variable: " + variableBinding.getName()); //$NON-NLS-1$
			}
		}
	}
	
	/**
	 * Disables linking with selection and updates the toggle button state.
	 * This is called when navigating from the table to prevent the view from
	 * automatically refreshing and losing the current content.
	 */
	private void disableLinkingWithSelection() {
		linkWithSelectionEnabled = false;
		if (linkWithSelectionAction != null) {
			linkWithSelectionAction.setChecked(false);
		}
	}

	void fillContextMenu(IMenuManager manager) {
		//		addFocusActionOrNot(manager);
		manager.add(resetAction);
		manager.add(refreshAction);
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
		manager.add(propertiesAction);
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
		variableTableViewer.getControl().setFocus();
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
				if (!input.isEmpty()) {
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

	void setSingleInput(IJavaElement javaElement) {
		setInput(Collections.singletonList(javaElement));
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
		//		variableTableViewer.setInput(fInput);
		variableTableViewer.setInput(javaElementsOrResources);
		
		// Clear the editor input cache if the input is not a single IJavaElement
		// This ensures the view will refresh when switching back to an editor after
		// actions like reset() or setInputFromEditorLocationAction that set IResource inputs
		if (javaElementsOrResources != null && javaElementsOrResources.size() == 1) {
			Object input = javaElementsOrResources.get(0);
			if (!(input instanceof IJavaElement)) {
				currentJavaElementInput = null;
			}
		} else {
			currentJavaElementInput = null;
		}
		
		JHViewContentProvider contentProvider= (JHViewContentProvider) variableTableViewer.getContentProvider();
		Object[] elements= contentProvider.getElements(javaElementsOrResources);
		if (elements.length > 0) {
			variableTableViewer.setSelection(new StructuredSelection(elements[0]));
			// if (elements.length == 1) {
			// variableTableViewer.setExpandedState(elements[0], true);
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

	/**
	 * Adds a part listener to track when editors are activated and automatically
	 * refresh the view with the active editor's content.
	 */
	private void addEditorPartListener() {
		// Check if listener is already registered to prevent duplicates
		if (editorPartListener != null) {
			return;
		}
		
		editorPartListener = new IPartListener2() {
			@Override
			public void partActivated(IWorkbenchPartReference partRef) {
				if (linkWithSelectionEnabled && partRef.getPart(false) instanceof IEditorPart) {
					// Update the view when an editor is activated
					// Ensure UI updates happen on the UI thread
					getSite().getShell().getDisplay().asyncExec(() -> updateViewFromActiveEditor());
				}
			}

			@Override
			public void partBroughtToTop(IWorkbenchPartReference partRef) {
				// Not needed
			}

			@Override
			public void partClosed(IWorkbenchPartReference partRef) {
				// Not needed
			}

			@Override
			public void partDeactivated(IWorkbenchPartReference partRef) {
				// Not needed
			}

			@Override
			public void partOpened(IWorkbenchPartReference partRef) {
				if (linkWithSelectionEnabled && partRef.getPart(false) instanceof IEditorPart) {
					// Update the view when an editor is opened
					// Ensure UI updates happen on the UI thread
					getSite().getShell().getDisplay().asyncExec(() -> updateViewFromActiveEditor());
				}
			}

			@Override
			public void partHidden(IWorkbenchPartReference partRef) {
				// Not needed
			}

			@Override
			public void partVisible(IWorkbenchPartReference partRef) {
				// Not needed
			}

			@Override
			public void partInputChanged(IWorkbenchPartReference partRef) {
				if (linkWithSelectionEnabled && partRef.getPart(false) instanceof IEditorPart) {
					// Update the view when editor input changes
					// Ensure UI updates happen on the UI thread
					getSite().getShell().getDisplay().asyncExec(() -> updateViewFromActiveEditor());
				}
			}
		};

		getSite().getPage().addPartListener(editorPartListener);
	}

	/**
	 * Updates the view content based on the currently active editor.
	 */
	private void updateViewFromActiveEditor() {
		IEditorPart editor = getSite().getPage().getActiveEditor();
		if (editor == null) {
			return;
		}
		
		IEditorInput input = editor.getEditorInput();
		if (input == null) {
			return;
		}

		IJavaElement javaElement = input.getAdapter(IJavaElement.class);
		if (javaElement != null && !javaElement.equals(currentJavaElementInput)) {
			currentJavaElementInput = javaElement;
			setSingleInput(javaElement);
		}
	}

	/**
	 * Adds a selection listener to track selections in views like Package Explorer.
	 * When a package, source folder, or project is selected, the view will update
	 * to show variables from all contained compilation units.
	 */
	private void addWorkbenchSelectionListener() {
		if (workbenchSelectionListener != null) {
			return;
		}

		workbenchSelectionListener = (part, selection) -> {
			// Skip if link with selection is disabled
			if (!linkWithSelectionEnabled) {
				return;
			}
			
			// Ignore selections from this view itself
			if (part == JavaHelperView.this) {
				return;
			}

			if (selection instanceof IStructuredSelection structuredSelection) {
				Object firstElement = structuredSelection.getFirstElement();
				if (firstElement instanceof IPackageFragment 
						|| firstElement instanceof IPackageFragmentRoot
						|| firstElement instanceof IJavaProject) {
					IJavaElement javaElement = (IJavaElement) firstElement;
					if (!javaElement.equals(currentJavaElementInput)) {
						currentJavaElementInput = javaElement;
						setInputWithProgress(javaElement);
					}
				} else if (firstElement instanceof ICompilationUnit compilationUnit) {
					if (!compilationUnit.equals(currentJavaElementInput)) {
						currentJavaElementInput = compilationUnit;
						setSingleInput(compilationUnit);
					}
				}
			}
		};

		getSite().getPage().addSelectionListener(workbenchSelectionListener);
	}

	/**
	 * Sets the input with a progress dialog for potentially long-running operations.
	 * This is used when processing packages, source folders, or projects that may
	 * contain many compilation units.
	 * 
	 * @param javaElement the Java element to process (package, source folder, or project)
	 */
	private void setInputWithProgress(IJavaElement javaElement) {
		IProgressService progressService = PlatformUI.getWorkbench().getProgressService();
		
		try {
			progressService.busyCursorWhile(monitor -> {
				monitor.beginTask("Processing " + javaElement.getElementName() + "...", IProgressMonitor.UNKNOWN); //$NON-NLS-1$ //$NON-NLS-2$
				
				try {
					int totalUnits = countCompilationUnits(javaElement);
					if (totalUnits > 0) {
						monitor.beginTask("Processing " + totalUnits + " compilation units...", totalUnits); //$NON-NLS-1$ //$NON-NLS-2$
					}
					
					// Create a progress-aware content provider
					VariableBindingContentProviderWithProgress contentProvider = new VariableBindingContentProviderWithProgress(monitor);
					final Object[] elements = contentProvider.getElements(Collections.singletonList(javaElement));
					
					// Update UI on the display thread
					getSite().getShell().getDisplay().asyncExec(() -> {
						variableTableViewer.setInput(Collections.singletonList(javaElement));
						// Manually set the elements since we already computed them
						variableTableViewer.refresh();
						if (elements.length > 0) {
							variableTableViewer.setSelection(new StructuredSelection(elements[0]));
						}
					});
				} finally {
					monitor.done();
				}
			});
		} catch (InvocationTargetException | InterruptedException e) {
			logger.error(e, "Error processing Java element: " + javaElement.getElementName()); //$NON-NLS-1$
		}
	}

	/**
	 * Counts the number of compilation units in a Java element.
	 * 
	 * @param javaElement the Java element to count compilation units for
	 * @return the number of compilation units
	 */
	private int countCompilationUnits(IJavaElement javaElement) {
		try {
			if (javaElement instanceof ICompilationUnit) {
				return 1;
			} else if (javaElement instanceof IPackageFragment pf) {
				return pf.getCompilationUnits().length;
			} else if (javaElement instanceof IPackageFragmentRoot pfr) {
				int count = 0;
				for (IJavaElement child : pfr.getChildren()) {
					if (child instanceof IPackageFragment pf) {
						count += pf.getCompilationUnits().length;
					}
				}
				return count;
			} else if (javaElement instanceof IJavaProject project) {
				int count = 0;
				for (IPackageFragmentRoot root : project.getPackageFragmentRoots()) {
					if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
						for (IJavaElement child : root.getChildren()) {
							if (child instanceof IPackageFragment pf) {
								count += pf.getCompilationUnits().length;
							}
						}
					}
				}
				return count;
			}
		} catch (JavaModelException e) {
			// Ignore and return 0
		}
		return 0;
	}

	@Override
	public void dispose() {
		// Remove workbench selection listener when view is disposed
		if (workbenchSelectionListener != null) {
			try {
				IWorkbenchPage page = getSite().getPage();
				if (page != null) {
					page.removeSelectionListener(workbenchSelectionListener);
				}
			} catch (Exception e) {
				// Ignore errors during shutdown
			}
			workbenchSelectionListener = null;
		}
		// Remove editor part listener when view is disposed
		if (editorPartListener != null) {
			// Check if page is still available to prevent errors during workbench shutdown
			try {
				IWorkbenchPage page = getSite().getPage();
				if (page != null) {
					page.removePartListener(editorPartListener);
				}
			} catch (Exception e) {
				// Ignore errors during shutdown
			}
			editorPartListener = null;
		}
		super.dispose();
	}

}