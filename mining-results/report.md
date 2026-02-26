# Refactoring Mining Report — 2026-02-26

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core ⚠️ | 0 | 0 | 0 |
| eclipse.jdt.ui ⚠️ | 0 | 0 | 0 |
| eclipse.platform.ui | 1144 | 11 | 2 |
| sandbox | 746 | 0 | 0 |

## Details
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


## Errors
The following repositories encountered errors during scanning:

- **eclipse.jdt.core**: `Remote branch 'main' not found in upstream origin`
- **eclipse.jdt.ui**: `Remote branch 'main' not found in upstream origin`
