package org.sandbox.jdt.ui.tests.quickfix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.internal.corext.dom.IASTSharedValues;
import org.eclipse.jdt.internal.corext.fix.CleanUpConstants;
import org.eclipse.jdt.internal.corext.fix.CleanUpPreferenceUtil;
import org.eclipse.jdt.internal.corext.fix.CleanUpRefactoring;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.GroupCategory;
import org.eclipse.ltk.core.refactoring.IUndoManager;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextEditBasedChange;
import org.eclipse.ltk.core.refactoring.TextEditBasedChangeGroup;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

public class EclipseJava11 implements AfterEachCallback, BeforeEachCallback {

	private static final String TESTRESOURCES_RTSTUBS18_JAR = "testresources/rtstubs18.jar";
	private static final String TEST_SETUP_PROJECT = "TestSetupProject";
	public IPackageFragmentRoot fSourceFolder;
	private CustomProfile fProfile;

	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		IJavaProject javaProject= createJavaProject(TEST_SETUP_PROJECT, "bin");
		javaProject.setRawClasspath(getDefaultClasspath(), null);
		Map<String, String> options= javaProject.getOptions(false);
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		javaProject.setOptions(options);
		fSourceFolder= EclipseJava11.addSourceContainer(getProject(TEST_SETUP_PROJECT), "src", new Path[0], new Path[0], null, new IClasspathAttribute[0]);
		Map<String, String> settings= new Hashtable<>();
		fProfile= new ProfileManager.CustomProfile("testProfile", settings, CleanUpProfileVersioner.CURRENT_VERSION, CleanUpProfileVersioner.PROFILE_KIND);
		InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).put(CleanUpConstants.CLEANUP_PROFILE, fProfile.getID());
		InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).put(CleanUpConstants.SAVE_PARTICIPANT_PROFILE, fProfile.getID());
		disableAll();
	}

	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		delete(fSourceFolder);
	}
	
	public IJavaProject getProject(String projectname) {
		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject(projectname));
	}
	
	public IClasspathEntry[] getDefaultClasspath() throws CoreException {
		IPath[] rtJarPath= findRtJar(new Path(TESTRESOURCES_RTSTUBS18_JAR));
		return new IClasspathEntry[] {  JavaCore.newLibraryEntry(rtJarPath[0], rtJarPath[1], rtJarPath[2], true) };
	}
	private void disableAll() throws CoreException {
		Map<String, String> settings= fProfile.getSettings();
		CleanUpOptions options= JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS);
		for (String key : options.getKeys()) {
			settings.put(key, CleanUpOptions.FALSE);
		}
		commitProfile();
	}
	/**
	 * Removes an IJavaElement's resource. Retries if deletion failed (e.g. because the indexer
	 * still locks the file).
	 *
	 * @param elem the element to delete
	 * @throws CoreException if operation failed
	 * @see #ASSERT_NO_MIXED_LINE_DELIMIERS
	 */
	public void delete(final IJavaElement elem) throws CoreException {
		IWorkspaceRunnable runnable= monitor -> {
			if (elem instanceof IJavaProject) {
				IJavaProject jproject= (IJavaProject) elem;
				jproject.setRawClasspath(new IClasspathEntry[0], jproject.getProject().getFullPath(), null);
			}
			delete(elem.getResource());
		};
		ResourcesPlugin.getWorkspace().run(runnable, null);
	}
	private static final int MAX_RETRY= 5;
	private static final int RETRY_DELAY= 1000;
	/**
	 * Removes a resource. Retries if deletion failed (e.g. because the indexer
	 * still locks the file).
	 *
	 * @param resource the resource to delete
	 * @throws CoreException if operation failed
	 */
	public static void delete(IResource resource) throws CoreException {
		for (int i= 0; i < MAX_RETRY; i++) {
			try {
				resource.delete(IResource.FORCE | IResource.ALWAYS_DELETE_PROJECT_CONTENT, null);
				i= MAX_RETRY;
			} catch (CoreException e) {
				if (i == MAX_RETRY - 1) {
					JavaPlugin.log(e);
					throw e;
				}
				try {
					JavaPlugin.log(new IllegalStateException("sleep before retrying JavaProjectHelper.delete() for " + resource.getLocationURI()));
					Thread.sleep(RETRY_DELAY); // give other threads time to close the file
				} catch (InterruptedException e1) {
				}
			}
		}
	}
	/**
	 * @param rtStubsPath the path to the RT stubs
	 * @return a rt.jar (stubs only)
	 * @throws CoreException
	 */
	public IPath[] findRtJar(IPath rtStubsPath) throws CoreException {
//		File rtStubs= getFileInPlugin(rtStubsPath);
		File rtStubs= rtStubsPath.toFile().getAbsoluteFile();
		assertNotNull(rtStubs);
		assertTrue(rtStubs.exists());
		return new IPath[] {
			Path.fromOSString(rtStubs.getPath()),
			null,
			null
		};
	}
	
//	public File getFileInPlugin(IPath path) throws CoreException {
//		try {
//			URL installURL= new URL(
//					getBundle().getEntry("/"), 
//					path.toString());
//			URL localURL= FileLocator.toFileURL(installURL);
//			return new File(localURL.getFile());
//		} catch (IOException e) {
//			throw new CoreException(new Status(IStatus.ERROR, "tests_sandbox_fragment", IStatus.ERROR, e.getMessage(), e));
//		}
//	}
	
	/**
	 * Returns the bundle associated with this plug-in.
	 *
	 * @return the associated bundle
	 * @since 3.0
	 */
	public final Bundle getBundle() {
		ClassLoader cl = getClass().getClassLoader();
		if (cl instanceof BundleReference)
			return ((BundleReference) cl).getBundle();
		return null;
	}
	/**
	 * Creates a IJavaProject.
	 * @param projectName The name of the project
	 * @param binFolderName Name of the output folder
	 * @return Returns the Java project handle
	 * @throws CoreException Project creation failed
	 */
	public static IJavaProject createJavaProject(String projectName, String binFolderName) throws CoreException {
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		IProject project= root.getProject(projectName);
		if (!project.exists()) {
			project.create(null);
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		}

		if (!project.isOpen()) {
			project.open(null);
		}

		IPath outputLocation;
		if (binFolderName != null && binFolderName.length() > 0) {
			IFolder binFolder= project.getFolder(binFolderName);
			if (!binFolder.exists()) {
				CoreUtility.createFolder(binFolder, false, true, null);
			}
			outputLocation= binFolder.getFullPath();
		} else {
			outputLocation= project.getFullPath();
		}

		if (!project.hasNature(JavaCore.NATURE_ID)) {
			addNatureToProject(project, JavaCore.NATURE_ID, null);
		}

		IJavaProject jproject= JavaCore.create(project);

		jproject.setOutputLocation(outputLocation, null);
		jproject.setRawClasspath(new IClasspathEntry[0], null);

		return jproject;
	}
	
	private static void addNatureToProject(IProject proj, String natureId, IProgressMonitor monitor) throws CoreException {
		IProjectDescription description = proj.getDescription();
		String[] prevNatures= description.getNatureIds();
		String[] newNatures= new String[prevNatures.length + 1];
		System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
		newNatures[prevNatures.length]= natureId;
		description.setNatureIds(newNatures);
		proj.setDescription(description, monitor);
	}
	/**
	 * Adds a source container to a IJavaProject.
	 * @param jproject The parent project
	 * @param containerName The name of the new source container
	 * @param inclusionFilters Inclusion filters to set
	 * @param exclusionFilters Exclusion filters to set
	 * @param outputLocation The location where class files are written to, <b>null</b> for project output folder
	 * @param attributes The classpath attributes to set
	 * @return The handle to the new source container
	 * @throws CoreException Creation failed
	 */
	public static IPackageFragmentRoot addSourceContainer(IJavaProject jproject, String containerName, IPath[] inclusionFilters, IPath[] exclusionFilters, String outputLocation, IClasspathAttribute[] attributes) throws CoreException {
		IProject project= jproject.getProject();
		IContainer container;
		if (containerName == null || containerName.length() == 0) {
			container= project;
		} else {
			IFolder folder= project.getFolder(containerName);
			if (!folder.exists()) {
				CoreUtility.createFolder(folder, false, true, null);
			}
			container= folder;
		}
		IPackageFragmentRoot root= jproject.getPackageFragmentRoot(container);

		IPath outputPath= null;
		if (outputLocation != null) {
			IFolder folder= project.getFolder(outputLocation);
			if (!folder.exists()) {
				CoreUtility.createFolder(folder, false, true, null);
			}
			outputPath= folder.getFullPath();
		}
		IClasspathEntry cpe= JavaCore.newSourceEntry(root.getPath(), inclusionFilters, exclusionFilters, outputPath, attributes);
		addToClasspath(jproject, cpe);
		return root;
	}
	
	public static void addToClasspath(IJavaProject jproject, IClasspathEntry cpe) throws JavaModelException {
		IClasspathEntry[] oldEntries= jproject.getRawClasspath();
		for (IClasspathEntry oldEntry : oldEntries) {
			if (oldEntry.equals(cpe)) {
				return;
			}
		}
		int nEntries= oldEntries.length;
		IClasspathEntry[] newEntries= new IClasspathEntry[nEntries + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, nEntries);
		newEntries[nEntries]= cpe;
		jproject.setRawClasspath(newEntries, null);
	}

	protected RefactoringStatus assertRefactoringResultAsExpected(ICompilationUnit[] cus, String[] expected, Set<String> setOfExpectedGroupCategories) throws CoreException {
		RefactoringStatus status= performRefactoring(cus, setOfExpectedGroupCategories);

		String[] previews= new String[cus.length];
		for (int i= 0; i < cus.length; i++) {
			ICompilationUnit cu= cus[i];
			previews[i]= cu.getBuffer().getContents();
		}

		assertEqualStringsIgnoreOrder(previews, expected);

		return status;
	}
	
	protected RefactoringStatus assertRefactoringHasNoChange(ICompilationUnit[] cus) throws CoreException {
		for (ICompilationUnit cu : cus) {
			assertNoCompilationError(cu);
		}
		return assertRefactoringHasNoChangeEventWithError(cus);
	}
	
	protected RefactoringStatus assertRefactoringHasNoChangeEventWithError(ICompilationUnit[] cus) throws CoreException {
		String[] expected= new String[cus.length];

		for (int i= 0; i < cus.length; i++) {
			expected[i]= cus[i].getBuffer().getContents();
		}

		return assertRefactoringResultAsExpected(cus, expected, null);
	}
	
	protected CompilationUnit assertNoCompilationError(ICompilationUnit cu) {
		ASTParser parser= ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		CompilationUnit root= (CompilationUnit) parser.createAST(null);
		IProblem[] problems= root.getProblems();
		boolean hasProblems= false;
		for (IProblem prob : problems) {
			if (!prob.isWarning() && !prob.isInfo()) {
				hasProblems= true;
				break;
			}
		}
		if (hasProblems) {
			StringBuilder builder= new StringBuilder();
			builder.append(cu.getElementName() + " has compilation problems: \n");
			for (IProblem prob : problems) {
				builder.append(prob.getMessage()).append('\n');
			}
			fail(builder.toString());
		}
		return root;
	}
	
	public static void assertEqualStringsIgnoreOrder(String[] actuals, String[] expecteds) {
		ArrayList<String> list1= new ArrayList<>(Arrays.asList(actuals));
		ArrayList<String> list2= new ArrayList<>(Arrays.asList(expecteds));
		for (int i= list1.size() - 1; i >= 0; i--) {
			if (list2.remove(list1.get(i))) {
				list1.remove(i);
			}
		}
		int n1= list1.size();
		int n2= list2.size();
		if (n1 + n2 > 0) {
			if (n1 == 1 && n2 == 1) {
				assertEquals(list2.get(0), list1.get(0));
			}
			StringBuilder buf= new StringBuilder();
			for (int i= 0; i < n1; i++) {
				String s1= list1.get(i);
				if (s1 != null) {
					buf.append(s1);
					buf.append("\n");
				}
			}
			String actual= buf.toString();
			buf= new StringBuilder();
			for (int i= 0; i < n2; i++) {
				String s2= list2.get(i);
				if (s2 != null) {
					buf.append(s2);
					buf.append("\n");
				}
			}
			String expected= buf.toString();

			assertEquals(expected, actual);
		}
	}
	
	protected final RefactoringStatus performRefactoring(ICompilationUnit[] cus, Set<String> setOfExpectedGroupCategories) throws CoreException {
		final CleanUpRefactoring ref= new CleanUpRefactoring();
		ref.setUseOptionsFromProfile(true);
		ICleanUp[] cleanUps= JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps();

		return performRefactoring(ref, cus, cleanUps, setOfExpectedGroupCategories);
	}
	
	protected RefactoringStatus performRefactoring(final CleanUpRefactoring ref, ICompilationUnit[] cus, ICleanUp[] cleanUps, Set<String> setOfExpectedGroupCategories) throws CoreException {
		for (ICompilationUnit cu : cus) {
			ref.addCompilationUnit(cu);
		}
		for (ICleanUp cleanUp : cleanUps) {
			ref.addCleanUp(cleanUp);
		}

		IUndoManager undoManager= RefactoringCore.getUndoManager();
		undoManager.flush();
		final CreateChangeOperation create= new CreateChangeOperation(
			new CheckConditionsOperation(ref, CheckConditionsOperation.ALL_CONDITIONS),
			RefactoringStatus.FATAL);

		final PerformChangeOperation perform= new PerformChangeOperation(create);
		perform.setUndoManager(undoManager, ref.getName());

		IWorkspace workspace= ResourcesPlugin.getWorkspace();
		workspace.run(perform, new NullProgressMonitor());
		
		RefactoringStatus status= create.getConditionCheckingStatus();
		if (status.hasFatalError()) {
			throw new CoreException(new StatusInfo(status.getSeverity(), status.getMessageMatchingSeverity(status.getSeverity())));
		}

		assertTrue(perform.changeExecuted(),"Change wasn't executed");

		Change undo= perform.getUndoChange();
		assertNotNull(undo, "Undo doesn't exist");
		assertTrue(undoManager.anythingToUndo(),"Undo manager is empty");

		if (setOfExpectedGroupCategories != null) {
			Change change= create.getChange();
			Set<GroupCategory> actualCategories= new HashSet<>();
			collectGroupCategories(actualCategories, change);
			actualCategories.forEach(actualCategory -> {
				assertTrue(setOfExpectedGroupCategories.contains(actualCategory.getName()),
						() -> "Unexpected group category: " + actualCategory.getName() + ", should find: " + String.join(", ", setOfExpectedGroupCategories));
			});
		}

		return status;
	}
	
	private void collectGroupCategories(Set<GroupCategory> result, Change change) {
		if (change instanceof TextEditBasedChange) {
			for (TextEditBasedChangeGroup group : ((TextEditBasedChange)change).getChangeGroups()) {
				result.addAll(group.getGroupCategorySet().asList());
			}
		} else if (change instanceof CompositeChange) {
			for (Change child : ((CompositeChange)change).getChildren()) {
				collectGroupCategories(result, child);
			}
		}
	}
	
	protected void enable(String key) throws CoreException {
		fProfile.getSettings().put(key, CleanUpOptions.TRUE);
		commitProfile();
	}

	private void commitProfile() throws CoreException {
		List<Profile> profiles= CleanUpPreferenceUtil.getBuiltInProfiles();
		profiles.add(fProfile);

		CleanUpProfileVersioner versioner= new CleanUpProfileVersioner();
		ProfileStore profileStore= new ProfileStore(CleanUpConstants.CLEANUP_PROFILES, versioner);
		profileStore.writeProfiles(profiles, InstanceScope.INSTANCE);

		CleanUpPreferenceUtil.saveSaveParticipantOptions(InstanceScope.INSTANCE, fProfile.getSettings());
	}
}
