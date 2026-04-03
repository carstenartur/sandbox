# Refactoring Mining Report — 2026-03-31

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 85 | 1 |
| eclipse.jdt.ui | 1468 | 31 | 2 |
| eclipse.platform.ui | 1145 | 11 | 2 |
| eclipse.platform | 313 | 0 | 0 |
| eclipse.platform.text | 0 | 0 | 0 |
| eclipse.platform.debug | 0 | 0 | 0 |
| sandbox | 917 | 0 | 0 |

## Details
### eclipse.jdt.core
#### Rule: `performance` → `performance`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/TypeConflictingSimpleNameFinder.java:59` — `new String(simpleTypeName)` → `simpleTypeName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:259` — `new String(sourceUnit.getFileName())` → `sourceUnit.getFileName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:1135` — `new String(source.getFileName())` → `source.getFileName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodBinding.java:124` — `new String(this.binding.selector)` → `this.binding.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodBinding.java:382` — `new String(this.binding.computeUniqueKey())` → `this.binding.computeUniqueKey()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:173` — `new String(dotSeparated)` → `dotSeparated`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:548` — `new String(this.binding.sourceName())` → `this.binding.sourceName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:577` — `new String(this.binding.computeUniqueKey())` → `this.binding.computeUniqueKey()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:658` — `new String(typeVariableBinding.sourceName)` → `typeVariableBinding.sourceName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:698` — `new String(((IntersectionTypeBinding18)this.binding).getIntersectingTypes()[0...` → `((IntersectionTypeBinding18)this.binding).getIntersectingTypes()[0].sourceName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:703` — `new String(baseTypeBinding.simpleName)` → `baseTypeBinding.simpleName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:708` — `new String(this.binding.sourceName())` → `this.binding.sourceName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:776` — `new String(typeVariableBinding.sourceName)` → `typeVariableBinding.sourceName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeBinding.java:823` — `new String(baseTypeBinding.simpleName)` → `baseTypeBinding.simpleName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:170` — `new String(this.binding.computeUniqueKey())` → `this.binding.computeUniqueKey()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:194` — `new String(this.binding.name)` → `this.binding.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableBinding.java:306` — `new String(typeSig)` → `typeSig`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NameEnvironmentWithProgress.java:59` — `new String(CharOperation.concatWith(packageName,'/'))` → `CharOperation.concatWith(packageName,'/')`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NameEnvironmentWithProgress.java:60` — `new String(CharOperation.concatWith(packageName,typeName,'/'))` → `CharOperation.concatWith(packageName,typeName,'/')`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredTypeBinding.java:211` — `new String(referenceBinding.compoundName[referenceBinding.compoundName.length...` → `referenceBinding.compoundName[referenceBinding.compoundName.length - 1]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:110` — `new String(name)` → `name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:159` — `new String(this.identifierStack[0])` → `this.identifierStack[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:188` — `new String(this.identifierStack[length])` → `this.identifierStack[length]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:536` — `new String(this.identifierStack[pos + i])` → `this.identifierStack[pos + i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:606` — `new String(this.identifierStack[i])` → `this.identifierStack[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:653` — `new String(this.identifierStack[pos + i])` → `this.identifierStack[pos + i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:655` — `new String(this.identifierStack[pos + i])` → `this.identifierStack[pos + i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1070` — `new String(this.identifierStack[idIndex])` → `this.identifierStack[idIndex]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1079` — `new String(this.identifierStack[0])` → `this.identifierStack[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DocCommentParser.java:1088` — `new String(this.identifierStack[2])` → `this.identifierStack[2]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:216` — `new String(this.binding.computeUniqueKey())` → `this.binding.computeUniqueKey()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:246` — `new String(compoundName[i])` → `compoundName[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageBinding.java:249` — `new String(compoundName[length - 1])` → `compoundName[length - 1]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:635` — `new String(methodDeclaration.selector)` → `methodDeclaration.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:957` — `new String(typeDeclaration.name)` → `typeDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:980` — `new String(annotationTypeMemberDeclaration.selector)` → `annotationTypeMemberDeclaration.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1011` — `new String(receiver.qualifyingName.getName()[0])` → `receiver.qualifyingName.getName()[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1035` — `new String(argument.name)` → `argument.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1420` — `new String(statement.label)` → `statement.label`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1629` — `new String(component.name)` → `component.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1769` — `new String(statement.label)` → `statement.label`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:1810` — `new String(enumConstant.name)` → `enumConstant.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2138` — `new String(reference.token)` → `reference.token`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2156` — `new String(reference.token)` → `reference.token`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2466` — `new String(statement.label)` → `statement.label`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2509` — `new String(expression.selector)` → `expression.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2561` — `new String(expression.selector)` → `expression.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2633` — `new String(argument.name)` → `argument.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:2680` — `new String(memberValuePair.name)` → `memberValuePair.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3005` — `new String(reference.selector)` → `reference.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3094` — `new String(nameReference.token)` → `nameReference.token`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3290` — `new String(expression.source())` → `expression.source()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3435` — `new String(typeDeclaration.name)` → `typeDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3520` — `new String(typeParameter.name)` → `typeParameter.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3600` — `new String(typeName[0])` → `typeName[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3705` — `new String(tokens[0])` → `tokens[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3727` — `new String(tokens[0])` → `tokens[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3788` — `new String(typeDeclaration.name)` → `typeDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:3814` — `new String(typeDeclaration.name)` → `typeDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4019` — `new String(localDeclaration.name)` → `localDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4067` — `new String(fieldDeclaration.name)` → `fieldDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4111` — `new String(localDeclaration.name)` → `localDeclaration.name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4335` — `new String(name)` → `name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4391` — `new String(name)` → `name`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4492` — `new String(tokens[i])` → `tokens[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:4745` — `new String(tokens[index])` → `tokens[index]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6435` — `new String(typeName[0])` → `typeName[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6442` — `new String(typeName[1])` → `typeName[1]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6463` — `new String(typeName[i])` → `typeName[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6492` — `new String(typeName[0])` → `typeName[0]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6499` — `new String(typeName[1])` → `typeName[1]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6520` — `new String(typeName[i])` → `typeName[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:6556` — `new String(singleTypeReference.token)` → `singleTypeReference.token`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MemberValuePairBinding.java:141` — `new String(membername)` → `membername`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationBinding.java:170` — `new String(this.binding.computeUniqueKey(recipientKey.toCharArray()))` → `this.binding.computeUniqueKey(recipientKey.toCharArray())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationBinding.java:219` — `new String(this.binding.getAnnotationType().sourceName())` → `this.binding.getAnnotationType().sourceName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TextBlock.java:254` — `new String(CharOperation.subarray(escaped,start,len - 3))` → `CharOperation.subarray(escaped,start,len - 3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredPackageBinding.java:125` — `new String(compoundName[i])` → `compoundName[i]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecoveredPackageBinding.java:128` — `new String(compoundName[length - 1])` → `compoundName[length - 1]`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTParser.java:1346` — `new String(sourceUnit.getFileName())` → `sourceUnit.getFileName()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTParser.java:1371` — `new String(fileName)` → `fileName`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleBinding.java:91` — `new String(tmp)` → `tmp`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleBinding.java:136` — `new String(k)` → `k`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DefaultValuePairBinding.java:47` — `new String(this.method.selector)` → `this.method.selector`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Name.java:108` — `new String(buffer)` → `buffer`

### eclipse.jdt.ui
#### Rule: `modernize-java11` → `modernize-java11`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/model/JavaModelLabelProvider.java:124` — `"".equals(text)` → `text != null && text.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:306` — `"".equals(locale.toString())` → `locale.toString() != null && locale.toString().isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/dialogfields/DialogField.java:158` — `"".equals(fLabelText)` → `fLabelText != null && fLabelText.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/BuildPathsBlock.java:655` — `"".equals(text)` → `text != null && text.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/JarImportWizardPage.java:345` — `"".equals(path)` → `path != null && path.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/RefactoringLocationControl.java:70` — `"".equals(key)` → `key != null && key.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarimport/RefactoringLocationControl.java:137` — `"".equals(text)` → `text != null && text.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UseStringIsBlankCleanUp.java:133` — `"".equals(arguments.get(0).resolveConstantExpressionValue())` → `arguments.get(0).resolveConstantExpressionValue() != null && arguments.get(0)...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UseStringIsBlankCleanUp.java:144` — `"".equals(expression.resolveConstantExpressionValue())` → `expression.resolveConstantExpressionValue() != null && expression.resolveCons...`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarPackageReader.java:185` — `"".equals(value)` → `value != null && value.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarPackageReader.java:199` — `"".equals(value)` → `value != null && value.isEmpty()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarRefactoringDialog.java:115` — `"".equals(project)` → `project != null && project.isEmpty()`

#### Rule: `performance` → `performance`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:380` — `new String(characters)` → `characters`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:396` — `new String(characters)` → `characters`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:415` — `new String(characters)` → `characters`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/AbstractSpellDictionary.java:430` — `new String(characters)` → `characters`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/OverrideCompletionProposal.java:109` — `new String(content)` → `content`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:112` — `new String(types[i])` → `types[i]`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:382` — `new String(parameterNames[i])` → `parameterNames[i]`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ParameterGuessingProposal.java:407` — `new String(Signature.toCharArray(types[i]))` → `Signature.toCharArray(types[i])`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/JavaMethodCompletionProposal.java:417` — `new String(fProposal.getName())` → `fProposal.getName()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/SmartSemicolonAutoEditStrategy.java:229` — `new String(new char[]{' ',character})` → `new char[]{' ',character}`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/SmartSemicolonAutoEditStrategy.java:233` — `new String(new char[]{character})` → `new char[]{character}`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:351` — `new String(text)` → `text`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:498` — `new String(text)` → `text`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/DocumentAdapter.java:530` — `new String(contents)` → `contents`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/ClassPathDetector.java:203` — `new String(sourceAttribute.getSourceFileName())` → `sourceAttribute.getSourceFileName()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/jarpackager/JarFileExportOperation.java:864` — `new String(sourceAttribute.getSourceFileName())` → `sourceAttribute.getSourceFileName()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/JavaSourceViewerConfiguration.java:610` — `new String(spaces)` → `spaces`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/JavaSourceViewerConfiguration.java:653` — `new String(spaceChars)` → `spaceChars`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/text/java/CompletionProposalCollector.java:714` — `new String(declarationKey)` → `declarationKey`

### eclipse.platform.ui
#### Rule: `modernize-java11` → `modernize-java11`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ActivityPersistanceHelper.java:213` — `"".equals(store.getDefaultString(preferenceKey))` → `store.getDefaultString(preferenceKey) != null && store.getDefaultString(prefe...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/ThemeElementCategory.java:58` — `"".equals(classString)` → `classString != null && classString.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/ExtensionActivityRegistry.java:181` — `"".equals(store.getDefaultString(preferenceKey))` → `store.getDefaultString(preferenceKey) != null && store.getDefaultString(prefe...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/EditorHistory.java:135` — `"".equals(item.getName())` → `item.getName() != null && item.getName().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/EditorHistory.java:135` — `"".equals(item.getToolTipText())` → `item.getToolTipText() != null && item.getToolTipText().isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/DynamicHelpAction.java:61` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/HelpContentsAction.java:63` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/HelpSearchAction.java:63` — `"".equals(overrideText)` → `overrideText != null && overrideText.isEmpty()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/misc/Policy.java:186` — `"".equals(DEBUG_HANDLERS_VERBOSE_COMMAND_ID)` → `DEBUG_HANDLERS_VERBOSE_COMMAND_ID != null && DEBUG_HANDLERS_VERBOSE_COMMAND_I...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyDialog.java:69` — `"".equals(name)` → `name != null && name.isEmpty()`

#### Rule: `performance` → `performance`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/preferences/WorkingCopyPreferences.java:382` — `new String(Base64.encode(value))` → `Base64.encode(value)`


<!-- report-hash: 807d0c39b89cd3cc04fcacb636151b425d13e02b2dc6f2f2e46bd9661afd7136 -->
