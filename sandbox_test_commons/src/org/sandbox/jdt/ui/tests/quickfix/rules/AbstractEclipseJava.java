/*******************************************************************************
 * Copyright (c) 2024 Carsten Hammer and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *
 *******************************************************************************/
package org.sandbox.jdt.ui.tests.quickfix.rules;

import static org.eclipse.jdt.internal.corext.fix.CleanUpConstants.DEFAULT_CLEAN_UP_OPTIONS;

/**
 * JUnit 5 extension that provides the test infrastructure for Eclipse JDT cleanup and refactoring tests.
 * <p>
 * This class acts as a test fixture that:
 * <ul>
 * <li>Creates and configures a temporary Eclipse Java project for each test</li>
 * <li>Sets up the Java compiler compliance level (e.g., Java 8, 17, 21)</li>
 * <li>Manages cleanup profiles and options for testing JDT cleanup implementations</li>
 * <li>Provides helper methods for executing refactorings and asserting results</li>
 * <li>Cleans up resources after test execution</li>
 * </ul>
 * </p>
 * <p>
 * Concrete subclasses like {@code EclipseJava17} specify the runtime stubs and compiler version.
 * Test classes use this as a {@code @RegisterExtension} to obtain a configured test environment.
 * </p>
 * 
 * @see org.junit.jupiter.api.extension.BeforeEachCallback
 * @see org.junit.jupiter.api.extension.AfterEachCallback
 */
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleReference;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;

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

import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jdt.ui.cleanup.CleanUpOptions;
import org.eclipse.jdt.ui.cleanup.ICleanUp;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.preferences.cleanup.CleanUpProfileVersioner;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.CustomProfile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileManager.Profile;
import org.eclipse.jdt.internal.ui.preferences.formatter.ProfileStore;
import org.eclipse.jdt.internal.ui.util.CoreUtility;

public class AbstractEclipseJava implements AfterEachCallback, BeforeEachCallback {

	/** Name of the temporary test project created for each test */
	private static final String TEST_SETUP_PROJECT = "TestSetupProject"; //$NON-NLS-1$
	
	/** Default source folder name */
	private static final String DEFAULT_SRC_FOLDER = "src"; //$NON-NLS-1$
	
	/** Default binary output folder name */
	private static final String DEFAULT_BIN_FOLDER = "bin"; //$NON-NLS-1$
	
	/** Maximum number of retry attempts when deleting resources */
	private static final int MAX_RETRY = 5;
	
	/** Delay in milliseconds between retry attempts */
	private static final int RETRY_DELAY = 1000;

	/** Path to the runtime stubs JAR (e.g., rtstubs_17.jar) for the configured Java version */
	private final String testResourcesStubs;
	
	/** Java compiler compliance level (e.g., JavaCore.VERSION_17) */
	private final String complianceLevel;
	
	/** The source folder root for test compilation units */
	private IPackageFragmentRoot sourceFolder;
	
	/** The cleanup profile used for configuring cleanup options in tests */
	private CustomProfile cleanUpProfile;
	
	/** The temporary Java project created for the test */
	private IJavaProject javaProject;

	/**
	 * Constructs a new test environment with the specified runtime stubs and compiler version.
	 * 
	 * @param stubs path to the runtime stubs JAR file (e.g., "testresources/rtstubs_17.jar")
	 * @param compilerVersion the Java compiler compliance level (e.g., JavaCore.VERSION_17)
	 */
	public AbstractEclipseJava(final String stubs, final String compilerVersion) {
		this.testResourcesStubs = stubs;
		this.complianceLevel = compilerVersion;
	}

	/**
	 * Sets up the test environment before each test execution.
	 * <p>
	 * This method:
	 * <ul>
	 * <li>Creates a new temporary Java project</li>
	 * <li>Configures the project's classpath with runtime stubs</li>
	 * <li>Sets the Java compiler compliance level</li>
	 * <li>Creates a source folder for test compilation units</li>
	 * <li>Initializes a cleanup profile with all options disabled by default</li>
	 * </ul>
	 * </p>
	 * 
	 * @param context the extension context (provided by JUnit)
	 * @throws CoreException if project setup fails
	 */
	@Override
	public void beforeEach(final ExtensionContext context) throws CoreException {
		setJavaProject(createJavaProject(TEST_SETUP_PROJECT, DEFAULT_BIN_FOLDER));
		getJavaProject().setRawClasspath(getDefaultClasspath(), null);
		final Map<String, String> options = getJavaProject().getOptions(false);
		JavaCore.setComplianceOptions(complianceLevel, options);
		getJavaProject().setOptions(options);
		setSourceFolder(addSourceContainer(getProject(TEST_SETUP_PROJECT), DEFAULT_SRC_FOLDER, new Path[0],
				new Path[0], null, new IClasspathAttribute[0]));
		final Map<String, String> settings = new HashMap<>();
		cleanUpProfile = new ProfileManager.CustomProfile("testProfile", settings, CleanUpProfileVersioner.CURRENT_VERSION, //$NON-NLS-1$
				CleanUpProfileVersioner.PROFILE_KIND);
		InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).put(CleanUpConstants.CLEANUP_PROFILE, cleanUpProfile.getID());
		InstanceScope.INSTANCE.getNode(JavaUI.ID_PLUGIN).put(CleanUpConstants.SAVE_PARTICIPANT_PROFILE,
				cleanUpProfile.getID());
		disableAll();
	}
	/**
	 * Creates a classpath for JUnit testing by adding the specified JUnit container to the project.
	 * 
	 * @param junitContainerPath the path to the JUnit container (e.g., for JUnit 5)
	 * @return the source folder root for test compilation units
	 * @throws JavaModelException if classpath modification fails
	 * @throws CoreException if project configuration fails
	 */
	public IPackageFragmentRoot createClasspathForJUnit(final IPath junitContainerPath) throws JavaModelException, CoreException {
		final IJavaProject project = getJavaProject();
		project.setRawClasspath(getDefaultClasspath(), null);
		final IClasspathEntry cpe = JavaCore.newContainerEntry(junitContainerPath);
		AbstractEclipseJava.addToClasspath(project, cpe);
		sourceFolder = AbstractEclipseJava.addSourceContainer(project, DEFAULT_SRC_FOLDER);
		return sourceFolder;
	}

	/**
	 * Cleans up resources after each test execution.
	 * <p>
	 * This method deletes the source folder and related resources created during test setup.
	 * </p>
	 * 
	 * @param context the extension context (provided by JUnit)
	 * @throws CoreException if cleanup fails
	 */
	@Override
	public void afterEach(final ExtensionContext context) throws CoreException {
		delete(getSourceFolder());
	}

	/**
	 * Gets the Java project with the specified name from the workspace.
	 * 
	 * @param projectName the name of the project
	 * @return the Java project handle
	 */
	public IJavaProject getProject(final String projectName) {
		return JavaCore.create(ResourcesPlugin.getWorkspace().getRoot().getProject(projectName));
	}

	/**
	 * Returns the default classpath for the test project.
	 * <p>
	 * The classpath contains the runtime stubs JAR configured for the test Java version.
	 * </p>
	 * 
	 * @return array containing the rt.jar classpath entry
	 * @throws CoreException if the runtime stubs cannot be located
	 */
	public IClasspathEntry[] getDefaultClasspath() throws CoreException {
		final IPath[] rtJarPath = findRtJar(new Path(testResourcesStubs));
		return new IClasspathEntry[] { JavaCore.newLibraryEntry(rtJarPath[0], rtJarPath[1], rtJarPath[2], true) };
	}

	/**
	 * Disables all cleanup options in the current profile.
	 * <p>
	 * This provides a clean slate for tests to selectively enable specific cleanup options.
	 * </p>
	 * 
	 * @throws CoreException if the profile cannot be updated
	 */
	protected void disableAll() throws CoreException {
		final Map<String, String> settings = cleanUpProfile.getSettings();
		JavaPlugin.getDefault().getCleanUpRegistry().getDefaultOptions(DEFAULT_CLEAN_UP_OPTIONS).getKeys()
		.forEach(a -> settings.put(a, CleanUpOptions.FALSE));
		commitProfile();
	}

	/**
	 * Removes an IJavaElement's resource with retry logic.
	 * <p>
	 * Retries deletion if it fails initially (e.g., because the indexer still locks the file).
	 * For Java projects, the classpath is cleared before deletion.
	 * </p>
	 *
	 * @param elem the element to delete
	 * @throws CoreException if all deletion attempts fail
	 */
	public void delete(final IJavaElement elem) throws CoreException {
		final IWorkspaceRunnable runnable = monitor -> {
			if (elem instanceof IJavaProject jproject) {
				jproject.setRawClasspath(new IClasspathEntry[0], jproject.getProject().getFullPath(), null);
			}
			delete(elem.getResource());
		};
		ResourcesPlugin.getWorkspace().run(runnable, null);
	}

	/**
	 * Removes a resource with retry logic.
	 * <p>
	 * Retries deletion if it fails initially (e.g., because the indexer still locks the file).
	 * Waits {@link #RETRY_DELAY} milliseconds between attempts, up to {@link #MAX_RETRY} times.
	 * </p>
	 *
	 * @param resource the resource to delete
	 * @throws CoreException if all deletion attempts fail
	 */
	public static void delete(final IResource resource) throws CoreException {
		for (int i = 0; i < MAX_RETRY; i++) {
			try {
				resource.delete(IResource.FORCE | IResource.ALWAYS_DELETE_PROJECT_CONTENT, null);
				return; // Success, exit early
			} catch (CoreException e) {
				if (i == MAX_RETRY - 1) {
					// Last attempt failed, throw the exception
					throw e;
				}
				try {
					Thread.sleep(RETRY_DELAY); // give other threads time to close the file
				} catch (InterruptedException e1) {
					// Restore interrupt status
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * Locates the runtime stubs JAR file for the configured Java version.
	 * 
	 * @param rtStubsPath the path to the RT stubs
	 * @return an array containing [jar path, source attachment path, source attachment root path]
	 * @throws CoreException if the stubs file doesn't exist
	 */
	public IPath[] findRtJar(final IPath rtStubsPath) throws CoreException {
		final File rtStubs = rtStubsPath.toFile().getAbsoluteFile();
		assertNotNull(rtStubs, "Runtime stubs file must not be null");
		assertTrue(rtStubs.exists(), "Runtime stubs file must exist: " + rtStubs.getAbsolutePath());
		return new IPath[] { Path.fromOSString(rtStubs.getPath()), null, null };
	}

	/**
	 * Returns the OSGi bundle associated with this class.
	 *
	 * @return the associated bundle, or null if not running in an OSGi environment
	 */
	public final Bundle getBundle() {
		final ClassLoader cl = getClass().getClassLoader();
		if (cl instanceof BundleReference) {
			return ((BundleReference) cl).getBundle();
		}
		return null;
	}

	/**
	 * Creates a new Java project in the workspace.
	 *
	 * @param projectName the name of the project
	 * @param binFolderName name of the output folder, or null/empty for project root
	 * @return the newly created Java project handle
	 * @throws CoreException if project creation fails
	 */
	public static IJavaProject createJavaProject(final String projectName, final String binFolderName) throws CoreException {
		final IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		final IProject project = root.getProject(projectName);
		if (!project.exists()) {
			project.create(null);
		} else {
			project.refreshLocal(IResource.DEPTH_INFINITE, null);
		}
		if (!project.isOpen()) {
			project.open(null);
		}
		final IPath outputLocation;
		if (binFolderName != null && binFolderName.length() > 0) {
			final IFolder binFolder = project.getFolder(binFolderName);
			if (!binFolder.exists()) {
				CoreUtility.createFolder(binFolder, false, true, null);
			}
			outputLocation = binFolder.getFullPath();
		} else {
			outputLocation = project.getFullPath();
		}
		if (!project.hasNature(JavaCore.NATURE_ID)) {
			addNatureToProject(project, JavaCore.NATURE_ID, null);
		}
		final IJavaProject jproject = JavaCore.create(project);
		jproject.setOutputLocation(outputLocation, null);
		jproject.setRawClasspath(new IClasspathEntry[0], null);
		return jproject;
	}

	/**
	 * Adds a nature to a project.
	 * 
	 * @param proj the project
	 * @param natureId the nature ID to add (e.g., JavaCore.NATURE_ID)
	 * @param monitor progress monitor, or null
	 * @throws CoreException if the operation fails
	 */
	private static void addNatureToProject(final IProject proj, final String natureId, final IProgressMonitor monitor)
			throws CoreException {
		final IProjectDescription description = proj.getDescription();
		final String[] prevNatures = description.getNatureIds();
		final String[] newNatures = new String[prevNatures.length + 1];
		System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
		newNatures[prevNatures.length] = natureId;
		description.setNatureIds(newNatures);
		proj.setDescription(description, monitor);
	}

	/**
	 * Adds a source container to a Java project with full configuration options.
	 *
	 * @param jproject the parent project
	 * @param containerName the name of the new source container, or null/empty for project root
	 * @param inclusionFilters inclusion filters to set (paths to include)
	 * @param exclusionFilters exclusion filters to set (paths to exclude)
	 * @param outputLocation the location where class files are written to, or null for project output folder
	 * @param attributes the classpath attributes to set
	 * @return the handle to the new source container
	 * @throws CoreException if creation fails
	 */
	public static IPackageFragmentRoot addSourceContainer(final IJavaProject jproject, final String containerName,
			final IPath[] inclusionFilters, final IPath[] exclusionFilters, final String outputLocation, final IClasspathAttribute[] attributes)
					throws CoreException {
		final IProject project = jproject.getProject();
		final IContainer container;
		if (containerName == null || containerName.length() == 0) {
			container = project;
		} else {
			final IFolder folder = project.getFolder(containerName);
			if (!folder.exists()) {
				CoreUtility.createFolder(folder, false, true, null);
			}
			container = folder;
		}
		final IPackageFragmentRoot root = jproject.getPackageFragmentRoot(container);

		final IPath outputPath;
		if (outputLocation != null) {
			final IFolder folder = project.getFolder(outputLocation);
			if (!folder.exists()) {
				CoreUtility.createFolder(folder, false, true, null);
			}
			outputPath = folder.getFullPath();
		} else {
			outputPath = null;
		}
		final IClasspathEntry cpe = JavaCore.newSourceEntry(root.getPath(), inclusionFilters, exclusionFilters, outputPath,
				attributes);
		addToClasspath(jproject, cpe);
		return root;
	}

	/**
	 * Adds a source container to a Java project with simple configuration.
	 * 
	 * @param jproject the parent project
	 * @param containerName the name of the new source container
	 * @return the handle to the new source container
	 * @throws CoreException if creation fails
	 */
	public static IPackageFragmentRoot addSourceContainer(final IJavaProject jproject, final String containerName) throws CoreException {
		return addSourceContainer(jproject, containerName, new Path[0]);
	}
	
	/**
	 * Adds a source container to a Java project with exclusion filters.
	 * 
	 * @param jproject the parent project
	 * @param containerName the name of the new source container
	 * @param exclusionFilters exclusion filters to set (paths to exclude)
	 * @return the handle to the new source container
	 * @throws CoreException if creation fails
	 */
	public static IPackageFragmentRoot addSourceContainer(final IJavaProject jproject, final String containerName, final IPath[] exclusionFilters) throws CoreException {
		return addSourceContainer(jproject, containerName, new Path[0], exclusionFilters);
	}
	
	/**
	 * Adds a source container to a Java project with inclusion and exclusion filters.
	 * 
	 * @param jproject the parent project
	 * @param containerName the name of the new source container
	 * @param inclusionFilters inclusion filters to set (paths to include)
	 * @param exclusionFilters exclusion filters to set (paths to exclude)
	 * @return the handle to the new source container
	 * @throws CoreException if creation fails
	 */
	public static IPackageFragmentRoot addSourceContainer(final IJavaProject jproject, final String containerName, final IPath[] inclusionFilters, final IPath[] exclusionFilters) throws CoreException {
		return addSourceContainer(jproject, containerName, inclusionFilters, exclusionFilters, null);
	}
	
	/**
	 * Adds a source container to a Java project with custom output location.
	 * 
	 * @param jproject the parent project
	 * @param containerName the name of the new source container
	 * @param inclusionFilters inclusion filters to set (paths to include)
	 * @param exclusionFilters exclusion filters to set (paths to exclude)
	 * @param outputLocation the location where class files are written to, or null for project output folder
	 * @return the handle to the new source container
	 * @throws CoreException if creation fails
	 */
	public static IPackageFragmentRoot addSourceContainer(final IJavaProject jproject, final String containerName, final IPath[] inclusionFilters, final IPath[] exclusionFilters, final String outputLocation) throws CoreException {
		return addSourceContainer(jproject, containerName, inclusionFilters, exclusionFilters, outputLocation,
				new IClasspathAttribute[0]);
	}
	
	/**
	 * Adds a classpath entry to a Java project if it doesn't already exist.
	 * 
	 * @param jproject the project to modify
	 * @param cpe the classpath entry to add
	 * @throws JavaModelException if the operation fails
	 */
	public static void addToClasspath(final IJavaProject jproject, final IClasspathEntry cpe) throws JavaModelException {
		final IClasspathEntry[] oldEntries = jproject.getRawClasspath();
		for (final IClasspathEntry oldEntry : oldEntries) {
			if (oldEntry.equals(cpe)) {
				return; // Entry already exists
			}
		}
		final int nEntries = oldEntries.length;
		final IClasspathEntry[] newEntries = new IClasspathEntry[nEntries + 1];
		System.arraycopy(oldEntries, 0, newEntries, 0, nEntries);
		newEntries[nEntries] = cpe;
		jproject.setRawClasspath(newEntries, null);
	}

	/**
	 * Executes the configured refactoring and asserts the result matches expectations.
	 * <p>
	 * Also validates that the input compilation units have no compilation errors,
	 * catching invalid test input early with detailed diagnostics.
	 * </p>
	 * 
	 * @param cus the compilation units to refactor
	 * @param expected the expected source code after refactoring (one per CU)
	 * @param setOfExpectedGroupCategories expected group category names, or null to skip validation
	 * @return the refactoring status
	 * @throws CoreException if the refactoring fails
	 */
	public RefactoringStatus assertRefactoringResultAsExpected(final ICompilationUnit[] cus, final String[] expected,
			final Set<String> setOfExpectedGroupCategories) throws CoreException {
		for (final ICompilationUnit cu : cus) {
			assertNoCompilationError(cu);
		}
		final RefactoringStatus status = performRefactoring(cus, setOfExpectedGroupCategories);
		final String[] previews = new String[cus.length];
		for (int i = 0; i < cus.length; i++) {
			final ICompilationUnit cu = cus[i];
			previews[i] = cu.getBuffer().getContents();
		}
		assertEqualStringsIgnoreOrder(previews, expected);
		return status;
	}

	/**
	 * Asserts that the refactoring produces no changes.
	 * <p>
	 * Also validates that the compilation units have no compilation errors.
	 * </p>
	 * 
	 * @param cus the compilation units to check
	 * @return the refactoring status
	 * @throws CoreException if the validation fails
	 */
	public RefactoringStatus assertRefactoringHasNoChange(final ICompilationUnit[] cus) throws CoreException {
		for (final ICompilationUnit cu : cus) {
			assertNoCompilationError(cu);
		}
		return assertRefactoringHasNoChangeEventWithError(cus);
	}

	/**
	 * Asserts that the refactoring produces no changes (even when compilation errors are present).
	 * 
	 * @param cus the compilation units to check
	 * @return the refactoring status
	 * @throws CoreException if the refactoring fails
	 */
	protected RefactoringStatus assertRefactoringHasNoChangeEventWithError(final ICompilationUnit[] cus)
			throws CoreException {
		final String[] expected = new String[cus.length];
		for (int i = 0; i < cus.length; i++) {
			expected[i] = cus[i].getBuffer().getContents();
		}
		return assertRefactoringResultAsExpectedSkipCompilationCheck(cus, expected, null);
	}

	/**
	 * Executes the configured refactoring and asserts the result matches expectations.
	 * <p>
	 * Does NOT validate compilation errors - used internally by
	 * {@link #assertRefactoringHasNoChangeEventWithError} which deliberately
	 * tests refactoring behavior on code with compilation errors.
	 * </p>
	 */
	private RefactoringStatus assertRefactoringResultAsExpectedSkipCompilationCheck(final ICompilationUnit[] cus,
			final String[] expected, final Set<String> setOfExpectedGroupCategories) throws CoreException {
		final RefactoringStatus status = performRefactoring(cus, setOfExpectedGroupCategories);
		final String[] previews = new String[cus.length];
		for (int i = 0; i < cus.length; i++) {
			final ICompilationUnit cu = cus[i];
			previews[i] = cu.getBuffer().getContents();
		}
		assertEqualStringsIgnoreOrder(previews, expected);
		return status;
	}

	/**
	 * Parses and validates a compilation unit for compilation errors.
	 * 
	 * <p>Uses the JDT {@link IProblem} API to provide detailed error diagnostics
	 * including problem severity, source line number, and problem ID for
	 * easier debugging of test input code.</p>
	 * 
	 * @param cu the compilation unit to check
	 * @return the AST compilation unit root
	 * @throws AssertionError if compilation errors (non-warnings) are found
	 */
	protected CompilationUnit assertNoCompilationError(final ICompilationUnit cu) {
		final ASTParser parser = ASTParser.newParser(IASTSharedValues.SHARED_AST_LEVEL);
		parser.setSource(cu);
		parser.setResolveBindings(true);
		final CompilationUnit root = (CompilationUnit) parser.createAST(null);
		final IProblem[] problems = root.getProblems();
		boolean hasProblems = false;
		for (final IProblem prob : problems) {
			if (!prob.isWarning() && !prob.isInfo()) {
				hasProblems = true;
				break;
			}
		}
		if (hasProblems) {
			final StringBuilder builder = new StringBuilder();
			builder.append(cu.getElementName()).append(" has compilation problems: \n"); //$NON-NLS-1$
			for (final IProblem prob : problems) {
				if (!prob.isWarning() && !prob.isInfo()) {
					builder.append("ERROR line "); //$NON-NLS-1$
					builder.append(prob.getSourceLineNumber());
					builder.append(": "); //$NON-NLS-1$
					builder.append(prob.getMessage());
					builder.append(" [id="); //$NON-NLS-1$
					builder.append(prob.getID());
					builder.append("]\n"); //$NON-NLS-1$
				}
			}
			fail(builder.toString());
		}
		return root;
	}

	/**
	 * Asserts that two string arrays contain the same elements, ignoring order.
	 * <p>
	 * If the arrays differ, produces a detailed comparison showing the differences.
	 * </p>
	 * 
	 * @param actuals the actual strings
	 * @param expecteds the expected strings
	 */
	public static void assertEqualStringsIgnoreOrder(final String[] actuals, final String[] expecteds) {
		final ArrayList<String> actualList = new ArrayList<>(Arrays.asList(actuals));
		final ArrayList<String> expectedList = new ArrayList<>(Arrays.asList(expecteds));
		
		// Remove matching elements from both lists
		for (int i = actualList.size() - 1; i >= 0; i--) {
			if (expectedList.remove(actualList.get(i))) {
				actualList.remove(i);
			}
		}
		
		final int numUnmatchedActuals = actualList.size();
		final int numUnmatchedExpected = expectedList.size();
		
		if (numUnmatchedActuals + numUnmatchedExpected > 0) {
			// Special case: if there's exactly one difference, show a direct comparison
			if (numUnmatchedActuals == 1 && numUnmatchedExpected == 1) {
				assertEquals(expectedList.get(0), actualList.get(0));
			}
			
			// Build detailed error message showing all differences
			final String actual = buildStringFromList(actualList);
			final String expected = buildStringFromList(expectedList);
			assertEquals(expected, actual);
		}
	}

	/**
	 * Helper method to build a single string from a list of strings.
	 * 
	 * @param strings the list of strings
	 * @return a single concatenated string with newline separators
	 */
	private static String buildStringFromList(final ArrayList<String> strings) {
		final StringBuilder buf = new StringBuilder();
		for (final String s : strings) {
			if (s != null) {
				buf.append(s);
				buf.append("\n"); //$NON-NLS-1$
			}
		}
		return buf.toString();
	}

	/**
	 * Performs a cleanup refactoring on the given compilation units.
	 * <p>
	 * This is the main method that executes cleanup operations configured via the current profile.
	 * </p>
	 * 
	 * @param cus the compilation units to refactor
	 * @param setOfExpectedGroupCategories expected group category names, or null to skip validation
	 * @return the refactoring status
	 * @throws CoreException if the refactoring fails
	 */
	protected final RefactoringStatus performRefactoring(final ICompilationUnit[] cus,
			final Set<String> setOfExpectedGroupCategories) throws CoreException {
		final CleanUpRefactoring ref = new CleanUpRefactoring();
		ref.setUseOptionsFromProfile(true);
		return performRefactoring(ref, cus, JavaPlugin.getDefault().getCleanUpRegistry().createCleanUps(),
				setOfExpectedGroupCategories);
	}

	/**
	 * Performs a cleanup refactoring with specified cleanup instances.
	 * 
	 * @param ref the cleanup refactoring instance
	 * @param cus the compilation units to refactor
	 * @param cleanUps the cleanup instances to apply
	 * @param setOfExpectedGroupCategories expected group category names, or null to skip validation
	 * @return the refactoring status
	 * @throws CoreException if the refactoring fails
	 */
	protected RefactoringStatus performRefactoring(final CleanUpRefactoring ref, final ICompilationUnit[] cus,
			final ICleanUp[] cleanUps, final Set<String> setOfExpectedGroupCategories) throws CoreException {
		for (final ICompilationUnit cu : cus) {
			ref.addCompilationUnit(cu);
		}
		for (final ICleanUp cleanUp : cleanUps) {
			ref.addCleanUp(cleanUp);
		}
		final IUndoManager undoManager = RefactoringCore.getUndoManager();
		undoManager.flush();
		final CreateChangeOperation create = new CreateChangeOperation(
				new CheckConditionsOperation(ref, CheckConditionsOperation.ALL_CONDITIONS), RefactoringStatus.FATAL);
		final PerformChangeOperation perform = new PerformChangeOperation(create);
		perform.setUndoManager(undoManager, ref.getName());
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		workspace.run(perform, new NullProgressMonitor());
		final RefactoringStatus status = create.getConditionCheckingStatus();
		if (status.hasFatalError()) {
			throw new CoreException(
					new StatusInfo(status.getSeverity(), status.getMessageMatchingSeverity(status.getSeverity())));
		}
		assertTrue(perform.changeExecuted(), "Change wasn't executed"); //$NON-NLS-1$
		final Change undo = perform.getUndoChange();
		assertNotNull(undo, "Undo doesn't exist"); //$NON-NLS-1$
		assertTrue(undoManager.anythingToUndo(), "Undo manager is empty"); //$NON-NLS-1$
		if (setOfExpectedGroupCategories != null) {
			final Change change = create.getChange();
			final Set<GroupCategory> actualCategories = new HashSet<>();
			collectGroupCategories(actualCategories, change);
			actualCategories.forEach(actualCategory -> {
				assertTrue(setOfExpectedGroupCategories.contains(actualCategory.getName()),
						() -> "Unexpected group category: " + actualCategory.getName() + ", should find: " //$NON-NLS-1$ //$NON-NLS-2$
								+ String.join(", ", setOfExpectedGroupCategories)); //$NON-NLS-1$
			});
		}
		return status;
	}

	/**
	 * Recursively collects group categories from a change tree.
	 * 
	 * @param result the set to collect categories into
	 * @param change the change to examine
	 */
	private void collectGroupCategories(final Set<GroupCategory> result, final Change change) {
		if (change instanceof TextEditBasedChange) {
			for (final TextEditBasedChangeGroup group : ((TextEditBasedChange) change).getChangeGroups()) {
				result.addAll(group.getGroupCategorySet().asList());
			}
		} else if (change instanceof CompositeChange) {
			for (final Change child : ((CompositeChange) change).getChildren()) {
				collectGroupCategories(result, child);
			}
		}
	}

	/**
	 * Enables a cleanup option in the current profile.
	 * 
	 * @param key the cleanup option key (from MYCleanUpConstants or CleanUpConstants)
	 * @throws CoreException if the profile cannot be updated
	 */
	public void enable(final String key) throws CoreException {
		cleanUpProfile.getSettings().put(key, CleanUpOptions.TRUE);
		commitProfile();
	}

	/**
	 * Sets a cleanup option to a specific value in the current profile.
	 * <p>
	 * This is useful for non-boolean options like target format selections.
	 * </p>
	 * 
	 * @param key the cleanup option key (from MYCleanUpConstants or CleanUpConstants)
	 * @param value the value to set
	 * @throws CoreException if the profile cannot be updated
	 */
	public void set(final String key, final String value) throws CoreException {
		cleanUpProfile.getSettings().put(key, value);
		commitProfile();
	}

	/**
	 * Disables a cleanup option in the current profile.
	 * 
	 * @param key the cleanup option key (from MYCleanUpConstants or CleanUpConstants)
	 * @throws CoreException if the profile cannot be updated
	 */
	public void disable(final String key) throws CoreException {
		cleanUpProfile.getSettings().put(key, CleanUpOptions.FALSE);
		commitProfile();
	}

	/**
	 * Commits the current cleanup profile changes to persistent storage.
	 * 
	 * @throws CoreException if the profile cannot be saved
	 */
	private void commitProfile() throws CoreException {
		final List<Profile> profiles = CleanUpPreferenceUtil.getBuiltInProfiles();
		profiles.add(cleanUpProfile);
		final CleanUpProfileVersioner versioner = new CleanUpProfileVersioner();
		final ProfileStore profileStore = new ProfileStore(CleanUpConstants.CLEANUP_PROFILES, versioner);
		profileStore.writeProfiles(profiles, InstanceScope.INSTANCE);
		CleanUpPreferenceUtil.saveSaveParticipantOptions(InstanceScope.INSTANCE, cleanUpProfile.getSettings());
	}

	/**
	 * Gets the source folder for test compilation units.
	 * 
	 * @return the source folder root
	 */
	public IPackageFragmentRoot getSourceFolder() {
		return sourceFolder;
	}

	/**
	 * Sets the source folder for test compilation units.
	 * 
	 * @param sourceFolder the source folder root
	 */
	public void setSourceFolder(final IPackageFragmentRoot sourceFolder) {
		this.sourceFolder = sourceFolder;
	}

	/**
	 * Gets the temporary Java project created for the test.
	 * 
	 * @return the Java project
	 */
	public IJavaProject getJavaProject() {
		return javaProject;
	}

	/**
	 * Sets the temporary Java project for the test.
	 * 
	 * @param javaProject the Java project
	 */
	public void setJavaProject(final IJavaProject javaProject) {
		this.javaProject = javaProject;
	}
}
