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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Modifier enum.
 */
class ModifierTest {
	
	@Test
	void testFromJdtFlags_public() {
		Set<Modifier> modifiers = Modifier.fromJdtFlags(0x0001);
		assertThat(modifiers).containsExactly(Modifier.PUBLIC);
	}
	
	@Test
	void testFromJdtFlags_privateStatic() {
		int flags = 0x0002 | 0x0008; // PRIVATE | STATIC
		Set<Modifier> modifiers = Modifier.fromJdtFlags(flags);
		assertThat(modifiers).containsExactlyInAnyOrder(Modifier.PRIVATE, Modifier.STATIC);
	}
	
	@Test
	void testFromJdtFlags_publicStaticFinal() {
		int flags = 0x0001 | 0x0008 | 0x0010; // PUBLIC | STATIC | FINAL
		Set<Modifier> modifiers = Modifier.fromJdtFlags(flags);
		assertThat(modifiers).containsExactlyInAnyOrder(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL);
	}
	
	@Test
	void testFromJdtFlags_empty() {
		Set<Modifier> modifiers = Modifier.fromJdtFlags(0);
		assertThat(modifiers).isEmpty();
	}
	
	@Test
	void testToJdtFlags() {
		Set<Modifier> modifiers = Set.of(Modifier.PUBLIC, Modifier.STATIC);
		int flags = Modifier.toJdtFlags(modifiers);
		assertThat(flags).isEqualTo(0x0001 | 0x0008);
	}
	
	@Test
	void testToJdtFlags_empty() {
		int flags = Modifier.toJdtFlags(Set.of());
		assertThat(flags).isEqualTo(0);
	}
	
	@Test
	void testIsPresentIn_public() {
		assertThat(Modifier.PUBLIC.isPresentIn(0x0001)).isTrue();
		assertThat(Modifier.PRIVATE.isPresentIn(0x0001)).isFalse();
	}
	
	@Test
	void testIsPresentIn_publicStatic() {
		int flags = 0x0001 | 0x0008;
		assertThat(Modifier.PUBLIC.isPresentIn(flags)).isTrue();
		assertThat(Modifier.STATIC.isPresentIn(flags)).isTrue();
		assertThat(Modifier.FINAL.isPresentIn(flags)).isFalse();
	}
	
	@Test
	void testGetFlag() {
		assertThat(Modifier.PUBLIC.getFlag()).isEqualTo(0x0001);
		assertThat(Modifier.PRIVATE.getFlag()).isEqualTo(0x0002);
		assertThat(Modifier.STATIC.getFlag()).isEqualTo(0x0008);
	}
	
	@Test
	void testRoundTrip() {
		int originalFlags = 0x0001 | 0x0008 | 0x0010; // PUBLIC | STATIC | FINAL
		Set<Modifier> modifiers = Modifier.fromJdtFlags(originalFlags);
		int convertedFlags = Modifier.toJdtFlags(modifiers);
		assertThat(convertedFlags).isEqualTo(originalFlags);
	}
}
