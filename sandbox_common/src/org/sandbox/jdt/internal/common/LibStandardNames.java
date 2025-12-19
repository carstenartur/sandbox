/*******************************************************************************
 * Copyright (c) 2021 IBM Corporation and others.
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
package org.sandbox.jdt.internal.common;

/**
 * Library of standard method and field names used across sandbox cleanup plugins.
 * This class provides a centralized repository of common Java API method and field names
 * that are frequently referenced during code transformations and cleanups.
 */
public class LibStandardNames {
	/**
	 * Method name for {@link System#getProperty(String)}
	 */
	public static final String METHOD_GET_PROPERTY= "getProperty"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.nio.charset.Charset#displayName()}
	 */
	public static final String METHOD_DISPLAY_NAME= "displayName"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.nio.charset.Charset#defaultCharset()}
	 */
	public static final String METHOD_DEFAULT_CHARSET= "defaultCharset"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.io.File#getSeparator()}
	 */
	public static final String METHOD_GET_SEPARATOR= "getSeparator"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.io.File#getPathSeparator()}
	 */
	public static final String METHOD_GET_PATH_SEPARATOR= "getPathSeparator"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.util.Locale#getDefault()} and other getDefault methods
	 */
	public static final String METHOD_GET_DEFAULT= "getDefault"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link System#lineSeparator()}
	 */
	public static final String METHOD_LINE_SEPARATOR= "lineSeparator"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Boolean#getBoolean(String)}
	 */
	public static final String METHOD_BOOLEAN= "getBoolean"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Integer#getInteger(String)}
	 */
	public static final String METHOD_INTEGER= "getInteger"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Long#getLong(String)}
	 */
	public static final String METHOD_LONG= "getLong"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Boolean#parseBoolean(String)}
	 */
	public static final String METHOD_PARSEBOOLEAN= "parseBoolean"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Integer#parseInt(String)}
	 */
	public static final String METHOD_PARSEINTEGER= "parseInt"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Long#parseLong(String)}
	 */
	public static final String METHOD_PARSELONG= "parseLong"; //$NON-NLS-1$
	
	/**
	 * Field name for {@link java.io.File#pathSeparator}
	 */
	public static final String FIELD_PATH_SEPARATOR= "pathSeparator"; //$NON-NLS-1$
	
	/**
	 * Field name for {@link java.io.File#separator}
	 */
	public static final String FIELD_SEPARATOR= "separator"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.lang.Iterable#forEach(java.util.function.Consumer)}
	 */
	public static final String METHOD_FOREACH= "forEach"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Runtime#version()}
	 */
	public static final String METHOD_VERSION= "version"; //$NON-NLS-1$
	
	/**
	 * Method name for version feature accessor
	 */
	public static final String METHOD_FEATURE= "feature"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link org.eclipse.core.runtime.Status#warning(String)} factory method
	 */
	public static final String METHOD_WARNING= "warning"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link org.eclipse.core.runtime.Status#error(String)} factory method
	 */
	public static final String METHOD_ERROR= "error"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link org.eclipse.core.runtime.Status#info(String)} factory method
	 */
	public static final String METHOD_INFO= "info"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link String#getBytes(java.nio.charset.Charset)}
	 */
	public static final String METHOD_GET_BYTES= "getBytes"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.util.Properties#storeToXML(java.io.OutputStream, String, java.nio.charset.Charset)}
	 */
	public static final String METHOD_STORE_TO_XML= "storeToXML"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.nio.charset.Charset#forName(String)}
	 */
	public static final String METHOD_FOR_NAME= "forName"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.nio.file.Files#newBufferedReader(java.nio.file.Path, java.nio.charset.Charset)}
	 */
	public static final String METHOD_NEW_READER= "newReader"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.nio.file.Files#newBufferedWriter(java.nio.file.Path, java.nio.charset.Charset, java.nio.file.OpenOption...)}
	 */
	public static final String METHOD_NEW_WRITER= "newWriter"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.net.URLDecoder#decode(String, java.nio.charset.Charset)}
	 */
	public static final String METHOD_DECODE= "decode"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link java.net.URLEncoder#encode(String, java.nio.charset.Charset)}
	 */
	public static final String METHOD_ENCODE= "encode"; //$NON-NLS-1$
	
	/**
	 * Method name for {@link Object#toString()}
	 */
	public static final String METHOD_TOSTRING= "toString"; //$NON-NLS-1$
	
	/**
	 * Field name for {@link java.nio.charset.StandardCharsets#UTF_8}
	 */
	public static final String FIELD_UTF8= "UTF_8"; //$NON-NLS-1$
}
