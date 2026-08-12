from pathlib import Path

SOURCE = Path("sandbox_usage_view_test/src/org/sandbox/jdt/ui/helper/views/SandboxHelpScreenshotsSWTBotTest.java")


def replace_once(text: str, old: str, new: str, description: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one {description}, found {count}")
    return text.replace(old, new, 1)


text = SOURCE.read_text(encoding="utf-8")
if "coordinatedIntToEnumPreviewIsAtomic" in text:
    raise SystemExit("The coordinated preview Workbench test is already present")

text = replace_once(
    text,
    "import org.eclipse.swt.widgets.Event;\n",
    "import org.eclipse.swt.widgets.Event;\nimport org.eclipse.swt.widgets.Text;\n",
    "SWT Event import",
)

constants_anchor = '''    private static final String JFACE_IMAGE_DATA_PROVIDER_LABEL =
            "Modernize Image creation for DPI/zoom (ImageDataProvider)";
'''
constants_addition = constants_anchor + '''    private static final String INT_TO_ENUM_MASTER_LABEL = "Convert int constants to enum/switch";
    private static final String INT_TO_ENUM_PROJECT_WIDE_LABEL =
            "Analyze all project source files for coordinated migrations";
    private static final String INT_TO_ENUM_CANDIDATE_FRAGMENT = "nested enum Status";
'''
text = replace_once(text, constants_anchor, constants_addition, "JFace image-provider constant")

test_anchor = '''    @Test
    public void captureCssCleanupPreferences() throws IOException {
'''
test_method = '''    @Test
    public void coordinatedIntToEnumPreviewIsAtomic() throws Exception {
        configureIntToEnumCleanupProfile();
        IFile ownerFile = cleanupPreviewProject.getFile("src/demo/coordinated/StateOwner.java");
        IFile callerFile = cleanupPreviewProject.getFile("src/demo/coordinated/StateCaller.java");
        String ownerBefore = readFile(ownerFile);
        String callerBefore = readFile(callerFile);

        openProjectExplorer();
        SWTBotTreeItem ownerNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.coordinated").expand().getNode("StateOwner.java");
        ownerNode.select();
        SWTBotShell wizard = openCleanUpWizard(ownerNode);
        clickButton(wizard, "Next >", "Next >");
        bot.sleep(800);
        prepareForScreenshot(wizard);

        SWTBotTree previewTree = wizard.bot().tree();
        SWTBotTreeItem candidate = findTreeItemContaining(previewTree, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        assertTrue(candidate != null, "Preview must expose the coordinated Int-to-Enum candidate");
        candidate.select();
        bot.sleep(500);

        assertTrue(candidate.getItems().length == 0,
                "The atomic candidate must be a leaf without per-file or per-edit checkboxes");
        assertTrue(currentPlainText(wizard).contains("Selection is atomic"),
                "The coordinated viewer must explain the atomic selection contract");

        var affectedFiles = wizard.bot().table();
        assertTrue(affectedFiles.rowCount() == 2,
                "The coordinated viewer must list exactly the owner and required caller");
        String affectedLabels = affectedFiles.cell(0, 0) + "\n" + affectedFiles.cell(1, 0);
        assertTrue(affectedLabels.contains("StateOwner.java"),
                "The coordinated viewer must list StateOwner.java");
        assertTrue(affectedLabels.contains("StateCaller.java"),
                "The coordinated viewer must list StateCaller.java");

        assertTrue(candidate.isChecked(), "The coordinated candidate must initially be selected");
        candidate.uncheck();
        assertTrue(!candidate.isChecked(), "The single candidate checkbox must disable the whole migration");
        if (wizard.bot().button("Finish").isEnabled()) {
            clickButtonAsync(wizard, "Finish");
        } else {
            clickButton(wizard, "Cancel");
        }
        waitForShellToClose(wizard, "Clean Up wizard");
        assertTrue(ownerBefore.equals(readFile(ownerFile)),
                "Disabling the candidate must keep StateOwner.java byte-identical");
        assertTrue(callerBefore.equals(readFile(callerFile)),
                "Disabling the candidate must keep StateCaller.java byte-identical");

        ownerNode = projectTree().getTreeItem(CLEANUP_PREVIEW_PROJECT).expand()
                .getNode("src").expand().getNode("demo.coordinated").expand().getNode("StateOwner.java");
        ownerNode.select();
        wizard = openCleanUpWizard(ownerNode);
        clickButton(wizard, "Next >", "Next >");
        bot.sleep(800);
        previewTree = wizard.bot().tree();
        candidate = findTreeItemContaining(previewTree, INT_TO_ENUM_CANDIDATE_FRAGMENT);
        assertTrue(candidate != null && candidate.isChecked(),
                "Reopening the preview must present one selected atomic candidate");
        clickButtonAsync(wizard, "Finish");
        waitForShellToClose(wizard, "Clean Up wizard");

        String ownerAfter = readFile(ownerFile);
        String callerAfter = readFile(callerFile);
        assertTrue(ownerAfter.contains("enum Status"),
                "Applying the candidate must introduce the enum in StateOwner.java");
        assertTrue(callerAfter.contains("Status.PENDING"),
                "Applying the candidate must update the required caller in the same operation");
        assertTrue(!ownerBefore.equals(ownerAfter) && !callerBefore.equals(callerAfter),
                "The selected atomic candidate must modify both required files");

        undoLastCleanup();
        assertTrue(ownerBefore.equals(readFile(ownerFile)),
                "Undo must restore StateOwner.java byte-exactly");
        assertTrue(callerBefore.equals(readFile(callerFile)),
                "Undo must restore StateCaller.java byte-exactly");
    }

''' + test_anchor
text = replace_once(text, test_anchor, test_method, "CSS screenshot test anchor")

configure_anchor = '''    private static void configureJFaceCleanupProfile() {
'''
configure_method = '''    private static void configureIntToEnumCleanupProfile() {
        openPreferences();
        SWTBotShell preferences = bot.shell("Preferences").activate();
        selectPreferencePath(preferences.bot().tree(), "Java", "Code Style", "Clean Up");
        clickButton(preferences, "Edit...", "Edit…");
        SWTBotShell profileDialog = bot.activeShell();
        profileDialog.bot().textWithLabel("Profile name:").setText("Sandbox Coordinated Preview");
        profileDialog.bot().tabItem("Int to Enum (Sandbox)").activate();

        ensureChecked(profileDialog, INT_TO_ENUM_MASTER_LABEL, true);
        ensureChecked(profileDialog, INT_TO_ENUM_PROJECT_WIDE_LABEL, true);

        clickButton(profileDialog, "OK");
        clickButton(preferences, "Apply and Close", "OK");
        waitForShellToClose(preferences, "Preferences");
    }

''' + configure_anchor
text = replace_once(text, configure_anchor, configure_method, "JFace profile configuration method")

plain_text_anchor = '''    private static String currentDiffText(SWTBotShell shell) {
'''
plain_text_method = '''    private static String currentPlainText(SWTBotShell shell) {
        return UIThreadRunnable.syncExec(shell.display, new Result<String>() {
            @Override
            public String run() {
                return String.join("\n---\n", collectText(shell.widget));
            }
        });
    }

''' + plain_text_anchor
text = replace_once(text, plain_text_anchor, plain_text_method, "diff-text helper")

collect_text_anchor = '''    private static void waitForShellToClose(SWTBotShell shell, String description) {
'''
collect_text_method = '''    private static List<String> collectText(Control root) {
        Deque<Control> pending = new ArrayDeque<>();
        pending.add(root);
        List<String> texts = new ArrayList<>();
        while (!pending.isEmpty()) {
            Control control = pending.removeFirst();
            if (control instanceof Text text) {
                texts.add(text.getText());
            }
            if (control instanceof Composite composite) {
                for (Control child : composite.getChildren()) {
                    pending.addLast(child);
                }
            }
        }
        return texts;
    }

''' + collect_text_anchor
text = replace_once(text, collect_text_anchor, collect_text_method, "shell-close helper")

fixture_anchor = '''        createUnit(sourceRoot, "demo.multi", "SorterOnly.java",
                """
                        package demo.multi;
                        import org.eclipse.jface.viewers.ViewerSorter;
                        public class SorterOnly {
                            public ViewerSorter sorter() {
                                return new ViewerSorter();
                            }
                        }
                        """);
'''
fixture_addition = fixture_anchor + '''        createUnit(sourceRoot, "demo.coordinated", "StateOwner.java",
                """
                        package demo.coordinated;

                        public class StateOwner {
                            static final int STATUS_PENDING = 0;
                            static final int STATUS_APPROVED = 1;

                            void process(int status) {
                                if (status == STATUS_PENDING) {
                                    System.out.println("pending");
                                } else if (status == STATUS_APPROVED) {
                                    System.out.println("approved");
                                }
                            }
                        }
                        """);
        createUnit(sourceRoot, "demo.coordinated", "StateCaller.java",
                """
                        package demo.coordinated;

                        public class StateCaller {
                            void run(StateOwner owner) {
                                owner.process(StateOwner.STATUS_PENDING);
                            }
                        }
                        """);
'''
text = replace_once(text, fixture_anchor, fixture_addition, "SorterOnly fixture")

SOURCE.write_text(text, encoding="utf-8")
