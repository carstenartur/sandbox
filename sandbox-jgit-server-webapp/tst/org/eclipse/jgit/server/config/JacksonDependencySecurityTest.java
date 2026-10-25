/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jgit.server.config;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.fasterxml.jackson.core.ErrorReportConfiguration;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;

/** Consumer guard for the bounded error-token reporting fixed by GHSA-7hhh-6rmp-j9qf. */
public class JacksonDependencySecurityTest {

	@Test
	public void malformedDataInputTokenRespectsConfiguredErrorLimit() throws Exception {
		JsonFactory factory= JsonFactory.builder()
				.errorReportConfiguration(ErrorReportConfiguration.builder().maxErrorTokenLength(64).build())
				.build();
		byte[] malformed= ("invalid".repeat(2048) + " ").getBytes(StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
		try (DataInputStream input= new DataInputStream(new ByteArrayInputStream(malformed));
				JsonParser parser= factory.createParser((DataInput) input)) {
			JsonParseException failure= assertThrows(JsonParseException.class, parser::nextToken);
			assertTrue("Error reporting must not retain the complete untrusted token", //$NON-NLS-1$
					failure.getOriginalMessage().length() < 512);
		}
	}
}
