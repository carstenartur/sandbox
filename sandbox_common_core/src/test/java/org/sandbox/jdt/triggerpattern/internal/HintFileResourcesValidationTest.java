package org.sandbox.jdt.triggerpattern.internal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.sandbox.jdt.triggerpattern.internal.HintFileStore.HintFileValidationProblem;

/**
 * Validates all bundled {@code .sandbox-hint} resource files in the classpath.
 * This includes active and disabled bundled hint files so PRs catch invalid
 * syntax, duplicate IDs and broken includes before those files are enabled.
 */
class HintFileResourcesValidationTest {

	private final HintFileStore store = new HintFileStore();

	@Test
	void testAllBundledHintFilesValid() {
		List<HintFileValidationProblem> problems = store.validateBundledLibraries(
				HintFileResourcesValidationTest.class.getClassLoader());

		assertTrue(problems.isEmpty(), () -> formatProblems(problems));
	}

	@Test
	void testDisabledHintFilesArePartOfValidationSet() {
		String[] activeNames = HintFileStore.getBundledLibraryNames();
		String[] disabledNames = HintFileStore.getDisabledBundledLibraryNames();
		String[] allNames = HintFileStore.getAllBundledLibraryNames();

		assertTrue(disabledNames.length > 0,
				"There should be disabled bundled hint files to validate"); //$NON-NLS-1$
		assertTrue(allNames.length > activeNames.length,
				"Validation set should include disabled bundled hint files"); //$NON-NLS-1$
		assertTrue(List.of(disabledNames).contains("anonymous-to-lambda.sandbox-hint"), //$NON-NLS-1$
				"Known disabled hint file should be tracked for validation"); //$NON-NLS-1$
	}

	private static String formatProblems(List<HintFileValidationProblem> problems) {
		StringBuilder sb = new StringBuilder("Hint file validation failed:\n"); //$NON-NLS-1$
		for (HintFileValidationProblem problem : problems) {
			sb.append("- ") //$NON-NLS-1$
					.append(problem.resourceName())
					.append(": ") //$NON-NLS-1$
					.append(problem.message())
					.append('\n');
		}
		return sb.toString();
	}

}