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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Tests for ParameterInfo.
 */
class ParameterInfoTest {
	
	@Test
	void testSimpleParameter() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		ParameterInfo param = ParameterInfo.of("name", stringType);
		
		assertThat(param.name()).isEqualTo("name");
		assertThat(param.type()).isEqualTo(stringType);
		assertThat(param.varargs()).isFalse();
	}
	
	@Test
	void testVarargsParameter() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		ParameterInfo param = ParameterInfo.varargs("args", stringType);
		
		assertThat(param.name()).isEqualTo("args");
		assertThat(param.type()).isEqualTo(stringType);
		assertThat(param.varargs()).isTrue();
	}
	
	@Test
	void testValidation_nullName() {
		TypeInfo stringType = TypeInfo.Builder.of("java.lang.String").build();
		assertThatThrownBy(() -> new ParameterInfo(null, stringType, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Parameter name cannot be null");
	}
	
	@Test
	void testValidation_nullType() {
		assertThatThrownBy(() -> new ParameterInfo("name", null, false))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Parameter type cannot be null");
	}
}
