/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.jdt.internal.ui.fix;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.ILogListener;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.equinox.app.IApplication;
import org.eclipse.equinox.app.IApplicationContext;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.refactoring.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.fix.AbstractCleanUp;
import org.eclipse.jdt.internal.ui.fix.CleanUpRefactoringWizard;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.internal.ui.text.correction.AssistContext;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpContext;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.jdt.ui.cleanup.ICleanUpFix;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposal;
import org.eclipse.jdt.ui.text.java.IProblemLocation;
import org.eclipse.jdt.ui.text.java.correction.CUCorrectionProposal;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardOpenOperation;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.finders.UIThreadRunnable;
import org.eclipse.swtbot.swt.finder.results.Result;
import org.eclipse.swtbot.swt.finder.waits.DefaultCondition;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotButton;
import org.eclipse.swtbot.swt.finder.widgets.SWTBotShell;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.IWorkbenchCommandConstants;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.IHandlerService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.sandbox.jdt.internal.corext.fix.math.MathCleanUpOptions;
import org.sandbox.jdt.internal.corext.fix.math.MathematicalAnalysis;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Nine actual workbench contracts. Assertions always execute on the JUnit thread. */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MathematicalWorkbenchSWTBotTest {
 private static final String TAB="Mathematics (Sandbox)";
 private static final String ENABLE="Enable explicit mathematics cleanup";
 private static final String CONSENT="I explicitly accept new ArithmeticException behavior";
 private static final String UNDERFLOW="Detect tiny-and-inexact underflow (unsupported)";
 private static final String DIAGNOSTICS="Unsupported numerical combinations are diagnosed and left unchanged. Estimates are not benchmark measurements.";
 private static final NullProgressMonitor MONITOR=new NullProgressMonitor();
 private static final Set<String> PASSED=ConcurrentHashMap.newKeySet();
 private static final List<String> ERRORS=new java.util.concurrent.CopyOnWriteArrayList<>();
 private static final ILogListener LOG=(status,plugin)->{if(status.matches(IStatus.ERROR))ERRORS.add(plugin+": "+status);};
 private static final AtomicReference<Throwable> ASYNC_FAILURE=new AtomicReference<>();
 @RegisterExtension static final TestWatcher WATCHER=new TestWatcher() {
  @Override public void testSuccessful(ExtensionContext context) { PASSED.add(context.getRequiredTestMethod().getName()); }
 };
 private SWTWorkbenchBot bot;
 private IJavaProject project;
 private IPackageFragment pack;
 private final List<IProject> temporaryProjects=new ArrayList<>();

 @BeforeAll static void initializeEvidence() throws Exception {
  PASSED.clear();ERRORS.clear();ASYNC_FAILURE.set(null);
  Files.deleteIfExists(probeOutput().resolve("receipt.json"));
  Platform.addLogListener(LOG);
 }
 @BeforeEach void setUp() throws Exception {
  bot=new SWTWorkbenchBot();
  bot.views().stream().filter(view->view.getTitle().equals("Welcome")).forEach(view->view.close());
  project=createProject("MathematicsWorkbenchQualification");temporaryProjects.add(project.getProject());
  pack=project.getPackageFragmentRoot(project.getProject().getFolder("src")).createPackageFragment("example",true,MONITOR);
  persist(project,enabledOptions());
 }
 @AfterEach void tearDown() throws Exception {
  ui(()->{var window=PlatformUI.getWorkbench().getActiveWorkbenchWindow();if(window.getActivePage()!=null)window.getActivePage().closeAllEditors(false);return null;});
  Shell workbench=workbench();
  for(SWTBotShell shell:bot.shells()) if(shell.widget!=workbench && shell.isOpen())shell.close();
  MathematicalAnalysisJob.cancelAll();
  for(IProject resource:temporaryProjects) if(resource.exists())resource.delete(true,true,MONITOR);
  assertNull(ASYNC_FAILURE.get(),()->"Asynchronous dialog failure: "+ASYNC_FAILURE.get());
 }

 @Test @Order(1) void optionsAreIndependentPersistedExplicitAndReachable() throws Exception {
  SWTBotShell preferences=openPreferences();selectCleanup(preferences);
  button(preferences.widget,"New...","New…").click();
  SWTBotShell create=bot.shell("New Profile");create.bot().textWithLabel("Profile name:").setText("Mathematics qualification");
  button(create.widget,"OK").click();SWTBotShell profile=bot.activeShell();profile.bot().tabItem(TAB).activate();size(profile.widget);
  var enabled=profile.bot().checkBox(ENABLE);assertFalse(enabled.isChecked());enabled.select();
  assertEquals("Only BigInteger",profile.bot().comboBoxWithLabel("Numeric type profile:").getText());
  assertEquals("Preserve Java behavior",profile.bot().comboBoxWithLabel("Safety profile:").getText());
  assertFalse(profile.bot().checkBox(CONSENT).isChecked());assertFalse(profile.bot().checkBox(CONSENT).isEnabled());
  Label warning=label(profile.widget,MathCleanUpOptions.CHECKED_WARNING);
  assertTrue(ui(()->((GridData)warning.getLayoutData()).exclude),"A hidden warning must not reserve layout space");
  assertReachable(profile.widget,label(profile.widget,DIAGNOSTICS));
  assertReachable(profile.widget,profile.bot().checkBox(UNDERFLOW).widget);
  assertFalse(profile.bot().checkBox(UNDERFLOW).isEnabled());
  top(profile.widget);capture(profile.widget,"mathematics-preserve-options.png");
  profile.bot().comboBoxWithLabel("Numeric type profile:").setSelection("int and long");
  assertTrue(profile.bot().checkBox("int").isChecked());assertTrue(profile.bot().checkBox("long").isChecked());assertFalse(profile.bot().checkBox("BigInteger").isChecked());
  profile.bot().comboBoxWithLabel("Numeric type profile:").setSelection("float and double");
  assertTrue(profile.bot().checkBox("float").isChecked());assertTrue(profile.bot().checkBox("double").isChecked());
  profile.bot().comboBoxWithLabel("Safety profile:").setSelection("Guard with unchanged original fallback");
  assertFalse(profile.bot().checkBox(CONSENT).isEnabled());
  profile.bot().comboBoxWithLabel("Safety profile:").setSelection("Checked: throw ArithmeticException");
  assertFalse(button(profile.widget,"OK").isEnabled());
  button(profile.widget,"Select All").click();assertFalse(profile.bot().checkBox(CONSENT).isChecked(),"Select All must not supply checked consent");
  profile.bot().checkBox(CONSENT).select();profile.bot().comboBoxWithLabel("Optimization goal:").setSelection("Readability");
  assertTrue(button(profile.widget,"OK").isEnabled());assertReachable(profile.widget,warning);
  assertFalse(ui(()->((GridData)warning.getLayoutData()).exclude));
  profile.bot().textWithLabel("Search work budget:").setText("0");
  profile.bot().comboBoxWithLabel("Numeric type profile:").setSelection("int and long");
  assertEquals("0",profile.bot().textWithLabel("Search work budget:").getText());
  assertFalse(button(profile.widget,"OK").isEnabled(),"Refreshing another option must preserve native field validation");
  profile.bot().textWithLabel("Search work budget:").setText("100000");
  String name=profile.bot().textWithLabel("Profile name:").getText();profile.bot().textWithLabel("Profile name:").setText("");
  profile.bot().comboBoxWithLabel("Numeric type profile:").setSelection("Only BigInteger");
  assertFalse(button(profile.widget,"OK").isEnabled(),"Math validity must not override the host's empty-name error");
  profile.bot().textWithLabel("Profile name:").setText(name);
  top(profile.widget);capture(profile.widget,"mathematics-checked-options.png");
  button(profile.widget,"OK").click();button(preferences.widget,"Apply and Close","OK").click();
  Map<String,String> saved=CleanUpPreferenceUtil.loadOptions(InstanceScope.INSTANCE);
  assertEquals("CHECKED_THROW",saved.get(MathCleanUpOptions.SAFETY));assertEquals("true",saved.get(MathCleanUpOptions.CHECKED_OPT_IN));
  preferences=openPreferences();selectCleanup(preferences);button(preferences.widget,"Edit...","Edit…").click();
  profile=bot.activeShell();profile.bot().tabItem(TAB).activate();assertTrue(profile.bot().checkBox(CONSENT).isChecked());
  button(profile.widget,"Cancel").click();button(preferences.widget,"Cancel").click();
 }

 @Test @Order(2) void standardFilePreviewAppliesTwoFilesAndUndoesByteExactly() throws Exception {
  ICompilationUnit first=unit("Calculation.java",source("Calculation")),second=unit("Other.java",source("Other"));
  String beforeFirst=first.getSource(),beforeSecond=second.getSource();
  RefactoringCore.getUndoManager().flush();CleanUpRefactoring refactoring=refactoring(first,second);
  AtomicReference<CleanUpRefactoringWizard> model=new AtomicReference<>();
  Display.getDefault().asyncExec(()->{
   try {
    var wizard=new CleanUpRefactoringWizard(refactoring,RefactoringWizard.DIALOG_BASED_USER_INTERFACE|RefactoringWizard.PREVIEW_EXPAND_FIRST_NODE);model.set(wizard);
    new RefactoringWizardOpenOperation(wizard).run(workbenchUnchecked(),"Verify mathematics");
   } catch(Throwable failure) { ASYNC_FAILURE.compareAndSet(null,failure); }
  });
  SWTBotShell dialog=bot.shell("Clean Up");size(dialog.widget);dialog.bot().radio("Use configured profiles").click();
  advanceToPreview(dialog,model);assertTrue(dialog.bot().tree().getAllItems().length>0);
  capture(dialog.widget,"mathematics-file-preview.png");button(dialog.widget,"OK").click();
  await(()->!dialog.isOpen(),"Cleanup wizard closes after application");
  assertNotEquals(beforeFirst,first.getSource());assertNotEquals(beforeSecond,second.getSource());
  assertEquals("17",project.getOption(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM,true));
  assertTrue(RefactoringCore.getUndoManager().anythingToUndo());RefactoringCore.getUndoManager().performUndo(null,MONITOR);
  assertEquals(beforeFirst,first.getSource());assertEquals(beforeSecond,second.getSource());
 }

 @Test @Order(3) void sharedCleanupInstancesKeepDistinctProjectProfiles() throws Exception {
  ICompilationUnit first=unit("Calculation.java",source("Calculation"));
  IJavaProject secondProject=createProject("MathematicsOtherProject");temporaryProjects.add(secondProject.getProject());
  var otherPackage=secondProject.getPackageFragmentRoot(secondProject.getProject().getFolder("src")).createPackageFragment("example",true,MONITOR);
  ICompilationUnit second=otherPackage.createCompilationUnit("Other.java",source("Other"),true,MONITOR);
  Map<String,String> otherOptions=new HashMap<>(enabledOptions());otherOptions.put(MathCleanUpOptions.WORK_BUDGET,"50000");persist(secondProject,otherOptions);
  assertNotEquals(CleanUpPreferenceUtil.loadOptions(new ProjectScope(project.getProject())).get(MathCleanUpOptions.WORK_BUDGET),
    CleanUpPreferenceUtil.loadOptions(new ProjectScope(secondProject.getProject())).get(MathCleanUpOptions.WORK_BUDGET));
  String beforeFirst=first.getSource(),beforeSecond=second.getSource();Change change=calculate(refactoring(first,second));
  change.initializeValidationData(MONITOR);assertFalse(change.isValid(MONITOR).hasFatalError());Change undo=change.perform(MONITOR);
  assertNotEquals(beforeFirst,first.getSource());assertNotEquals(beforeSecond,second.getSource());
  undo.perform(MONITOR);assertEquals(beforeFirst,first.getSource());assertEquals(beforeSecond,second.getSource());
 }

 @Test @Order(4) void aggregateGroupSelectionRetainsGuardsAndChangedPhasesAreSkipped() throws Exception {
  ICompilationUnit unit=unit("Calculation.java",source("Calculation"));String before=unit.getSource();
  MathematicalCleanUpCore cleanup=new MathematicalCleanUpCore(enabledOptions());cleanup.checkPreConditions(project,new ICompilationUnit[]{unit},MONITOR);
  var aggregate=CleanUpRefactoring.calculateChange(new CleanUpContext(unit,parse(unit)),new ICleanUp[]{cleanup,new AbstractCleanUp(){
   @Override public ICleanUpFix createFix(CleanUpContext context) { return monitor->{var change=new CompilationUnitChange("Header",unit);var edit=new InsertEdit(0,"// header\n");change.setEdit(edit);change.addTextEditGroup(new TextEditGroup("Header",edit));return change;}; }
  }},new ArrayList<>(),new HashSet<>());
  assertNotNull(aggregate);var groups=aggregate.getTextEditChangeGroups();assertEquals(2,groups.length);
  var math=Arrays.stream(groups).filter(group->group.getName().contains("mathematics")).findFirst().orElseThrow();
  math.setEnabled(false);assertEquals("// header\n"+before,aggregate.getPreviewContent(MONITOR));math.setEnabled(true);
  assertNotEquals("// header\n"+before,aggregate.getPreviewContent(MONITOR));
  var preferences=new ProjectScope(project.getProject()).getNode(JavaUI.ID_PLUGIN);preferences.put(MathCleanUpOptions.WORK_BUDGET,"99999");
  assertThrows(Exception.class,()->aggregate.getPreviewContent(MONITOR));assertEquals(before,unit.getSource());
  persist(project,enabledOptions());cleanup=new MathematicalCleanUpCore(enabledOptions());cleanup.checkPreConditions(project,new ICompilationUnit[]{unit},MONITOR);
  ICompilationUnit working=unit.getWorkingCopy(MONITOR);
  try {
   working.getBuffer().setContents("// earlier phase\n"+before);
   assertNull(cleanup.createFix(new CleanUpContext(working,parse(working))));
   assertTrue(cleanup.checkPostConditions(MONITOR).toString().contains("MATHEMATICS_PHASE_CHANGED"));
  } finally { working.discardWorkingCopy(); }
 }

 @Test @Order(5) void staleSourceCompilerClasspathAndPersistedProfileCannotApply() throws Exception {
  ICompilationUnit unit=unit("Calculation.java",source("Calculation"));String before=unit.getSource();
  MathematicalChange change=change(unit);unit.getBuffer().setContents(before+"\n");unit.save(MONITOR,true);
  assertFalse(change.isCurrent());MathematicalChange staleSource=change;assertThrows(Exception.class,()->staleSource.perform(MONITOR));assertEquals(before+"\n",unit.getSource());
  unit.getBuffer().setContents(before);unit.save(MONITOR,true);change=change(unit);
  project.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.ERROR);assertFalse(change.isCurrent());project.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.IGNORE);
  change=change(unit);IClasspathEntry[] old=project.getRawClasspath();var extra=project.getProject().getFolder("extra");extra.create(true,true,MONITOR);
  List<IClasspathEntry> entries=new ArrayList<>(Arrays.asList(old));entries.add(JavaCore.newSourceEntry(extra.getFullPath()));project.setRawClasspath(entries.toArray(IClasspathEntry[]::new),MONITOR);
  assertFalse(change.isCurrent());project.setRawClasspath(old,MONITOR);
  change=change(unit);new ProjectScope(project.getProject()).getNode(JavaUI.ID_PLUGIN).put(MathCleanUpOptions.MAX_STATES,"1999");assertFalse(change.isCurrent());
  assertEquals(before,unit.getSource());
 }

 @Test @Order(6) void asynchronousAssistCachesOnlyTheMatchingSourceAndEnvironment() throws Exception {
  ICompilationUnit unit=unit("Calculation.java",source("Calculation"));String source=unit.getSource();
  MathematicalQuickAssist assist=new MathematicalQuickAssist();AssistContext context=new AssistContext(unit,source.indexOf("BigInteger sum"),80);
  assertEquals(0,ui(()->assist.getAssists(context,new IProblemLocation[0])).length);
  AtomicReference<IJavaCompletionProposal[]> result=new AtomicReference<>();
  await(()->{try {var proposals=ui(()->assist.getAssists(context,new IProblemLocation[0]));result.set(proposals);return proposals.length==1;}catch(RuntimeException failure){throw failure;}},"Verified assist result becomes available");
  assertTrue(result.get()[0] instanceof CUCorrectionProposal);CUCorrectionProposal proposal=(CUCorrectionProposal)result.get()[0];
  assertNotEquals(source,proposal.getPreviewContent());assertEquals(source,unit.getSource());
  project.setOption(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.WARNING);
  assertEquals(0,ui(()->assist.getAssists(context,new IProblemLocation[0])).length);
  assertFalse(((MathematicalChange)proposal.getTextChange()).isCurrent());
 }

 @Test @Order(7) void queuedAssistCancellationCanBeRetriedWhileTheUiRemainsResponsive() throws Exception {
  // Runs the real queue lifecycle probe inside the native workbench too.
  new MathematicalQuickAssistTest().queuedCancellationRemovesPendingEntryAndAllowsTheSameSelectionAgain();
  assertTrue(ui(()->!workbenchUnchecked().isDisposed()));
 }

 @Test @Order(8) void progressDialogCancelsTheSharedAnalysisWorker() throws Exception {
  ICompilationUnit unit=unit("Calculation.java",source("Calculation"));String before=unit.getSource();
  CountDownLatch started=new CountDownLatch(1),finished=new CountDownLatch(1);AtomicBoolean cancelled=new AtomicBoolean();
  Display.getDefault().asyncExec(()->{
   try {
    new ProgressMonitorDialog(workbenchUnchecked()).run(true,true,progress->{
     try {
      MathematicalAnalysisJob.runAndWait(worker->{
       started.countDown();long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
       try {
        while(!worker.isCanceled()) {
         MathematicalAnalysis.analyze(parse(unit),before,MathCleanUpOptions.parse(enabledOptions(),17),worker,-1,0);
         if(System.nanoTime()>deadline)throw new IllegalStateException("Cancellation was not delivered");
        }
       } catch(Exception failure) {throw new IllegalStateException(failure);}
       return null;
      },progress);
     } catch(org.eclipse.core.runtime.OperationCanceledException expected) { cancelled.set(true); }
    });
   } catch(Throwable failure) { ASYNC_FAILURE.compareAndSet(null,failure); } finally { finished.countDown(); }
  });
  assertTrue(started.await(20,TimeUnit.SECONDS));button(bot.activeShell().widget,"Cancel").click();
  assertTrue(finished.await(20,TimeUnit.SECONDS));assertTrue(cancelled.get());assertEquals(before,unit.getSource());
 }

 @Test @Order(9) void registeredApplicationProducesReadOnlyBoundEvidence() throws Exception {
  ICompilationUnit unit=unit("Calculation.java",source("Calculation"));String before=unit.getSource();
  Path directory=Files.createTempDirectory("mathematics-application-");Path config=directory.resolve("options.properties"),report=directory.resolve("report.json");
  writeConfiguration(config,enabledOptions());
  var extension=Platform.getExtensionRegistry().getExtension("sandbox_math_cleanup.analysis");assertNotNull(extension);
  var run=extension.getConfigurationElements()[0].getChildren("run")[0];IApplication application=(IApplication)run.createExecutableExtension("class");
  String[] args={"--project",project.getElementName(),"--config",config.toString(),"--report",report.toString()};
  IApplicationContext context=(IApplicationContext)Proxy.newProxyInstance(IApplicationContext.class.getClassLoader(),new Class<?>[]{IApplicationContext.class},
    (proxy,method,parameters)->method.getName().equals("getArguments")?Map.of(IApplicationContext.APPLICATION_ARGS,args):null);
  assertEquals(IApplication.EXIT_OK,application.start(context));assertEquals(before,unit.getSource());
  var json=new ObjectMapper().readTree(Files.readString(report));assertEquals("analysis",json.path("mode").asText());assertEquals(9,json.path("requestedOptions").size());
  var file=json.path("files").get(0);assertFalse(file.path("applied").asBoolean());assertTrue(file.path("changes").size()>0);assertTrue(file.path("evidence").size()>0);
  assertEquals(MathematicalEnvironment.digest(before),file.path("sourceSha256").asText());
  assertEquals(MathematicalEnvironment.digest(file.path("replacement").asText()),file.path("afterSha256").asText());
 }

 @AfterAll static void retainSuccessfulProbe() throws Exception {
  try {
   Set<String> expected=new HashSet<>();for(var method:MathematicalWorkbenchSWTBotTest.class.getDeclaredMethods())if(method.isAnnotationPresent(Test.class))expected.add(method.getName());
   assertEquals(9,expected.size());assertEquals(expected,PASSED,"A partial test selection must not export a successful native receipt");
   assertTrue(ERRORS.isEmpty(),()->"Workbench logged errors: "+ERRORS);
   if(!Boolean.getBoolean("sandbox.math.retainHeadlessProbe"))return;
   IJavaProject project=createProject("MathematicsHeadlessQualification");
   var pack=project.getPackageFragmentRoot(project.getProject().getFolder("src")).createPackageFragment("example",true,MONITOR);
   String source="package example;\npublic class Calculation {\n    public int compute(int a, int b) {\n        int sum = a + b;\n        int result = sum + 0;\n        return result;\n    }\n}\n";
   ICompilationUnit unit=pack.createCompilationUnit("Calculation.java",source,true,MONITOR);
   Map<String,String> values=new HashMap<>(enabledOptions());values.put(MathCleanUpOptions.KINDS,"INT");values.put(MathCleanUpOptions.GOAL,"READABILITY");persist(project,values);
   var analysis=MathematicalAnalysisJob.runAndWait(monitor->{try{return MathematicalAnalysis.analyze(parse(unit),source,MathCleanUpOptions.parse(values,17),monitor,-1,0);}catch(Exception failure){throw new IllegalStateException(failure);}},MONITOR);
   assertTrue(analysis.changed(),()->"Retained fixture must contain a verified candidate: "+analysis.diagnostics());
   ResourcesPlugin.getWorkspace().save(true,MONITOR);assertTrue(ERRORS.isEmpty(),()->"Workspace save logged errors: "+ERRORS);
   MathematicalArtifacts.Receipt artifacts=MathematicalArtifacts.capture(true);
   Path directory=probeOutput();Files.createDirectories(directory);Path configuration=directory.resolve("math.properties");writeConfiguration(configuration,values);
   Map<String,Object> receipt=new java.util.LinkedHashMap<>();receipt.put("schemaVersion",1);receipt.put("status","PASS");receipt.put("successfulTests",9);
   receipt.put("workspace",ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString());receipt.put("project",project.getElementName());
   receipt.put("source",unit.getResource().getLocation().toOSString());receipt.put("sourceSha256",MathematicalEnvironment.digest(source));
   receipt.put("configuration",configuration.toAbsolutePath().toString());receipt.put("configurationSha256",MathematicalEnvironment.digest(Files.readAllBytes(configuration)));receipt.put("targetJava",17);
   receipt.put("sdkSha256",artifacts.sdkSha256());receipt.put("adapterBundleSha256",artifacts.adapterBundleSha256());receipt.put("adapterBundleHashFormat",artifacts.adapterBundleHashFormat());
   receipt.put("verifiedAdapterClasses",artifacts.matchedClasses());new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(directory.resolve("receipt.json").toFile(),receipt);
  } finally { Platform.removeLogListener(LOG); }
 }

 private ICompilationUnit unit(String name,String source) throws Exception {return pack.createCompilationUnit(name,source,true,MONITOR);}
 private static IJavaProject createProject(String name) throws Exception {
  IWorkspace workspace=ResourcesPlugin.getWorkspace();IProject resource=workspace.getRoot().getProject(name);AtomicReference<IJavaProject> created=new AtomicReference<>();
  workspace.run(monitor->{
   if(resource.exists())resource.delete(true,true,monitor);resource.create(monitor);resource.open(monitor);
   IProjectDescription description=resource.getDescription();description.setNatureIds(new String[]{JavaCore.NATURE_ID});resource.setDescription(description,monitor);
   resource.getFolder("src").create(true,true,monitor);resource.getFolder("bin").create(true,true,monitor);IJavaProject project=JavaCore.create(resource);
   project.setRawClasspath(new IClasspathEntry[]{JavaCore.newSourceEntry(resource.getFolder("src").getFullPath()),JavaRuntime.getDefaultJREContainerEntry()},resource.getFolder("bin").getFullPath(),monitor);
   Map<String,String> options=new HashMap<>(project.getOptions(true));JavaCore.setComplianceOptions("17",options);options.put(JavaCore.COMPILER_PB_UNUSED_LOCAL,JavaCore.IGNORE);project.setOptions(options);created.set(project);
  },workspace.getRoot(),IWorkspace.AVOID_UPDATE,MONITOR);
  return created.get();
 }
 private static Map<String,String> enabledOptions() {Map<String,String> values=new HashMap<>(MathCleanUpOptions.defaults(17).toMap());values.put(MathCleanUpOptions.CLEANUP,"true");return values;}
 private static void persist(IJavaProject project,Map<String,String> math) throws Exception {
  var preferences=new ProjectScope(project.getProject()).getNode(JavaUI.ID_PLUGIN);
  Map<String,String> values=new HashMap<>(JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS).getMap());
  values.replaceAll((key,value)->"true".equals(value)?"false":value);values.putAll(math);values.forEach(preferences::put);
  preferences.put(CleanUpConstants.CLEANUP_PROFILE,"_Mathematics_"+project.getElementName());preferences.putInt(CleanUpConstants.CLEANUP_SETTINGS_VERSION_KEY,new CleanUpProfileVersioner().getCurrentVersion());preferences.flush();
 }
 private static String source(String name) {return "package example;\nimport java.math.BigInteger;\npublic class "+name+" {\n    public int compute(int a, int b) {\n        BigInteger input = BigInteger.valueOf(a * b);\n        BigInteger sum = input.add(BigInteger.ONE);\n        BigInteger result = sum.subtract(BigInteger.ONE);\n        return result.intValue();\n    }\n}\n";}
 private static CompilationUnit parse(ICompilationUnit unit) {ASTParser parser=ASTParser.newParser(AST.getJLSLatest());parser.setSource(unit);parser.setResolveBindings(true);return (CompilationUnit)parser.createAST(MONITOR);}
 private static MathematicalChange change(ICompilationUnit unit) throws Exception {
  var cleanup=new MathematicalCleanUpCore(enabledOptions());assertFalse(cleanup.checkPreConditions(unit.getJavaProject(),new ICompilationUnit[]{unit},MONITOR).hasFatalError());
  var fix=cleanup.createFix(new CleanUpContext(unit,parse(unit)));assertNotNull(fix,()->cleanup.checkPostConditions(MONITOR).toString());return (MathematicalChange)fix.createChange(MONITOR);
 }
 private static CleanUpRefactoring refactoring(ICompilationUnit... units) {
  var refactoring=new CleanUpRefactoring();refactoring.setUseOptionsFromProfile(true);for(ICompilationUnit unit:units)refactoring.addCompilationUnit(unit);
  for(ICleanUp cleanup:JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps(Set.of("org.sandbox.jdt.ui.cleanup.mathematics")))refactoring.addCleanUp(cleanup);
  assertEquals(1,refactoring.getCleanUps().length);return refactoring;
 }
 private static Change calculate(CleanUpRefactoring refactoring) throws Exception {
  var initial=refactoring.checkInitialConditions(MONITOR);assertFalse(initial.hasFatalError(),initial.toString());
  var status=refactoring.checkFinalConditions(MONITOR);assertFalse(status.hasFatalError(),status.toString());return refactoring.createChange(MONITOR);
 }
 private SWTBotShell openPreferences() {
  Display.getDefault().asyncExec(()->{try{PlatformUI.getWorkbench().getService(IHandlerService.class).executeCommand(IWorkbenchCommandConstants.WINDOW_PREFERENCES,null);}catch(Throwable failure){ASYNC_FAILURE.compareAndSet(null,failure);}});
  return bot.shell("Preferences");
 }
 private static void selectCleanup(SWTBotShell preferences) {preferences.bot().tree().getTreeItem("Java").expand().getNode("Code Style").expand().getNode("Clean Up").select();}
 private void advanceToPreview(SWTBotShell dialog,AtomicReference<CleanUpRefactoringWizard> model) {
  for(int count=0;count<4;count++) {
   IWizardPage before=ui(()->model.get().getContainer().getCurrentPage());
   if(before!=null && before.getClass().getSimpleName().contains("Preview"))return;
   button(dialog.widget,"Preview >","Next >").click();
   await(()->ui(()->{IWizardPage page=model.get().getContainer().getCurrentPage();return page!=null && page!=before && page.getControl()!=null && page.getControl().isVisible();}),"Wizard completes one page transition before the next click");
  }
  fail("The normal cleanup wizard did not reach its file preview");
 }
 private static SWTBotButton button(Shell shell,String... labels) {
  Button found=ui(()->controls(shell).stream().filter(Button.class::isInstance).map(Button.class::cast)
    .filter(control->control.isVisible() && Arrays.asList(labels).contains(control.getText().replace("&",""))).findFirst().orElseThrow(()->new IllegalStateException("Missing button "+Arrays.toString(labels))));
  return new SWTBotButton(found);
 }
 private static Label label(Shell shell,String text) {return ui(()->controls(shell).stream().filter(Label.class::isInstance).map(Label.class::cast).filter(item->item.getText().equals(text)).findFirst().orElseThrow());}
 private static List<Control> controls(Composite parent) {List<Control> result=new ArrayList<>();for(Control child:parent.getChildren()){result.add(child);if(child instanceof Composite composite)result.addAll(controls(composite));}return result;}
 private static void size(Shell shell) {ui(()->{Rectangle trim=shell.computeTrim(0,0,1280,900);shell.setSize(trim.width,trim.height);shell.layout(true,true);return null;});}
 private static void top(Shell shell) {ui(()->{controls(shell).stream().filter(ScrolledComposite.class::isInstance).map(ScrolledComposite.class::cast).forEach(scroll->scroll.setOrigin(0,0));return null;});}
 private static void assertReachable(Shell shell,Control control) {
  ui(()->{for(Composite parent=control.getParent();parent!=null && parent!=shell;parent=parent.getParent())if(parent instanceof ScrolledComposite scroll)scroll.showControl(control);return null;});
  String clipped=ui(()->{
   if(!control.isVisible() || control.getSize().x<=0 || control.getSize().y<=0)return "control is hidden or empty";
   var point=control.toDisplay(0,0);Rectangle area=new Rectangle(point.x,point.y,control.getSize().x,control.getSize().y);
   for(Composite parent=control.getParent();parent!=null;parent=parent.getParent()) {
    Rectangle client=parent.getClientArea();var origin=parent.toDisplay(client.x,client.y);Rectangle visible=new Rectangle(origin.x,origin.y,client.width,client.height);
    if(!visible.contains(area.x,area.y)||!visible.contains(area.x+area.width-1,area.y+area.height-1))return parent.getClass().getSimpleName()+" clips "+area+" in "+visible;
    if(parent==shell)break;
   }
   return "";
  });
  assertEquals("",clipped,"The normal dialog must let users reach the complete control");
 }
 private static byte[] screenshot(Shell shell) {return ui(()->{
  Rectangle client=shell.getClientArea();Image image=new Image(shell.getDisplay(),client.width,client.height);GC gc=new GC(shell);
  try {gc.copyArea(image,client.x,client.y);ImageLoader loader=new ImageLoader();loader.data=new org.eclipse.swt.graphics.ImageData[]{image.getImageData()};ByteArrayOutputStream out=new ByteArrayOutputStream();loader.save(out,SWT.IMAGE_PNG);return out.toByteArray();}
  finally {gc.dispose();image.dispose();}
 });}
 private void capture(Shell shell,String name) throws Exception {
  AtomicReference<byte[]> previous=new AtomicReference<>(),stable=new AtomicReference<>();
  await(()->{byte[] current=screenshot(shell),old=previous.getAndSet(current);if(old!=null && Arrays.equals(old,current)){stable.set(current);return true;}return false;},"Screenshot is visually stable");
  Path directory=Path.of(System.getProperty("sandbox.math.screenshot.output","target/screenshots"));Files.createDirectories(directory);Files.write(directory.resolve(name),stable.get());
 }
 private void await(BooleanSupplier condition,String description) {bot.waitUntil(new DefaultCondition(){@Override public boolean test(){return condition.getAsBoolean();}@Override public String getFailureMessage(){return description;}},30000,75);}
 private static Shell workbench() {return ui(MathematicalWorkbenchSWTBotTest::workbenchUnchecked);}
 private static Shell workbenchUnchecked() {return PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();}
 private record Outcome<T>(T value,Throwable failure) { }
 private static <T>T ui(Callable<T> operation) {
  Outcome<T> result=UIThreadRunnable.syncExec(Display.getDefault(),new Result<Outcome<T>>(){@Override public Outcome<T> run(){try{return new Outcome<>(operation.call(),null);}catch(Throwable failure){return new Outcome<>(null,failure);}}});
  if(result.failure() instanceof Error error)throw error;
  if(result.failure()!=null)throw new IllegalStateException(result.failure());return result.value();
 }
 private static Path probeOutput() {return Path.of(System.getProperty("sandbox.math.headless.probe.output","target/headless-probe"));}
 private static void writeConfiguration(Path file,Map<String,String> values) throws Exception {Properties properties=new Properties();properties.putAll(values);StringWriter out=new StringWriter();properties.store(out,"Explicit mathematics qualification options");Files.writeString(file,out.toString(),StandardCharsets.UTF_8);}
}
