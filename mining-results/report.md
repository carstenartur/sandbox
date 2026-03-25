# Refactoring Mining Report — 2026-03-25

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 0 | 0 |
| eclipse.jdt.ui | 1468 | 12 | 1 |
| eclipse.platform.ui | 1144 | 10 | 1 |
| eclipse.platform ⚠️ | 0 | 0 | 0 |
| eclipse.platform.text ⚠️ | 0 | 0 | 0 |
| eclipse.platform.debug ⚠️ | 0 | 0 | 0 |
| sandbox | 917 | 0 | 0 |

## Details
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


## Errors
The following repositories encountered errors during scanning:

- **eclipse.platform**: `Remote branch 'main' not found in upstream origin`
- **eclipse.platform.text**: `Remote branch 'main' not found in upstream origin`
- **eclipse.platform.debug**: `Remote branch 'main' not found in upstream origin`
