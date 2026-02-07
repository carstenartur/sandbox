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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.e4.core.services.log.Logger;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJarEntryResource;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.internal.core.SourceType;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.refactoring.RenameSupport;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
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
import org.sandbox.jdt.ui.helper.views.colum.ConflictHighlightingLabelProvider;
import org.sandbox.jdt.ui.helper.views.colum.DeclaringMethodColumn;
import org.sandbox.jdt.ui.helper.views.colum.DeprecatedColumn;
import org.sandbox.jdt.ui.helper.views.colum.NameColumn;
import org.sandbox.jdt.ui.helper.views.colum.PackageColumn;
import org.sandbox.jdt.ui.helper.views.colum.QualifiednameColumn;

public class JavaHelperView extends ViewPart implements IShowInSource, IShowInTarget {

	Logger logger= PlatformUI.getWorkbench().getService(Logger.class);
	TableViewer variableTableViewer;
	private Table variableTable;
	private Action refreshAction;
	private Action propertiesAction;

	private Action linkWithSelectionAction;
	
	private Action filterConflictsAction;
	
	private Action renameVariableAction;
	
	/** When true, the view automatically updates when selections change in other views */
	private boolean linkWithSelectionEnabled = true;
	
	/** When true, only shows variables with naming conflicts (same name, different type) */
	private boolean filterConflictsEnabled = false;
	
	/** Filter for showing only naming conflicts */
	private NamingConflictFilter namingConflictFilter = new NamingConflictFilter();

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
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		hookConflictHighlighting();
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
		manager.add(filterConflictsAction);
		manager.add(new Separator());
		manager.add(refreshAction);
		// fDrillDownAdapter.addNavigationActions(manager);
	}

	private void fillLocalPullDown(IMenuManager manager) {
		manager.add(linkWithSelectionAction);
		manager.add(filterConflictsAction);
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
		
		// Toggle action for filtering naming conflicts
		filterConflictsAction = new Action("Filter Naming Conflicts", Action.AS_CHECK_BOX) { //$NON-NLS-1$
			@Override
			public void run() {
				filterConflictsEnabled = isChecked();
				applyConflictFilter();
			}
		};
		filterConflictsAction.setToolTipText("Filter Naming Conflicts - when enabled, only shows variables with the same name but different types"); //$NON-NLS-1$
		filterConflictsAction.setImageDescriptor(JHPluginImages.IMG_FILTER_CONFLICTS);
		filterConflictsAction.setChecked(filterConflictsEnabled);
		
		// Action to rename a variable with a suggested name based on type
		renameVariableAction = new Action("Rename Variable with Type Suffix...") { //$NON-NLS-1$
			@Override
			public void run() {
				renameSelectedVariable();
			}
		};
		renameVariableAction.setToolTipText("Rename the selected variable with a suggested name based on its type"); //$NON-NLS-1$
		
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
		
		refreshAction= new Action("Re&fresh", JHPluginImages.IMG_REFRESH) { //$NON-NLS-1$
			@Override
			public void run() {
				BusyIndicator.showWhile(getSite().getShell().getDisplay(), () -> variableTableViewer.refresh());
			}
		};
		refreshAction.setToolTipText("Refresh"); //$NON-NLS-1$
		refreshAction.setActionDefinitionId("org.eclipse.ui.file.refresh"); //$NON-NLS-1$
	}
	
	/**
	 * Applies or removes the naming conflict filter based on the filterConflictsEnabled flag.
	 * When applying, analyzes all elements first to identify conflicts.
	 */
	private void applyConflictFilter() {
		if (filterConflictsEnabled) {
			// Analyze current elements to find conflicts
			JHViewContentProvider contentProvider = (JHViewContentProvider) variableTableViewer.getContentProvider();
			Object[] elements = contentProvider.getElements(variableTableViewer.getInput());
			namingConflictFilter.analyzeElements(elements);
			
			// Add filter
			variableTableViewer.addFilter(namingConflictFilter);
		} else {
			// Remove filter
			variableTableViewer.removeFilter(namingConflictFilter);
		}
	}
	
	/**
	 * Renames the currently selected variable with a suggested name based on its type.
	 * Shows a dialog to allow the user to modify the suggested name before applying.
	 */
	private void renameSelectedVariable() {
		IStructuredSelection selection = variableTableViewer.getStructuredSelection();
		Object element = selection.getFirstElement();
		
		if (!(element instanceof IVariableBinding variableBinding)) {
			return;
		}
		
		IJavaElement javaElement = variableBinding.getJavaElement();
		if (javaElement == null) {
			return;
		}
		
		String suggestedName = VariableNameSuggester.suggestName(variableBinding);
		String currentName = variableBinding.getName();
		
		// Show input dialog with suggested name pre-filled
		InputDialog dialog = new InputDialog(
				getSite().getShell(),
				"Rename Variable", //$NON-NLS-1$
				"Enter new name for variable '" + currentName + "':", //$NON-NLS-1$ //$NON-NLS-2$
				suggestedName,
				newText -> {
					if (newText == null || newText.trim().isEmpty()) {
						return "Name cannot be empty"; //$NON-NLS-1$
					}
					if (!isValidJavaIdentifier(newText)) {
						return "Invalid Java identifier"; //$NON-NLS-1$
					}
					return null;
				});
		
		if (dialog.open() == Window.OK) {
			String newName = dialog.getValue();
			if (!newName.equals(currentName)) {
				performRename(javaElement, newName);
			}
		}
	}
	
	/**
	 * Checks if a string is a valid Java identifier.
	 * 
	 * @param name the name to check
	 * @return true if the name is a valid Java identifier
	 */
	private boolean isValidJavaIdentifier(String name) {
		if (name == null || name.isEmpty()) {
			return false;
		}
		if (!Character.isJavaIdentifierStart(name.charAt(0))) {
			return false;
		}
		for (int i = 1; i < name.length(); i++) {
			if (!Character.isJavaIdentifierPart(name.charAt(i))) {
				return false;
			}
		}
		return true;
	}
	
	/**
	 * Performs the rename refactoring using JDT's RenameSupport.
	 * Opens the refactoring wizard dialog with preview option.
	 * 
	 * @param javaElement the Java element to rename
	 * @param newName the new name
	 */
	private void performRename(IJavaElement javaElement, String newName) {
		try {
			RenameSupport renameSupport = null;
			
			if (javaElement instanceof IField field) {
				renameSupport = RenameSupport.create(field, newName, 
						RenameSupport.UPDATE_REFERENCES | RenameSupport.UPDATE_GETTER_METHOD | RenameSupport.UPDATE_SETTER_METHOD);
			} else if (javaElement instanceof ILocalVariable localVariable) {
				renameSupport = RenameSupport.create(localVariable, newName, RenameSupport.UPDATE_REFERENCES);
			}
			
			if (renameSupport != null) {
				// Check if refactoring is valid
				if (renameSupport.preCheck().isOK()) {
					// Open the refactoring wizard dialog with preview
					renameSupport.openDialog(getSite().getShell(), true);
				}
			}
		} catch (CoreException e) {
			logger.error(e, "Error performing rename refactoring"); //$NON-NLS-1$
		}
	}

	/* see JavaModelUtil.reconcile((ICompilationUnit) input) */
	static void reconcile(ICompilationUnit unit) throws JavaModelException {
		synchronized (unit) {
			unit.reconcile(ICompilationUnit.NO_AST, false /* don't force problem detection */,
					null /* use primary owner */, null /* no progress monitor */);
		}
	}

	private void hookContextMenu() {
		MenuManager menuMgr= new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(this::fillContextMenu);
		Menu menu= menuMgr.createContextMenu(variableTable);
		variableTable.setMenu(menu);
		getSite().registerContextMenu(menuMgr, variableTableViewer);
		
		// Add mouse listener to select the row under cursor on right-click
		variableTable.addListener(SWT.MenuDetect, event -> {
			org.eclipse.swt.graphics.Point pt = variableTable.getDisplay().map(null, variableTable, event.x, event.y);
			org.eclipse.swt.widgets.TableItem item = variableTable.getItem(pt);
			if (item != null) {
				Object data = item.getData();
				if (data != null) {
					variableTableViewer.setSelection(new StructuredSelection(data), true);
				}
			}
		});
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
	 * Adds custom painting for conflict highlighting using SWT's EraseItem event.
	 * This paints a light red background for rows where the variable name has type conflicts.
	 */
	private void hookConflictHighlighting() {
		final org.eclipse.swt.graphics.Color conflictColor = new org.eclipse.swt.graphics.Color(
				variableTable.getDisplay(), 255, 200, 200);
		
		variableTable.addListener(SWT.EraseItem, event -> {
			// Only handle background painting
			if ((event.detail & SWT.BACKGROUND) == 0) {
				return;
			}
			
			org.eclipse.swt.widgets.TableItem item = (org.eclipse.swt.widgets.TableItem) event.item;
			Object data = item.getData();
			
			if (data instanceof IVariableBinding variableBinding) {
				Set<String> conflicts = ConflictHighlightingLabelProvider.getConflictingNames();
				if (conflicts != null && conflicts.contains(variableBinding.getName())) {
					// Paint conflict background
					org.eclipse.swt.graphics.GC gc = event.gc;
					org.eclipse.swt.graphics.Color oldBackground = gc.getBackground();
					gc.setBackground(conflictColor);
					gc.fillRectangle(event.x, event.y, event.width, event.height);
					gc.setBackground(oldBackground);
					// Mark that we handled the background
					event.detail &= ~SWT.BACKGROUND;
				}
			}
		});
		
		// Dispose color when table is disposed
		variableTable.addDisposeListener(e -> conflictColor.dispose());
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
		manager.add(refreshAction);
		manager.add(new Separator());

		// Add rename action when a variable is selected
		IStructuredSelection selection = variableTableViewer.getStructuredSelection();
		if (!selection.isEmpty()) {
			Object selectedElement = selection.getFirstElement();
			if (selectedElement instanceof IVariableBinding variableBinding) {
				IJavaElement javaElement = variableBinding.getJavaElement();
				// Enable rename only for fields and local variables
				boolean canRename = javaElement instanceof IField || javaElement instanceof ILocalVariable;
				renameVariableAction.setEnabled(canRename);
				manager.add(renameVariableAction);
				manager.add(new Separator());
			}
			
			MenuManager showInSubMenu= new MenuManager(getShowInMenuLabel());
			IWorkbenchWindow workbenchWindow= getSite().getWorkbenchWindow();
			showInSubMenu.add(ContributionItemFactory.VIEWS_SHOW_IN.create(workbenchWindow));
			manager.add(showInSubMenu);
		}
		manager.add(new Separator());
		// Other plug-ins can contribute there actions here
		manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
		manager.add(new Separator());
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
		
		// Analyze elements for naming conflicts and update the highlighting
		namingConflictFilter.analyzeElements(elements);
		ConflictHighlightingLabelProvider.setConflictingNames(namingConflictFilter.getConflictingNames());
		
		// Refresh the table to apply the conflict highlighting
		variableTableViewer.refresh();
		
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