# Refactoring Mining Report — 2026-02-27

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 85 | 1 |
| eclipse.jdt.ui | 1468 | 31 | 2 |
| eclipse.platform.ui | 1144 | 11 | 2 |
| sandbox | 756 | 0 | 0 |

## Details
### eclipse.jdt.core
#### Rule: `performance` → `Unnecessary String constructor`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/TypeConflictingSimpleNameFinder.java:59` — `new String(simpleTypeName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:216` — `new String(this.binding.computeUniqueKey())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:246` — `new String(compoundName[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:249` — `new String(compoundName[length - 1])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Name.java:108` — `new String(buffer)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:635` — `new String(methodDeclaration.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:957` — `new String(typeDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:980` — `new String(annotationTypeMemberDeclaration.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1011` — `new String(receiver.qualifyingName.getName()[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1035` — `new String(argument.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1420` — `new String(statement.label)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1629` — `new String(component.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1769` — `new String(statement.label)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1810` — `new String(enumConstant.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2138` — `new String(reference.token)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2156` — `new String(reference.token)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2466` — `new String(statement.label)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2509` — `new String(expression.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2561` — `new String(expression.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2633` — `new String(argument.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2680` — `new String(memberValuePair.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3005` — `new String(reference.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3094` — `new String(nameReference.token)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3290` — `new String(expression.source())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3435` — `new String(typeDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3520` — `new String(typeParameter.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3600` — `new String(typeName[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3705` — `new String(tokens[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3727` — `new String(tokens[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3788` — `new String(typeDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3814` — `new String(typeDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4019` — `new String(localDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4067` — `new String(fieldDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4111` — `new String(localDeclaration.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4335` — `new String(name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4391` — `new String(name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4492` — `new String(tokens[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4745` — `new String(tokens[index])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6435` — `new String(typeName[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6442` — `new String(typeName[1])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6463` — `new String(typeName[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6492` — `new String(typeName[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6499` — `new String(typeName[1])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6520` — `new String(typeName[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6556` — `new String(singleTypeReference.token)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationBinding.java:170` — `new String(this.binding.computeUniqueKey(recipientKey.toCharArray()))`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationBinding.java:219` — `new String(this.binding.getAnnotationType().sourceName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DefaultValuePairBinding.java:47` — `new String(this.method.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NameEnvironmentWithProgress.java:59` — `new String(CharOperation.concatWith(packageName,'/'))`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NameEnvironmentWithProgress.java:60` — `new String(CharOperation.concatWith(packageName,typeName,'/'))`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredPackageBinding.java:125` — `new String(compoundName[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredPackageBinding.java:128` — `new String(compoundName[length - 1])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredTypeBinding.java:211` — `new String(referenceBinding.compoundName[referenceBinding.compoundName.length...`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleBinding.java:91` — `new String(tmp)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleBinding.java:136` — `new String(k)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:173` — `new String(dotSeparated)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:548` — `new String(this.binding.sourceName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:577` — `new String(this.binding.computeUniqueKey())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:658` — `new String(typeVariableBinding.sourceName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:698` — `new String(((IntersectionTypeBinding18)this.binding).getIntersectingTypes()[0...`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:703` — `new String(baseTypeBinding.simpleName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:708` — `new String(this.binding.sourceName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:776` — `new String(typeVariableBinding.sourceName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:823` — `new String(baseTypeBinding.simpleName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:170` — `new String(this.binding.computeUniqueKey())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:194` — `new String(this.binding.name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:306` — `new String(typeSig)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTParser.java:1346` — `new String(sourceUnit.getFileName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTParser.java:1371` — `new String(fileName)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:110` — `new String(name)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:159` — `new String(this.identifierStack[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:188` — `new String(this.identifierStack[length])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:536` — `new String(this.identifierStack[pos + i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:606` — `new String(this.identifierStack[i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:653` — `new String(this.identifierStack[pos + i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:655` — `new String(this.identifierStack[pos + i])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1070` — `new String(this.identifierStack[idIndex])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1079` — `new String(this.identifierStack[0])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1088` — `new String(this.identifierStack[2])`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TextBlock.java:254` — `new String(CharOperation.subarray(escaped,start,len - 3))`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:259` — `new String(sourceUnit.getFileName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:1135` — `new String(source.getFileName())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodBinding.java:124` — `new String(this.binding.selector)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodBinding.java:382` — `new String(this.binding.computeUniqueKey())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MemberValuePairBinding.java:141` — `new String(membername)`

### eclipse.jdt.ui
#### Rule: `modernize-java11` → `unnamed`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/JarImportWizardPage.java:345` — `"".equals(path)` → `path != null && path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/RefactoringLocationControl.java:70` — `"".equals(key)` → `key != null && key.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/RefactoringLocationControl.java:137` — `"".equals(text)` → `text != null && text.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UseStringIsBlankCleanUp.java:133` — `"".equals(arguments.get(0).resolveConstantExpressionValue())` → `arguments.get(0).resolveConstantExpressionValue() != null && arguments.get(0)...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UseStringIsBlankCleanUp.java:144` — `"".equals(expression.resolveConstantExpressionValue())` → `expression.resolveConstantExpressionValue() != null && expression.resolveCons...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:306` — `"".equals(locale.toString())` → `locale.toString() != null && locale.toString().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/BuildPathsBlock.java:655` — `"".equals(text)` → `text != null && text.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/dialogfields/DialogField.java:158` — `"".equals(fLabelText)` → `fLabelText != null && fLabelText.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarPackageReader.java:185` — `"".equals(value)` → `value != null && value.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarPackageReader.java:199` — `"".equals(value)` → `value != null && value.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarRefactoringDialog.java:115` — `"".equals(project)` → `project != null && project.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/model/JavaModelLabelProvider.java:124` — `"".equals(text)` → `text != null && text.isEmpty()`

#### Rule: `performance` → `Unnecessary String constructor`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/java/CompletionProposalCollector.java:714` — `new String(declarationKey)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/JavaSourceViewerConfiguration.java:610` — `new String(spaces)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/JavaSourceViewerConfiguration.java:653` — `new String(spaceChars)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/JavaMethodCompletionProposal.java:417` — `new String(fProposal.getName())`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:112` — `new String(types[i])`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:382` — `new String(parameterNames[i])`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:407` — `new String(Signature.toCharArray(types[i]))`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/SmartSemicolonAutoEditStrategy.java:229` — `new String(new char[]{' ',character})`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/SmartSemicolonAutoEditStrategy.java:233` — `new String(new char[]{character})`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/OverrideCompletionProposal.java:109` — `new String(content)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:380` — `new String(characters)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:396` — `new String(characters)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:415` — `new String(characters)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:430` — `new String(characters)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/ClassPathDetector.java:203` — `new String(sourceAttribute.getSourceFileName())`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarFileExportOperation.java:864` — `new String(sourceAttribute.getSourceFileName())`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:351` — `new String(text)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:498` — `new String(text)`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:530` — `new String(contents)`

### eclipse.platform.ui
#### Rule: `modernize-java11` → `unnamed`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementCategory.java:58` — `"".equals(classString)` → `classString != null && classString.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/DynamicHelpAction.java:61` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/HelpSearchAction.java:63` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/HelpContentsAction.java:63` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/Policy.java:186` — `"".equals(DEBUG_HANDLERS_VERBOSE_COMMAND_ID)` → `DEBUG_HANDLERS_VERBOSE_COMMAND_ID != null && DEBUG_HANDLERS_VERBOSE_COMMAND_I...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/ExtensionActivityRegistry.java:181` — `"".equals(store.getDefaultString(preferenceKey))` → `store.getDefaultString(preferenceKey) != null && store.getDefaultString(prefe...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ActivityPersistanceHelper.java:213` — `"".equals(store.getDefaultString(preferenceKey))` → `store.getDefaultString(preferenceKey) != null && store.getDefaultString(prefe...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyDialog.java:69` — `"".equals(name)` → `name != null && name.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/EditorHistory.java:135` — `"".equals(item.getName())` → `item.getName() != null && item.getName().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/EditorHistory.java:135` — `"".equals(item.getToolTipText())` → `item.getToolTipText() != null && item.getToolTipText().isEmpty()`

#### Rule: `performance` → `Unnecessary String constructor`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/preferences/WorkingCopyPreferences.java:382` — `new String(Base64.encode(value))`

