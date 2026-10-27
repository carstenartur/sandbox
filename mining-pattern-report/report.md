# Refactoring Mining Report — 2026-10-27

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 3 | 1 |
| eclipse.jdt.ui | 1479 | 83 | 8 |
| eclipse.platform.ui | 1140 | 113 | 8 |
| eclipse.platform | 311 | 71 | 4 |
| eclipse.platform.text | 0 | 0 | 0 |
| eclipse.platform.debug | 0 | 0 | 0 |
| sandbox | 1761 | 173 | 12 |

## Details
### eclipse.jdt.core
#### Rule: `string-equals` → `string-equals.null-or-empty.review`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:91` — `annos == null || annos.isEmpty()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:270` — `nameString == null || nameString.isEmpty()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteAnalyzer.java:280` — `originalImports == null || originalImports.isEmpty()`

### eclipse.jdt.ui
#### Rule: `deprecations` → `deprecations.runtime-exec-string.consider-processbuilder`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javadocexport/JavadocWizard.java:315` — `Runtime.getRuntime().exec(args)`

#### Rule: `try-with-resources` → `Consider using try-with-resources for AutoCloseable resource`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/breadcrumb/BreadcrumbItemDropDown.java:574` — `shell.close()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/breadcrumb/BreadcrumbItemDropDown.java:585` — `shell.close()`

#### Rule: `stream-performance` → `stream-performance.sorted-before-collect.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesAdapter.java:861` — `unavailableSystemModules.stream().map(IJavaElement::getElementName).sorted()....`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesPage.java:639` — `allModulesToRemove.stream().filter(m -> !seedModules.contains(m)).sorted().co...`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/AddToClasspathAction.java:136` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/wizards/NewJavaProjectWizardPage.java:168` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetModel.java:719` — `System.arraycopy(workingSets,0,activeWorkingSets,0,workingSets.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:800` — `System.arraycopy(elements,0,newElements,0,elements.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/actions/ConfigureContainerAction.java:90` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/actions/CompositeActionGroup.java:46` — `System.arraycopy(fGroups,0,newGroups,0,fGroups.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/ClasspathContainerPreferencePage.java:104` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/ComplianceConfigurationBlock.java:222` — `System.arraycopy(keys,0,allKeys,0,keys.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/CompoundEditExitStrategy.java:164` — `System.arraycopy(commandIds,0,fCommandIds,0,commandIds.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/JavaOutlinePage.java:685` — `System.arraycopy(sortedObjects,0,sortedRegions,0,sortedObjects.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/EditFilterWizard.java:30` — `System.arraycopy(inc,0,fOrginalInclusion,0,inc.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/EditFilterWizard.java:34` — `System.arraycopy(excl,0,fOriginalExclusion,0,excl.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/BuildPathBasePage.java:253` — `System.arraycopy(exclusionFilters,0,newExclusionFilters,0,exclusionFilters.le...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListElement.java:398` — `System.arraycopy(filters,0,newFilters,0,filters.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/AbstractNewFolderWizardPage.java:483` — `System.arraycopy(exclusionFilters,0,newExclusionFilters,0,exclusionFilters.le...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/util/RowLayouter.java:143` — `System.arraycopy(fDefaultGridDatas,0,newDatas,0,fDefaultGridDatas.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/CombinedWordRule.java:144` — `System.arraycopy(old,0,fContent,0,old.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuesser.java:318` — `System.arraycopy(v.triggerChars,0,triggers,0,v.triggerChars.length)`

#### Rule: `arrays` → `arrays.clone.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/CustomFiltersActionGroup.java:557` — `patterns.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UnboxingCleanUp.java:190` — `binding.getParameterTypes().clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javadocexport/ContributedJavadocWizardPage.java:130` — `sourceElements.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:134` — `fields.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:137` — `inheritedFields.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:140` — `methods.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:143` — `inheritedMethods.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/AnnotationExpansionControl.java:789` — `range.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/AnnotationExpansionControl.java:827` — `styleRange.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/JavaSourceHover.java:200` — `sourceLines.clone()`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetModel.java:653` — `Arrays.asList(getAllWorkingSets()).contains(workingSet)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:774` — `Arrays.asList(VALID_WORKING_SET_IDS).contains(set.getId())`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:788` — `Arrays.asList(set.getElements()).contains(adaptedElement)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/HierarchyLabelProvider.java:107` — `Arrays.asList(elements).contains(element)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/LibrariesWorkbookPage.java:373` — `Arrays.asList(segments).contains(JavaRuntime.JRE_CONTAINER)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/util/SelectionUtil.java:85` — `Arrays.asList(resources).contains(null)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultPhoneticHashProvider.java:167` — `Arrays.asList(candidates).contains(token)`

#### Rule: `string-equals` → `string-equals.null-or-empty.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/FindReferencesInHierarchyAction.java:103` — `sel == null || sel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/FindStringsToExternalizeAction.java:175` — `elements == null || elements.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/WorkingSetFindAction.java:108` — `sel == null || sel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/FindAction.java:112` — `sel == null || sel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/FindDeclarationsInHierarchyAction.java:121` — `sel == null || sel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/ExternalizeStringsAction.java:218` — `elements == null || elements.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/OverrideIndicatorLabelDecorator.java:373` — `fListeners == null || fListeners.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/java/correction/CUCorrectionProposal.java:345` — `super.getName() == null || super.getName().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/java/correction/CUCorrectionProposal.java:353` — `super.getCommandId() == null || super.getCommandId().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/HashCleanUp.java:375` — `innerMethod.parameters() == null || innerMethod.parameters().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/HashCleanUp.java:487` — `hashOnField.arguments() == null || hashOnField.arguments().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/MapCloningCleanUp.java:243` — `classInstanceCreation.arguments() == null || classInstanceCreation.arguments(...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/CleanUpRefactoringWizard.java:492` — `res == null || res.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/JoinCleanUp.java:656` — `builderInstantiation.arguments() == null || builderInstantiation.arguments()....`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/JoinCleanUp.java:657` — `builderInstantiation.typeArguments() == null || builderInstantiation.typeArgu...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/StringBuilderCleanUp.java:791` — `originalClassInstanceCreation.typeArguments() == null || originalClassInstanc...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/formatter/ProfileConfigurationBlock.java:275` — `profiles == null || profiles.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarFileExportOperation.java:779` — `classFileList == null || classFileList.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/TypeHierarchyViewPart.java:1415` — `currSelection == null || currSelection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/TypeHierarchyViewPart.java:1653` — `inputList == null || inputList.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/packageview/FileTransferDragAdapter.java:109` — `elements == null || elements.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/packageview/FileTransferDragAdapter.java:136` — `elements == null || elements.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/propertiesfileeditor/PropertiesQuickAssistProcessor.java:170` — `keys == null || keys.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/propertiesfileeditor/PropertiesQuickAssistProcessor.java:226` — `keys == null || keys.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/propertiesfileeditor/OpenAction.java:62` — `selection == null || selection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/search/JavaSearchQuery.java:75` — `dataList == null || dataList.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/ClassFileEditor.java:341` — `path == null || path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/LibrariesWorkbookPage.java:255` — `ssel == null || ssel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/newsourcepage/DialogPackageExplorer.java:448` — `elements == null || elements.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleSelectionDialog.java:325` — `selection == null || selection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ProjectsWorkbookPage.java:185` — `ssel == null || ssel.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListLabelProvider.java:467` — `path == null || path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListLabelProvider.java:477` — `path == null || path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListLabelProvider.java:488` — `path == null || path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/AbstractNewFolderWizardPage.java:182` — `selection == null || selection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/navigator/JavaFileLinkHelper.java:39` — `selection == null || selection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/correction/proposals/AddStaticFavoriteProposal.java:51` — `favorites == null || favorites.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/browsing/JavaBrowsingPart.java:922` — `selection == null || selection.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/browsing/JavaBrowsingPart.java:925` — `selection == null || selection.isEmpty()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaImplementationCodeMining.java:195` — `Stream.of(results).filter(t -> t.getAncestor(IJavaElement.COMPILATION_UNIT) !...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaImplementationCodeMining.java:211` — `Stream.of(results).filter(t -> t.getAncestor(IJavaElement.COMPILATION_UNIT) !...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleSelectionDialog.java:345` — `fSelectedModules.stream().filter(m -> !fAllIncluded.contains(m)).map(fModules...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesPage.java:762` — `Arrays.stream(fAllSystemRoots).map(pfr -> pfr.getModuleDescription().getEleme...`

### eclipse.platform.ui
#### Rule: `deprecations` → `deprecations.runtime-exec-string.consider-processbuilder`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/ExternalEditor.java:126` — `Runtime.getRuntime().exec(new String[]{"open","-a",programFileName,path})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/ExternalEditor.java:128` — `Runtime.getRuntime().exec(new String[]{programFileName,path})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:70` — `Runtime.getRuntime().exec(new String[]{"/usr/bin/open",localHref})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:85` — `Runtime.getRuntime().exec(new String[]{webBrowser,"-remote","openURL(" + enco...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:137` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:146` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:155` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`

#### Rule: `stream-performance` → `stream-performance.collection-stream-foreach.to-collection-foreach`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbookEditorsHandler.java:165` — `groupedEditorReferences.getValue().stream().forEach(editorReference -> editor...` → `groupedEditorReferences.getValue().forEach((editorReference -> editorReferenc...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbookEditorsHandler.java:198` — `refsToMakeDistinguishableViaPathSegments.stream().forEach(e -> editorReferenc...` → `refsToMakeDistinguishableViaPathSegments.forEach((e -> editorReferenceLabelTe...`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/ActivityCategoryPreferencePage.java:401` — `System.arraycopy(enabledCategories,0,allChecked,0,enabledCategories.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/EditorSelectionDialog.java:511` — `System.arraycopy(externalEditors,0,newEditors,0,externalEditors.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/WorkingSetConfigurationBlock.java:78` — `System.arraycopy(workingSetIds,0,workingSetIdsCopy,0,workingSetIds.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/FilteredList.java:255` — `System.arraycopy(elements,0,fElements,0,elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:231` — `System.arraycopy(selectedElements,0,adaptables,0,selectedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/LightweightDecoratorManager.java:165` — `System.arraycopy(oldDefs,0,lightweightDefinitions,0,oldDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/DecoratorManager.java:276` — `System.arraycopy(oldDefs,0,fullDefinitions,0,oldDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/DecoratorManager.java:639` — `System.arraycopy(fullDefinitions,0,returnValue,0,fullDefinitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/keys/KeysPreferencePage.java:191` — `System.arraycopy(sortOrder,0,newSortOrder,0,sortOrder.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:288` — `System.arraycopy(editorArray,0,newArray,0,editorArray.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ColorsAndFontsPreferencePage.java:1487` — `System.arraycopy(definitions,0,definitionsCopy,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ColorsAndFontsPreferencePage.java:1532` — `System.arraycopy(definitions,0,definitionsCopy,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:65` — `System.arraycopy(definitions,0,copyOfDefinitions,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:177` — `System.arraycopy(definitions,0,copyOfDefinitions,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:239` — `System.arraycopy(allDefs,0,copy,0,allDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/DecoratorsPreferencePage.java:147` — `System.arraycopy(elements,0,results,0,elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/SimpleWorkingSetSelectionDialog.java:156` — `System.arraycopy(checked,0,workingSets,0,checked.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/WorkingSetSelectionDialog.java:313` — `System.arraycopy(untypedResult,0,typedResult,0,untypedResult.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/NewWizardNewPage.java:398` — `System.arraycopy(currentExpanded,0,expanded,0,currentExpanded.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/CommandImageManagerEvent.java:95` — `System.arraycopy(changedCommandIds,0,copy,0,changedCommandIds.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/wizards/preferences/WizardPreferencesPage.java:590` — `System.arraycopy(checkedElements,0,transferElements,0,checkedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/wizards/AbstractExtensionWizardRegistry.java:69` — `System.arraycopy(localPrimaryWizards,0,newPrimary,0,localPrimaryWizards.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressInfoItem.java:588` — `System.arraycopy(children,0,infos,0,children.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/DetailedProgressViewer.java:563` — `System.arraycopy(children,0,progressInfoItems,0,children.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/about/AboutFeaturesPage.java:165` — `System.arraycopy(clientArray,0,bundleGroupInfos,0,clientArray.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AbstractWorkingSetManager.java:868` — `System.arraycopy(elements,0,newElements,0,elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AggregateWorkingSet.java:49` — `System.arraycopy(components,0,componentCopy,0,components.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AggregateWorkingSet.java:199` — `System.arraycopy(localComponents,0,copiedArray,0,localComponents.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:2318` — `System.arraycopy(parts,0,editors,0,parts.length)`

#### Rule: `arrays` → `arrays.clone.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/ws/WorkbenchActivitySupport.java:397` — `mutableActivityManager.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:1166` — `defaultMap.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:98` — `((ArrayList<IEditorDescriptor>)editors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:99` — `((ArrayList<IEditorDescriptor>)deletedEditors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:100` — `((ArrayList<IEditorDescriptor>)declaredDefaultEditors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/FileEditorsPreferencePage.java:382` — `mapping.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressManagerUtil.java:59` — `elements.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/part/PageBookView.java:471` — `((HashMap<IWorkbenchPart,PageRec>)mapPartToRec).clone()`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/LargeFileLimitsPreferenceHandler.java:417` — `Arrays.asList(disabled).contains(fileExtension)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:3805` — `Arrays.asList(models).contains(model)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:3815` — `Arrays.asList(models).contains(model)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/e4/migration/MementoReader.java:56` — `Arrays.asList(memento.getAttributeKeys()).contains(attribute)`

#### Rule: `string-equals` → `string-equals.null-or-empty.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/actions/LabelRetargetAction.java:105` — `handlerText == null || handlerText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/XMLMemento.java:133` — `problemText == null || problemText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/PatternFilter.java:167` — `patternString == null || patternString.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/StyledStringHighlighter.java:42` — `text == null || text.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/StyledStringHighlighter.java:47` — `pattern == null || pattern.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/MutableActivityManager.java:284` — `name == null || name.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/MutableActivityManager.java:298` — `name == null || name.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CloseEditorHandler.java:87` — `partTags == null || partTags.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/DecoratorManager.java:953` — `originalText == null || originalText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/PluginActionSetBuilder.java:401` — `toolBarId == null || toolBarId.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/handlers/LegacyHandlerService.java:581` — `commandId == null || commandId.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/handlers/LegacyHandlerService.java:646` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/keys/BindingPersistence.java:879` — `string == null || string.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/keys/model/KeyController.java:446` — `be.getTrigger() == null || be.getTrigger().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:313` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:649` — `defaultEditors == null || defaultEditors.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:897` — `xmlString == null || xmlString.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/LargeFileLimitsPreferenceHandler.java:423` — `largeFilePreference == null || largeFilePreference.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/WorkbenchServiceRegistry.java:194` — `name == null || name.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/WorkbenchServiceRegistry.java:198` — `level == null || level.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ShowInHandler.java:125` — `targetId == null || targetId.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ShowInMenu.java:183` — `context.getSelection() == null || context.getSelection().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuAdditionCacheEntry.java:123` — `location.getPath() == null || location.getPath().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuAdditionCacheEntry.java:133` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuAdditionCacheEntry.java:153` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuAdditionCacheEntry.java:333` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuAdditionCacheEntry.java:351` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/WorkbenchMenuService.java:115` — `location.getPath() == null || location.getPath().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/WorkbenchMenuService.java:126` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/WorkbenchMenuService.java:144` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuFactoryGenerator.java:54` — `location.getPath() == null || location.getPath().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuFactoryGenerator.java:64` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuFactoryGenerator.java:83` — `query == null || query.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuHelper.java:161` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuHelper.java:164` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/menus/MenuHelper.java:243` — `style == null || style.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/NavigationHistoryEntry.java:54` — `historyText == null || historyText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/DecoratorsPreferencePage.java:214` — `text == null || text.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:98` — `result == null || result.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/FileEditorsPreferencePage.java:117` — `newExtension == null || newExtension.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ActionExpression.java:928` — `selection == null || selection.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchWindow.java:1453` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchWindow.java:1456` — `id == null || id.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/SaveablesList.java:424` — `saveOptions == null || saveOptions.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/SaveablesList.java:586` — `saveOptionMap == null || saveOptionMap.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:482` — `adaptableList == null || adaptableList.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:660` — `objects == null || objects.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:866` — `objects == null || objects.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:904` — `objects == null || objects.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:937` — `objects == null || objects.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/Workbench.java:1943` — `nlExtensions == null || nlExtensions.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressInfoItem.java:514` — `taskString == null || taskString.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/PartTaggedAsEditorPropertyTester.java:40` — `partTags == null || partTags.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/about/AboutPluginsPage.java:692` — `searchPattern == null || searchPattern.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPartReference.java:278` — `toolTip == null || toolTip.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/e4/migration/PerspectiveBuilder.java:433` — `list == null || list.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/part/EditorInputTransfer.java:200` — `xmlString == null || xmlString.isEmpty()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CycleViewHandler.java:56` — `modelService.findElements(currentPerspective,null,MPart.class,null,EModelServ...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:687` — `entry.getValue().stream().map(QuickAccessElement::getId).collect(Collectors.t...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:717` — `elementsPerProvider.getValue().stream().map(element -> matcher.match(element,...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessExtensionManager.java:177` — `Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor(EXT...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CoolBarToTrimManager.java:313` — `workbenchTrimElements.stream().filter(e -> e instanceof MToolBar).map(e -> (M...`

#### Rule: `modernize-java9` → `modernize-java9.unmodifiable-list-arrays-aslist.consider-list-of`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchWindow.java:374` — `Collections.unmodifiableList(Arrays.asList("Spacer Glue","SearchField","Searc...`

### eclipse.platform
#### Rule: `stream-performance` → `stream-performance.sorted-before-collect.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ContentDescriptionManager.java:505` — `Arrays.stream(bundleContext.getBundles()).map(bundle -> String.format("%d %s ...`

#### Rule: `arrays` → `arrays.clone.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildCommand.java:151` — `arguments.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildManager.java:1157` — `prereqs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildContext.java:68` — `requestedBuilt.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/InternalBuilder.java:108` — `((BuildCommand)command).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Project.java:311` — `((ProjectDescription)description).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Project.java:438` — `description.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1093` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1174` — `sourceInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1445` — `info.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1499` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1544` — `markers.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:2158` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectInfo.java:130` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectInfo.java:142` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceDescription.java:68` — `buildOrder.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceDescription.java:160` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/NatureManager.java:189` — `oldNatures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/NatureManager.java:190` — `newNatures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:199` — `markers.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:228` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:255` — `syncInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:265` — `b.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:426` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:438` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:469` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:142` — `variableDescriptions.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:145` — `dynamicConfigRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:272` — `projRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:314` — `refs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:338` — `configs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:351` — `dynamicConfigRefs.get(configName).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:359` — `dynamicConfigRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:378` — `((BuildCommand)oldCommands[i]).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:394` — `dynamicRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:476` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:488` — `staticRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:672` — `((BuildCommand)value[i]).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:788` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:802` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:865` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:879` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:942` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:278` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:319` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:350` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/MarkerSet.java:83` — `elements.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTree.java:167` — `data.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTree.java:714` — `oldData.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTreeReaderImpl_1.java:59` — `data.clone()`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/ResourceDelta.java:242` — `System.arraycopy(children,0,result,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/utils/ObjectMap.java:172` — `System.arraycopy(elements,0,expanded,0,elements.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/utils/UniversalUniqueIdentifier.java:292` — `System.arraycopy(fBits,0,result,0,fBits.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/PrefixPool.java:93` — `System.arraycopy(pool,0,newprefixList,0,pool.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/HistoryBucket.java:73` — `System.arraycopy(uuidBytes,0,state,0,uuidBytes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/SafeChunkyInputStream.java:43` — `System.arraycopy(chunk,0,result,0,chunk.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:936` — `System.arraycopy(order.vertexes,0,projects,0,order.vertexes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:947` — `System.arraycopy(order.vertexes,0,buildConfigs,0,order.vertexes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceRoot.java:192` — `System.arraycopy(roots,0,result,0,roots.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/DataDeltaNode.java:82` — `System.arraycopy(children,0,childrenCopy,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/DataTreeNode.java:134` — `System.arraycopy(children,0,childrenCopy,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/NoDataDeltaNode.java:90` — `System.arraycopy(children,0,childrenCopy,0,children.length)`

#### Rule: `string-equals` → `string-equals.null-or-empty.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/ResourceDelta.java:580` — `markerDeltas == null || markerDeltas.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/ResourceChangeEvent.java:62` — `markerDeltas == null || markerDeltas.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/refresh/MonitorManager.java:214` — `resources == null || resources.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/HistoryStore2.java:313` — `fileEntry == null || fileEntry.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectContentTypes.java:237` — `associated == null || associated.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/LocationValidator.java:67` — `schemeSpecificPart == null || schemeSpecificPart.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/LocationValidator.java:106` — `schemeSpecificPart == null || schemeSpecificPart.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/PathVariableManager.java:250` — `schemeSpecificPart == null || schemeSpecificPart.isEmpty()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectPathVariableManager.java:315` — `schemeSpecificPart == null || schemeSpecificPart.isEmpty()`

### sandbox
#### Rule: `try-with-resources` → `Consider using try-with-resources for AutoCloseable resource`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/config/ReleasedRepositoryReadH2Test.java:92` — `context.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/config/ReleasedRepositoryReadH2Test.java:131` — `service.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/config/ReleasedRepositoryReadH2Test.java:132` — `service.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/config/ReleasedRuntimeStartupH2Test.java:127` — `context.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/LocalEmbeddingEncoderTest.java:70` — `encoder.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/AnalysisGenerationCacheTest.java:178` — `cache.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/AnalysisGenerationCacheTest.java:202` — `cache.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/AnalysisGenerationCacheTest.java:216` — `cache.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/HistoricalAnalysisH2Test.java:114` — `generations.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/HistoricalAnalysisH2Test.java:114` — `repositories.close()`
- `sandbox-jgit-server-webapp/tst/org/eclipse/jgit/server/analysis/HistoricalAnalysisH2Test.java:114` — `provider.close()`
- `sandbox_triggerpattern_test/src/org/sandbox/jdt/internal/ui/wizard/ProjectRulePreviewTest.java:54` — `candidate.close()`
- `sandbox_triggerpattern_test/src/org/sandbox/jdt/internal/ui/wizard/ProjectRulePreviewTest.java:60` — `candidate.close()`

#### Rule: `collection-performance` → `collection-performance.map-values-isempty.to-map-isempty`
- `sandbox_triggerpattern_test/src/org/sandbox/jdt/internal/ui/preferences/LlmSecureCredentialsStoreTest.java:54` — `store.values().isEmpty()` → `store.isEmpty()`
- `sandbox_triggerpattern_test/src/org/sandbox/jdt/internal/ui/preferences/LlmSecureCredentialsStoreTest.java:65` — `store.values().isEmpty()` → `store.isEmpty()`

#### Rule: `collection-performance` → `collection-performance.map-values-size.to-map-size`
- `sandbox_common_core/src/test/java/org/sandbox/jdt/triggerpattern/internal/StructuredRewriteActionParserTest.java:85` — `list.values().size()` → `list.size()`

#### Rule: `stream-performance` → `stream-performance.nested-flatmap.review`
- `sandbox_tools/src/org/sandbox/jdt/internal/corext/fix/helper/WhileToForEach.java:258` — `miExpr.receiver().flatMap(receiver -> receiver.asSimpleName()).flatMap(Simple...`
- `sandbox_triggerpattern_test/src/org/sandbox/jdt/internal/ui/wizard/ProjectRuleRefactoringTest.java:322` — `java.util.Arrays.stream(registry.getConfigurationElementsFor("org.eclipse.ui....`

#### Rule: `stream-performance` → `stream-performance.sorted-before-collect.review`
- `sandbox_common/src/org/sandbox/jdt/cleanup/multifile/GeneratedNameAllocator.java:205` — `group.stream().map(NestedTypeRequest::requestId).filter(candidateId -> !candi...`
- `sandbox_distribution_verify/src/main/java/org/sandbox/distribution/AggregateInstallationVerifier.java:920` — `variable.getModifiers().stream().map(modifier -> modifier.name().toLowerCase(...`

#### Rule: `stream-performance` → `stream-performance.collection-stream-foreach.to-collection-foreach`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/JHViewContentProvider.java:103` — `Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).stream().fo...` → `Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).forEach((pa...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/VisitorTest.java:191` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(entry....` → `dataholder.entrySet().forEach((entry -> {   System.out.println(entry.getKey()...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/VisitorTest.java:239` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println("=====...` → `dataholder.entrySet().forEach((entry -> {   System.out.println("=============...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:152` — `VisitorEnum.stream().forEach(ve -> {   hv.add(ve,(node,holder) -> {     holde...` → `VisitorEnum.forEach((ve -> {   hv.add(ve,(node,holder) -> {     holder.merge(...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:188` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(entry....` → `dataholder.entrySet().forEach((entry -> {   System.out.println(entry.getKey()...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:230` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println("Posit...` → `dataholder.entrySet().forEach((entry -> {   System.out.println("Position " + ...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:291` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(ASTNod...` → `dataholder.entrySet().forEach((entry -> {   System.out.println(ASTNode.nodeCl...`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:1591` — `Arrays.asList(additionalExpectedOperators).contains(actualOperator)`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:3301` — `Arrays.asList(fieldNames).contains(node.getName().getIdentifier())`
- `sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views/MathematicsHelpScreenshots.java:323` — `Arrays.asList(labels).contains(control.getText().replace("&",""))`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/internal/ui/fix/MathematicalWorkbenchSWTBotTest.java:445` — `Arrays.asList(labels).contains(control.getText().replace("&",""))`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapperTest.java:42` — `Arrays.asList(filtered).contains("test")`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapperTest.java:66` — `Arrays.asList(filtered).contains("--source")`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapperTest.java:139` — `Arrays.asList(values).contains(expected.toFile().getPath())`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapperTest.java:144` — `Arrays.asList(values).contains(unexpected.toFile().getPath())`

#### Rule: `arrays` → `arrays.clone.review`
- `sandbox_common/src/org/sandbox/jdt/cleanup/multifile/AbstractPlannedMultiFileCleanUp.java:174` — `compilationUnits.clone()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/cleanup/NlsAwareCleanUpFix.java:29` — `operations.clone()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/editor/SandboxHintTemplateStore.java:75` — `TEMPLATES.clone()`
- `sandbox_eclipse_help_swtbot_test/src/org/sandbox/jdt/ui/helper/views/CleanupSourceSnapshot.java:29` — `Objects.requireNonNull(bytes).clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java:74` — `byteOrderMark.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java:79` — `byteOrderMark.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java:210` — `bytes.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java:215` — `bytes.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/git/UnifiedDiffFormatter.java:51` — `before.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/git/UnifiedDiffFormatter.java:52` — `after.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Pattern.java:73` — `constraints.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Pattern.java:149` — `constraints.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:343` — `BUNDLED_LIBRARIES.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:352` — `DISABLED_BUNDLED_LIBRARIES.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:73` — `originalBytes.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:74` — `bom.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:79` — `originalBytes.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:84` — `bom.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:92` — `transformedBytes.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:97` — `transformedBytes.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:208` — `bom.clone()`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/XMLResourceSupport.java:213` — `bom.clone()`
- `.github/probes/patched-jdt-ui/src/org/sandbox/jdt/ui/probe/ScopeExpansionProbeApplication.java:312` — `compilationUnits.clone()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitBestEffortSupport.java:273` — `fixes.clone()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitBestEffortSupport.java:708` — `fixes.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/OrderedSourceReadComputationTest.java:48` — `values.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:86` — `source.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:86` — `up.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:87` — `source.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:87` — `up.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:157` — `input.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/NewUpstreamMathPilotTest.java:157` — `input.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/PreservedArraySourceTest.java:173` — `left.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/PreservedArraySourceTest.java:173` — `right.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/FieldArrayStateVisibilityTest.java:101` — `value.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/FieldArrayOrderingTest.java:156` — `left.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/FieldArrayOrderingTest.java:157` — `right.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/BouncyCastleModularResidueTest.java:84` — `infinity.clone()`
- `sandbox_math_cleanup_test/src/org/sandbox/jdt/math/tests/BouncyCastleModularResidueTest.java:85` — `infinity.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapperExitCodeTest.java:111` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapperExitCodeTest.java:119` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWorkspacePathTest.java:210` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CleanupPatchLifecycleTest.java:213` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CleanupPatchLifecycleTest.java:220` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapperPersistedClasspathTest.java:120` — `arguments.clone()`
- `sandbox_cleanup_application_test/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapperWorkspaceRuleTest.java:202` — `arguments.clone()`
- `sandbox_test_commons/integration-src/org/sandbox/jdt/ui/tests/quickfix/rules/CompilerDiagnosticRegressionTest.java:188` — `source.clone()`
- `sandbox_functional_converter_test/src/org/sandbox/jdt/ui/tests/quickfix/StreamChainToLoopTest.java:106` — `cleanups.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapper.java:65` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapper.java:70` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapper.java:81` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/CodeCleanupApplicationWrapper.java:89` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ProjectSnapshot.java:38` — `content.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ProjectSnapshot.java:44` — `content.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java:51` — `arguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java:56` — `arguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java:66` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java:73` — `applicationArguments.clone()`
- `sandbox_cleanup_application/src/org/sandbox/jdt/core/cleanupapp/ScopeFilteringCodeCleanupApplicationWrapper.java:164` — `arguments.clone()`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/editor/SandboxHintSourceViewerConfiguration.java:122` — `System.arraycopy(defaults,0,result,0,defaults.length)`
- `sandbox_common_core/src/test/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserverCompatibilityTest.java:93` — `System.arraycopy(prefix,0,result,0,prefix.length)`
- `sandbox_common_core/src/test/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserverTest.java:103` — `System.arraycopy(bom,0,result,0,bom.length)`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/cleanup/multifile/api/LineDelimiterPreserver.java:182` — `System.arraycopy(bom,0,result,0,bom.length)`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:366` — `System.arraycopy(BUNDLED_LIBRARIES,0,result,0,BUNDLED_LIBRARIES.length)`
- `sandbox_test_commons/src/org/sandbox/jdt/ui/tests/quickfix/rules/EclipseBundleClasspath.java:134` — `System.arraycopy(current,0,updated,0,current.length)`
- `sandbox_test_commons/src/org/sandbox/jdt/ui/tests/quickfix/rules/AbstractEclipseJava.java:452` — `System.arraycopy(prevNatures,0,newNatures,0,prevNatures.length)`

#### Rule: `string-equals` → `string-equals.null-or-empty.review`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/AnalyticsResource.java:59` — `repo == null || repo.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/AdminResource.java:168` — `expectedToken == null || expectedToken.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:118` — `repo == null || repo.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:127` — `query == null || query.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:230` — `blobId == null || blobId.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:509` — `commitA == null || commitA.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:529` — `commitA == null || commitA.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/SearchResource.java:775` — `value == null || value.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/ApplicationSourceReports.java:38` — `prefix == null || prefix.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/ApplicationSourceReports.java:111` — `prefix == null || prefix.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/AnalysisSettings.java:32` — `extensions == null || extensions.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/AnalysisSettings.java:44` — `value == null || value.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/LocalEmbeddingEncoder.java:97` — `value == null || value.isEmpty()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/analysis/LocalEmbeddingEncoder.java:110` — `value == null || value.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/cleanup/multifile/RelatedCompilationUnitSearch.java:156` — `projects == null || projects.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/cleanup/multifile/JavaProjectCompilationUnits.java:270` — `currentScope == null || currentScope.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/cleanup/PlanAwareHintFileFixCore.java:80` — `plan == null || plan.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/cleanup/DslPluginRegistry.java:66` — `id == null || id.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/cleanup/DslPluginRegistry.java:70` — `bundleId == null || bundleId.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/mining/git/CommandLineGitProvider.java:195` — `diffOutput == null || diffOutput.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/mining/git/JGitHistoryProvider.java:231` — `diffText == null || diffText.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/editor/SandboxHintHyperlinkDetector.java:56` — `word == null || word.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/GuardRegistry.java:158` — `name == null || name.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/internal/corext/util/NamingUtils.java:63` — `name == null || name.isEmpty()`
- `sandbox_common/src/org/sandbox/jdt/internal/corext/util/NamingUtils.java:84` — `input == null || input.isEmpty()`
- `sandbox-functional-converter-core/src/main/java/org/sandbox/functional/core/renderer/StringRenderer.java:119` — `comparatorExpression == null || comparatorExpression.isEmpty()`
- `sandbox-functional-converter-core/src/main/java/org/sandbox/functional/core/renderer/StringRenderer.java:202` — `rawComment == null || rawComment.isEmpty()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/wizard/NewSandboxHintFileWizardPage.java:211` — `selection == null || selection.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/PatternIndex.java:216` — `entries == null || entries.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/PatternIndex.java:241` — `entries == null || entries.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ExecutionTreeSnapshot.java:90` — `nodes == null || nodes.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ExecutionTreeSnapshot.java:97` — `attributes == null || attributes.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ImportDirective.java:240` — `replacementPattern == null || replacementPattern.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ImportDirective.java:284` — `sourcePattern == null || sourcePattern.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ImportDirective.java:327` — `replacementPatterns == null || replacementPatterns.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ImportDirective.java:383` — `pattern == null || pattern.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/ImportDirective.java:415` — `pattern == null || pattern.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/OpenAiCompatibleClient.java:256` — `choices == null || choices.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/OpenAiCompatibleClient.java:293` — `choices == null || choices.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/GeminiClient.java:301` — `candidates == null || candidates.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/GeminiClient.java:322` — `parts == null || parts.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/GeminiClient.java:519` — `candidates == null || candidates.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/GeminiClient.java:542` — `parts == null || parts.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/llm/GeminiClient.java:601` — `json == null || json.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/EmbeddedJavaCompiler.java:349` — `input == null || input.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/BuiltInGuards.java:1202` — `version == null || version.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/PlaceholderInfo.java:41` — `name == null || name.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintTypeDeclarationSourcePreprocessor.java:30` — `source == null || source.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileParser.java:1325` — `text == null || text.isEmpty()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:518` — `hintFile.getTags() == null || hintFile.getTags().isEmpty()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/EnhancedForHandler.java:491` — `targetVariable == null || targetVariable.isEmpty()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/JdtLoopExtractor.java:1070` — `commentList == null || commentList.isEmpty()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ASTStreamRenderer.java:685` — `expressionText == null || expressionText.isEmpty()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUpCore.java:506` — `units == null || units.isEmpty()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/helper/RunWithJUnitPlugin.java:161` — `runnerQualifiedName == null || runnerQualifiedName.isEmpty()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/corext/fix/multifile/JUnitMigrationPlan.java:118` — `blockedRuleUnitHandles == null || blockedRuleUnitHandles.isEmpty()`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/VariableNameSuggester.java:122` — `str == null || str.isEmpty()`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/VariableNameSuggester.java:135` — `str == null || str.isEmpty()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/report/StatisticsCollector.java:237` — `evaluations == null || evaluations.isEmpty()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/scanner/SourceScanner.java:66` — `subPaths == null || subPaths.isEmpty()`
- `sandbox_method_reuse/src/org/sandbox/jdt/internal/corext/fix/helper/lib/SideEffectAnalyzer.java:47` — `statements == null || statements.isEmpty()`
- `sandbox_int_to_enum/src/org/sandbox/jdt/internal/ui/fix/IntToEnumCleanUpCore.java:271` — `units == null || units.isEmpty()`
- `sandbox_int_to_enum/src/org/sandbox/jdt/internal/corext/fix/helper/SwitchIntToEnumHelper.java:336` — `names == null || names.isEmpty()`
- `sandbox_oomph/src/test/resources/probe/SetupProbe.java:984` — `unsigned == null || unsigned.isEmpty()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `sandbox_tools/src/org/sandbox/jdt/internal/ui/fix/UseIteratorToForLoopCleanUpCore.java:87` — `computeFixSet().stream().map(UseIteratorToForLoopFixCore::toString).collect(C...`
- `sandbox_platform_helper/src/org/sandbox/jdt/internal/ui/fix/SimplifyPlatformStatusCleanUpCore.java:89` — `computeFixSet().stream().map(SimplifyPlatformStatusFixCore::toString).collect...`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/ui/fix/XMLCleanUpCore.java:94` — `computeFixSet().stream().map(XMLCleanUpFixCore::toString).collect(Collectors....`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUpCore.java:372` — `computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collector...`
- `sandbox_use_general_type/src/org/sandbox/jdt/internal/ui/fix/UseGeneralTypeCleanUpCore.java:83` — `computeFixSet().stream().map(UseGeneralTypeFixCore::toString).collect(Collect...`
- `sandbox_encoding_quickfix/src/org/sandbox/jdt/internal/ui/fix/UseExplicitEncodingCleanUpCore.java:204` — `computeFixSet().stream().map(UseExplicitEncodingFixCore::toString).collect(Co...`
- `sandbox_int_to_enum/src/org/sandbox/jdt/internal/ui/fix/IntToEnumCleanUpCore.java:213` — `computeFixSet().stream().map(IntToEnumFixCore::toString).collect(Collectors.t...`

#### Rule: `modernize-java9` → `modernize-java9.optional-ternary.to-orelse`
- `sandbox_common_core/src/test/java/org/sandbox/jdt/triggerpattern/test/SideEffectGuardTest.java:177` — `optional.isPresent() ? optional.get() : empty[0]`

