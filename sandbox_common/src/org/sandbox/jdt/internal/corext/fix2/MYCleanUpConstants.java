/*******************************************************************************
 * Copyright (c) 2021 Carsten Hammer.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Carsten Hammer
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix2;

/**
 * @author chammer
 *
 */
public class MYCleanUpConstants {

	/**
	 *
	 */
	public static final String EXPLICITENCODING_CLEANUP= "cleanup.explicit_encoding"; //$NON-NLS-1$

	/**
	 * Don't change behavior - just replace or insert to make use of platform encoding visible in the code.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_KEEP_BEHAVIOR= "cleanup.explicit_encoding_keep_behavior"; //$NON-NLS-1$

	/**
	 * Set all uses of platform encoding explicitly to UTF-8 - This changes behavior of the resulting code!
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_INSERT_UTF8= "cleanup.explicit_encoding_insert_utf8"; //$NON-NLS-1$

	/**
	 * Set all uses of platform encoding explicitly to UTF-8 - This changes behavior of the resulting code!
	 * At the same time try to have a single constant per project for this encoding that is referenced whenever
	 * code is changed to use this charset. That way later it is easy to change the default.
	 * <p>
	 * Possible values: {TRUE, FALSE}
	 * <p>
	 *
	 * @see CleanUpOptionsCore#TRUE
	 * @see CleanUpOptionsCore#FALSE
	 *
	 */
	public static final String EXPLICITENCODING_AGGREGATE_TO_UTF8= "cleanup.explicit_encoding_aggregate_to_utf8"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String SIMPLIFY_STATUS_CLEANUP= "cleanup.simplify_status_creation"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String XML_CLEANUP= "cleanup.xmlcleanup"; //$NON-NLS-1$
	/**
	 *
	 */
	public static final String JUNIT_CLEANUP= "cleanup.junitcleanup"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_ASSERT= "cleanup.junitcleanup_4_assert"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_ASSUME= "cleanup.junitcleanup_4_assume"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_SUITE= "cleanup.junitcleanup_4_suite"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_BEFORE= "cleanup.junitcleanup_4_before"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_AFTER= "cleanup.junitcleanup_4_after"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_BEFORECLASS= "cleanup.junitcleanup_4_beforeclass"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_AFTERCLASS= "cleanup.junitcleanup_4_afterclass"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_IGNORE= "cleanup.junitcleanup_4_ignore"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_TEST= "cleanup.junitcleanup_4_test"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULETEMPORARYFOLDER= "cleanup.junitcleanup_4_ruletemporaryfolder"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULETESTNAME= "cleanup.junitcleanup_4_ruletestname"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_EXTERNALRESOURCE= "cleanup.junitcleanup_4_externalresource"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RULEEXTERNALRESOURCE= "cleanup.junitcleanup_4_ruleexternalresource"; //$NON-NLS-1$
	/**
	 * 
	 */
	public static final String JUNIT_CLEANUP_4_RUNWITH= "cleanup.junitcleanup_4_runwith"; //$NON-NLS-1$
	/**
	 *
	 */
	public static final String JFACE_CLEANUP= "cleanup.jfacecleanup"; //$NON-NLS-1$

	/**
	 *
	 */
	public static final String USEFUNCTIONALLOOP_CLEANUP= "cleanup.functionalloop"; //$NON-NLS-1$
}
