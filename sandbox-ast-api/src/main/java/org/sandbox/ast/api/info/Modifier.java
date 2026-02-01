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
package org.sandbox.ast.api.info;

import java.util.EnumSet;
import java.util.Set;

/**
 * Represents Java modifiers (public, private, static, final, etc.).
 * This enum provides a type-safe way to work with modifiers without
 * relying on JDT-specific flags.
 */
public enum Modifier {
	PUBLIC(0x0001),
	PRIVATE(0x0002),
	PROTECTED(0x0004),
	STATIC(0x0008),
	FINAL(0x0010),
	SYNCHRONIZED(0x0020),
	VOLATILE(0x0040),
	TRANSIENT(0x0080),
	NATIVE(0x0100),
	INTERFACE(0x0200),
	ABSTRACT(0x0400),
	STRICTFP(0x0800),
	DEFAULT(0x1000);
	
	private final int flag;
	
	Modifier(int flag) {
		this.flag = flag;
	}
	
	/**
	 * Returns the flag value for this modifier.
	 * @return the flag value
	 */
	public int getFlag() {
		return flag;
	}
	
	/**
	 * Converts JDT modifier flags to a set of Modifier enums.
	 * 
	 * @param jdtFlags JDT modifier flags (as int)
	 * @return Set of Modifier enums
	 */
	public static Set<Modifier> fromJdtFlags(int jdtFlags) {
		Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
		for (Modifier mod : values()) {
			if ((jdtFlags & mod.flag) != 0) {
				modifiers.add(mod);
			}
		}
		return modifiers;
	}
	
	/**
	 * Converts a set of modifiers to JDT-style flags.
	 * 
	 * @param modifiers Set of modifiers
	 * @return JDT flags as int
	 */
	public static int toJdtFlags(Set<Modifier> modifiers) {
		int flags = 0;
		for (Modifier mod : modifiers) {
			flags |= mod.flag;
		}
		return flags;
	}
	
	/**
	 * Checks if the given flags contain this modifier.
	 * 
	 * @param jdtFlags JDT modifier flags
	 * @return true if this modifier is present
	 */
	public boolean isPresentIn(int jdtFlags) {
		return (jdtFlags & flag) != 0;
	}
}
