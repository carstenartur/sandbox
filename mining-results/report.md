# Refactoring Mining Report — 2026-07-06

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 0 | 0 |
| eclipse.jdt.ui | 1474 | 52 | 9 |
| eclipse.platform.ui | 1147 | 62 | 8 |
| eclipse.platform | 313 | 62 | 3 |
| eclipse.platform.text | 0 | 0 | 0 |
| eclipse.platform.debug | 0 | 0 | 0 |
| sandbox | 939 | 36 | 8 |

## Details
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
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/wizards/NewJavaProjectWizardPage.java:168` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/AddToClasspathAction.java:136` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/JavaOutlinePage.java:685` — `System.arraycopy(sortedObjects,0,sortedRegions,0,sortedObjects.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/CompoundEditExitStrategy.java:164` — `System.arraycopy(commandIds,0,fCommandIds,0,commandIds.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:800` — `System.arraycopy(elements,0,newElements,0,elements.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetModel.java:719` — `System.arraycopy(workingSets,0,activeWorkingSets,0,workingSets.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/BuildPathBasePage.java:253` — `System.arraycopy(exclusionFilters,0,newExclusionFilters,0,exclusionFilters.le...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/EditFilterWizard.java:30` — `System.arraycopy(inc,0,fOrginalInclusion,0,inc.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/EditFilterWizard.java:34` — `System.arraycopy(excl,0,fOriginalExclusion,0,excl.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListElement.java:398` — `System.arraycopy(filters,0,newFilters,0,filters.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/AbstractNewFolderWizardPage.java:483` — `System.arraycopy(exclusionFilters,0,newExclusionFilters,0,exclusionFilters.le...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/util/RowLayouter.java:143` — `System.arraycopy(fDefaultGridDatas,0,newDatas,0,fDefaultGridDatas.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/CombinedWordRule.java:144` — `System.arraycopy(old,0,fContent,0,old.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuesser.java:318` — `System.arraycopy(v.triggerChars,0,triggers,0,v.triggerChars.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/actions/ConfigureContainerAction.java:90` — `System.arraycopy(entries,0,newEntries,0,entries.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/actions/CompositeActionGroup.java:46` — `System.arraycopy(fGroups,0,newGroups,0,fGroups.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/ComplianceConfigurationBlock.java:222` — `System.arraycopy(keys,0,allKeys,0,keys.length)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/ClasspathContainerPreferencePage.java:104` — `System.arraycopy(entries,0,newEntries,0,entries.length)`

#### Rule: `arrays` → `arrays.clone.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/CustomFiltersActionGroup.java:557` — `patterns.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:134` — `fields.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:137` — `inheritedFields.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:140` — `methods.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/GenerateToStringDialog.java:143` — `inheritedMethods.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UnboxingCleanUp.java:190` — `binding.getParameterTypes().clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/JavaSourceHover.java:200` — `sourceLines.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/AnnotationExpansionControl.java:789` — `range.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/hover/AnnotationExpansionControl.java:827` — `styleRange.clone()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javadocexport/ContributedJavadocWizardPage.java:130` — `sourceElements.clone()`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:774` — `Arrays.asList(VALID_WORKING_SET_IDS).contains(set.getId())`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/ConfigureWorkingSetAssignementAction.java:788` — `Arrays.asList(set.getElements()).contains(adaptedElement)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetModel.java:653` — `Arrays.asList(getAllWorkingSets()).contains(workingSet)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/LibrariesWorkbookPage.java:373` — `Arrays.asList(segments).contains(JavaRuntime.JRE_CONTAINER)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/util/SelectionUtil.java:85` — `Arrays.asList(resources).contains(null)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultPhoneticHashProvider.java:167` — `Arrays.asList(candidates).contains(token)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/HierarchyLabelProvider.java:107` — `Arrays.asList(elements).contains(element)`

#### Rule: `arrays` → `arrays.aslist-stream.to-arrays-stream`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/correction/NewImplementationProposal.java:46` — `Arrays.asList(actions).stream()` → `java.util.Arrays.stream(actions)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/correction/proposals/NewInterfaceImplementationProposal.java:46` — `Arrays.asList(actions).stream()` → `java.util.Arrays.stream(actions)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/correction/NewJUnitTestCaseProposal.java:46` — `Arrays.asList(actions).stream()` → `java.util.Arrays.stream(actions)`

#### Rule: `string-isblank` → `string-isblank1`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/dialogs/OverrideMethodDialog.java:450` — `searchText.trim().isEmpty()` → `searchText.isBlank()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackagerfat/FatJarManifestProvider.java:105` — `manifestClasspath.trim().isEmpty()` → `manifestClasspath.isBlank()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/actions/IndentAction.java:936` — `prevLineString.trim().isEmpty()` → `prevLineString.isBlank()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/formatter/ModifyDialog.java:1014` — `filterText.trim().isEmpty()` → `filterText.isBlank()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/preferences/formatter/ModifyDialog.java:1530` — `previewCode.trim().isEmpty()` → `previewCode.isBlank()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaImplementationCodeMining.java:195` — `Stream.of(results).filter(t -> t.getAncestor(IJavaElement.COMPILATION_UNIT) !...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaImplementationCodeMining.java:211` — `Stream.of(results).filter(t -> t.getAncestor(IJavaElement.COMPILATION_UNIT) !...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleSelectionDialog.java:345` — `fSelectedModules.stream().filter(m -> !fAllIncluded.contains(m)).map(fModules...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesPage.java:762` — `Arrays.stream(fAllSystemRoots).map(pfr -> pfr.getModuleDescription().getEleme...`

### eclipse.platform.ui
#### Rule: `deprecations` → `deprecations.runtime-exec-string.consider-processbuilder`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:70` — `Runtime.getRuntime().exec(new String[]{"/usr/bin/open",localHref})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:85` — `Runtime.getRuntime().exec(new String[]{webBrowser,"-remote","openURL(" + enco...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:137` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:146` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/browser/DefaultWebBrowser.java:155` — `Runtime.getRuntime().exec(new String[]{webBrowser,href})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/ExternalEditor.java:126` — `Runtime.getRuntime().exec(new String[]{"open","-a",programFileName,path})`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/ExternalEditor.java:128` — `Runtime.getRuntime().exec(new String[]{programFileName,path})`

#### Rule: `stream-performance` → `stream-performance.collection-stream-foreach.to-collection-foreach`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbookEditorsHandler.java:167` — `groupedEditorReferences.getValue().stream().forEach(editorReference -> editor...` → `groupedEditorReferences.getValue().forEach(editorReference -> editorReference...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbookEditorsHandler.java:200` — `refsToMakeDistinguishableViaPathSegments.stream().forEach(e -> editorReferenc...` → `refsToMakeDistinguishableViaPathSegments.forEach(e -> editorReferenceLabelTex...`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/ActivityCategoryPreferencePage.java:401` — `System.arraycopy(enabledCategories,0,allChecked,0,enabledCategories.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/WorkingSetConfigurationBlock.java:78` — `System.arraycopy(workingSetIds,0,workingSetIdsCopy,0,workingSetIds.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/EditorSelectionDialog.java:511` — `System.arraycopy(externalEditors,0,newEditors,0,externalEditors.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/FilteredList.java:255` — `System.arraycopy(elements,0,fElements,0,elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/CommandImageManagerEvent.java:95` — `System.arraycopy(changedCommandIds,0,copy,0,changedCommandIds.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/DecoratorsPreferencePage.java:147` — `System.arraycopy(elements,0,results,0,elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/WorkingSetSelectionDialog.java:313` — `System.arraycopy(untypedResult,0,typedResult,0,untypedResult.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/NewWizardNewPage.java:398` — `System.arraycopy(currentExpanded,0,expanded,0,currentExpanded.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/SimpleWorkingSetSelectionDialog.java:156` — `System.arraycopy(checked,0,workingSets,0,checked.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/wizards/AbstractExtensionWizardRegistry.java:69` — `System.arraycopy(localPrimaryWizards,0,newPrimary,0,localPrimaryWizards.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/wizards/preferences/WizardPreferencesPage.java:590` — `System.arraycopy(checkedElements,0,transferElements,0,checkedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:288` — `System.arraycopy(editorArray,0,newArray,0,editorArray.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/keys/KeysPreferencePage.java:191` — `System.arraycopy(sortOrder,0,newSortOrder,0,sortOrder.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressInfoItem.java:588` — `System.arraycopy(children,0,infos,0,children.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/DetailedProgressViewer.java:563` — `System.arraycopy(children,0,progressInfoItems,0,children.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/about/AboutFeaturesPage.java:165` — `System.arraycopy(clientArray,0,bundleGroupInfos,0,clientArray.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/DecoratorManager.java:276` — `System.arraycopy(oldDefs,0,fullDefinitions,0,oldDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/DecoratorManager.java:639` — `System.arraycopy(fullDefinitions,0,returnValue,0,fullDefinitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/LightweightDecoratorManager.java:165` — `System.arraycopy(oldDefs,0,lightweightDefinitions,0,oldDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:231` — `System.arraycopy(selectedElements,0,adaptables,0,selectedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:2298` — `System.arraycopy(parts,0,editors,0,parts.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:65` — `System.arraycopy(definitions,0,copyOfDefinitions,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:177` — `System.arraycopy(definitions,0,copyOfDefinitions,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementHelper.java:239` — `System.arraycopy(allDefs,0,copy,0,allDefs.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ColorsAndFontsPreferencePage.java:1487` — `System.arraycopy(definitions,0,definitionsCopy,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ColorsAndFontsPreferencePage.java:1532` — `System.arraycopy(definitions,0,definitionsCopy,0,definitions.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AggregateWorkingSet.java:49` — `System.arraycopy(components,0,componentCopy,0,components.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AggregateWorkingSet.java:199` — `System.arraycopy(localComponents,0,copiedArray,0,localComponents.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/AbstractWorkingSetManager.java:868` — `System.arraycopy(elements,0,newElements,0,elements.length)`

#### Rule: `arrays` → `arrays.clone.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/part/PageBookView.java:471` — `((HashMap<IWorkbenchPart,PageRec>)mapPartToRec).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/ws/WorkbenchActivitySupport.java:397` — `mutableActivityManager.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/FileEditorsPreferencePage.java:382` — `mapping.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:98` — `((ArrayList<IEditorDescriptor>)editors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:99` — `((ArrayList<IEditorDescriptor>)deletedEditors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/FileEditorMapping.java:100` — `((ArrayList<IEditorDescriptor>)declaredDefaultEditors).clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/EditorRegistry.java:1166` — `defaultMap.clone()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressManagerUtil.java:59` — `elements.clone()`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/LargeFileLimitsPreferenceHandler.java:417` — `Arrays.asList(disabled).contains(fileExtension)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:3777` — `Arrays.asList(models).contains(model)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:3787` — `Arrays.asList(models).contains(model)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/e4/migration/MementoReader.java:56` — `Arrays.asList(memento.getAttributeKeys()).contains(attribute)`

#### Rule: `string-isblank` → `string-isblank1`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/menus/CommandContributionItem.java:569` — `tooltip.trim().isEmpty()` → `tooltip.isBlank()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/model/WorkbenchPartLabelProvider.java:81` — `path.trim().isEmpty()` → `path.isBlank()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/model/WorkbenchPartLabelProvider.java:88` — `path.trim().isEmpty()` → `path.isBlank()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/ImportExportPespectiveHandler.java:211` — `trimsData.trim().isEmpty()` → `trimsData.isBlank()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressAnimationItem.java:241` — `tt.trim().isEmpty()` → `tt.isBlank()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/StatusUtil.java:119` — `message.trim().isEmpty()` → `message.isBlank()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CycleViewHandler.java:56` — `modelService.findElements(currentPerspective,null,MPart.class,null,EModelServ...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:553` — `entry.getValue().stream().map(QuickAccessElement::getId).collect(Collectors.t...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:583` — `elementsPerProvider.getValue().stream().map(element -> matcher.match(element,...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessExtensionManager.java:177` — `Arrays.stream(Platform.getExtensionRegistry().getConfigurationElementsFor(EXT...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CoolBarToTrimManager.java:313` — `workbenchTrimElements.stream().filter(e -> e instanceof MToolBar).map(e -> (M...`

#### Rule: `modernize-java9` → `modernize-java9.unmodifiable-list-arrays-aslist.consider-list-of`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchWindow.java:356` — `Collections.unmodifiableList(Arrays.asList("Spacer Glue","SearchField","Searc...`

### eclipse.platform
#### Rule: `stream-performance` → `stream-performance.sorted-before-collect.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ContentDescriptionManager.java:505` — `Arrays.stream(bundleContext.getBundles()).map(bundle -> String.format("%d %s ...`

#### Rule: `arrays` → `arrays.clone.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:278` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:319` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Marker.java:350` — `markerInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/NatureManager.java:189` — `oldNatures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/NatureManager.java:190` — `newNatures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:142` — `variableDescriptions.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:145` — `dynamicConfigRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:272` — `projRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:314` — `refs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:338` — `configs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:351` — `dynamicConfigRefs.get(configName).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:359` — `dynamicConfigRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:378` — `((BuildCommand)oldCommands[i]).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:394` — `dynamicRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:475` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:487` — `staticRefs.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:671` — `((BuildCommand)value[i]).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:787` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:801` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:864` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:878` — `tempMap.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:941` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:199` — `markers.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:228` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:255` — `syncInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:265` — `b.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:426` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:438` — `sessionProperties.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ResourceInfo.java:469` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1093` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1174` — `sourceInfo.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1445` — `info.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1499` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:1544` — `markers.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:2158` — `resources.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/MarkerSet.java:83` — `elements.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectInfo.java:130` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectInfo.java:142` — `natures.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Project.java:324` — `((ProjectDescription)description).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Project.java:451` — `description.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceDescription.java:68` — `buildOrder.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceDescription.java:160` — `value.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTree.java:167` — `data.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTree.java:694` — `oldData.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/watson/ElementTreeReaderImpl_1.java:59` — `data.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/InternalBuilder.java:108` — `((BuildCommand)command).clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildContext.java:68` — `requestedBuilt.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildCommand.java:151` — `arguments.clone()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildManager.java:1157` — `prereqs.clone()`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/WorkspaceRoot.java:192` — `System.arraycopy(roots,0,result,0,roots.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:936` — `System.arraycopy(order.vertexes,0,projects,0,order.vertexes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Workspace.java:947` — `System.arraycopy(order.vertexes,0,buildConfigs,0,order.vertexes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/SafeChunkyInputStream.java:43` — `System.arraycopy(chunk,0,result,0,chunk.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/PrefixPool.java:93` — `System.arraycopy(pool,0,newprefixList,0,pool.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/localstore/HistoryBucket.java:73` — `System.arraycopy(uuidBytes,0,state,0,uuidBytes.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/utils/ObjectMap.java:172` — `System.arraycopy(elements,0,expanded,0,elements.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/utils/UniversalUniqueIdentifier.java:292` — `System.arraycopy(fBits,0,result,0,fBits.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/NoDataDeltaNode.java:90` — `System.arraycopy(children,0,childrenCopy,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/DataDeltaNode.java:82` — `System.arraycopy(children,0,childrenCopy,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/dtree/DataTreeNode.java:134` — `System.arraycopy(children,0,childrenCopy,0,children.length)`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/ResourceDelta.java:242` — `System.arraycopy(children,0,result,0,children.length)`

### sandbox
#### Rule: `stream-performance` → `stream-performance.collection-stream-foreach.to-collection-foreach`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/JHViewContentProvider.java:103` — `Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).stream().fo...` → `Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).forEach(pac...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:152` — `VisitorEnum.stream().forEach(ve -> {   hv.add(ve,(node,holder) -> {     holde...` → `VisitorEnum.forEach(ve -> {   hv.add(ve,(node,holder) -> {     holder.merge(V...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:188` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(entry....` → `dataholder.entrySet().forEach(entry -> {   System.out.println(entry.getKey()....`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:230` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println("Posit...` → `dataholder.entrySet().forEach(entry -> {   System.out.println("Position " + e...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/ReferenceHolderTest.java:291` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(ASTNod...` → `dataholder.entrySet().forEach(entry -> {   System.out.println(ASTNode.nodeCla...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/VisitorTest.java:193` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println(entry....` → `dataholder.entrySet().forEach(entry -> {   System.out.println(entry.getKey() ...`
- `sandbox_common_test/src/org/sandbox/jdt/ui/tests/quickfix/VisitorTest.java:241` — `dataholder.entrySet().stream().forEach(entry -> {   System.out.println("=====...` → `dataholder.entrySet().forEach(entry -> {   System.out.println("============="...`

#### Rule: `stream-performance` → `stream-performance.nested-flatmap.review`
- `sandbox_tools/src/org/sandbox/jdt/internal/corext/fix/helper/WhileToForEach.java:258` — `miExpr.receiver().flatMap(receiver -> receiver.asSimpleName()).flatMap(Simple...`

#### Rule: `arrays` → `arrays.aslist-stream.to-arrays-stream`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/JHViewContentProvider.java:103` — `Arrays.asList(packageRoot.getJavaProject().getPackageFragments()).stream()` → `java.util.Arrays.stream(packageRoot.getJavaProject().getPackageFragments())`

#### Rule: `arrays` → `arrays.clone.review`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Pattern.java:73` — `constraints.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Pattern.java:149` — `constraints.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:343` — `BUNDLED_LIBRARIES.clone()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:352` — `DISABLED_BUNDLED_LIBRARIES.clone()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/editor/SandboxHintTemplateStore.java:75` — `TEMPLATES.clone()`

#### Rule: `arrays` → `arrays.arraycopy-full-copy.review`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/HintFileStore.java:366` — `System.arraycopy(BUNDLED_LIBRARIES,0,result,0,BUNDLED_LIBRARIES.length)`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/editor/SandboxHintSourceViewerConfiguration.java:157` — `System.arraycopy(defaults,0,result,0,defaults.length)`
- `sandbox_test_commons/src/org/sandbox/jdt/ui/tests/quickfix/rules/AbstractEclipseJava.java:396` — `System.arraycopy(prevNatures,0,newNatures,0,prevNatures.length)`

#### Rule: `arrays` → `arrays.aslist-contains.review`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:1591` — `Arrays.asList(additionalExpectedOperators).contains(actualOperator)`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:3301` — `Arrays.asList(fieldNames).contains(node.getName().getIdentifier())`

#### Rule: `string-isblank` → `string-isblank1`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/JavaHelperView.java:329` — `newText.trim().isEmpty()` → `newText.isBlank()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/wizard/NewRuleWizardPage.java:459` — `sourcePatternText.getText().trim().isEmpty()` → `sourcePatternText.getText().isBlank()`
- `sandbox_xml_cleanup_test/src/test/java/org/sandbox/jdt/ui/tests/quickfix/XMLTestUtils.java:120` — `child.getTextContent().trim().isEmpty()` → `child.getTextContent().isBlank()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ASTStreamRenderer.java:101` — `parts[0].trim().isEmpty()` → `parts[0].isBlank()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/corext/fix/helper/ASTStreamRenderer.java:101` — `parts[1].trim().isEmpty()` → `parts[1].isBlank()`
- `sandbox-jgit-server-webapp/src/org/eclipse/jgit/server/rest/RepositoryResource.java:87` — `name.trim().isEmpty()` → `name.isBlank()`
- `sandbox-functional-converter-core/src/main/java/org/sandbox/functional/core/renderer/StringRenderer.java:43` — `parts[0].trim().isEmpty()` → `parts[0].isBlank()`
- `sandbox-functional-converter-core/src/main/java/org/sandbox/functional/core/renderer/StringRenderer.java:43` — `parts[1].trim().isEmpty()` → `parts[1].isBlank()`

#### Rule: `modernize-java9` → `modernize-java16.stream-collect-tolist.consider-stream-tolist`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/ui/fix/XMLCleanUpCore.java:94` — `computeFixSet().stream().map(XMLCleanUpFixCore::toString).collect(Collectors....`
- `sandbox_int_to_enum/src/org/sandbox/jdt/internal/ui/fix/IntToEnumCleanUpCore.java:91` — `computeFixSet().stream().map(IntToEnumFixCore::toString).collect(Collectors.t...`
- `sandbox_use_general_type/src/org/sandbox/jdt/internal/ui/fix/UseGeneralTypeCleanUpCore.java:83` — `computeFixSet().stream().map(UseGeneralTypeFixCore::toString).collect(Collect...`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/ui/fix/UseFunctionalCallCleanUpCore.java:107` — `computeFixSet().stream().map(UseFunctionalCallFixCore::toString).collect(Coll...`
- `sandbox_jface_cleanup/src/org/sandbox/jdt/internal/ui/fix/JFaceCleanUpCore.java:89` — `computeFixSet().stream().map(JfaceCleanUpFixCore::toString).collect(Collector...`
- `sandbox_tools/src/org/sandbox/jdt/internal/ui/fix/UseIteratorToForLoopCleanUpCore.java:87` — `computeFixSet().stream().map(UseIteratorToForLoopFixCore::toString).collect(C...`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUpCore.java:92` — `computeFixSet().stream().map(JUnitCleanUpFixCore::toString).collect(Collector...`
- `sandbox_encoding_quickfix/src/org/sandbox/jdt/internal/ui/fix/UseExplicitEncodingCleanUpCore.java:139` — `computeFixSet().stream().map(UseExplicitEncodingFixCore::toString).collect(Co...`
- `sandbox_platform_helper/src/org/sandbox/jdt/internal/ui/fix/SimplifyPlatformStatusCleanUpCore.java:92` — `computeFixSet().stream().map(SimplifyPlatformStatusFixCore::toString).collect...`


<!-- report-hash: 71fc6acddc11bc645c6e2f1083ac859bd545e46ae0899439a62d3ed9bb410b52 -->
