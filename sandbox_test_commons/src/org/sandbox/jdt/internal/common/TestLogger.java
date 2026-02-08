/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
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
 * Simple test logging utility that is disabled by default.
 * 
 * <p>To enable debug output during test development or debugging:</p>
 * <ul>
 *   <li>Set system property: {@code -Dsandbox.test.debug=true}</li>
 *   <li>Or call {@code TestLogger.enable()} in your test</li>
 * </ul>
 * 
 * <p><b>Usage:</b></p>
 * <pre>
 * // Enable in a specific test
 * TestLogger.enable();
 * 
 * // Use like System.out
 * TestLogger.println("Debug: " + value);
 * TestLogger.printf("Value: %s%n", arg);
 * </pre>
 * 
 * @since 1.3.0
 */
public final class TestLogger {
    
    private static boolean enabled = Boolean.getBoolean("sandbox.test.debug"); //$NON-NLS-1$
    
    private TestLogger() {
        // Utility class
    }
    
    /**
     * Enables debug output.
     */
    public static void enable() {
        enabled = true;
    }
    
    /**
     * Disables debug output.
     */
    public static void disable() {
        enabled = false;
    }
    
    /**
     * @return true if debug output is enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Prints a message if enabled.
     * 
     * @param message the message
     */
    public static void println(String message) {
        if (enabled) {
            System.out.println(message);
        }
    }
    
    /**
     * Prints an empty line if enabled.
     */
    public static void println() {
        if (enabled) {
            System.out.println();
        }
    }
    
    /**
     * Prints a formatted message if enabled.
     * 
     * @param format the format string
     * @param args the arguments
     */
    public static void printf(String format, Object... args) {
        if (enabled) {
            System.out.printf(format, args);
        }
    }
    
    /**
     * Prints an object if enabled.
     * 
     * @param obj the object
     */
    public static void println(Object obj) {
        if (enabled) {
            System.out.println(obj);
        }
    }
}
