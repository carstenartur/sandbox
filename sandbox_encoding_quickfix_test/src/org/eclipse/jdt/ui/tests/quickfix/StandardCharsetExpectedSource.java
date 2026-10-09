/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.eclipse.jdt.ui.tests.quickfix;

/**
 * Literal golden sources shared by the legacy Java 8/10 charset cases.
 * These are expected sources only; cleanup results are compared unchanged.
 */
public final class StandardCharsetExpectedSource {
	private StandardCharsetExpectedSource() {
	}

	public static final String ANNOTATED = """
package test1;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class E1 {
	@SuppressWarnings("unused")
	void method(String filename) {
		Charset cs1= StandardCharsets.UTF_8;
		Charset cs1b= StandardCharsets.UTF_8;
		Charset cs2= StandardCharsets.UTF_16;
		Charset cs3= StandardCharsets.UTF_16BE;
		Charset cs4= StandardCharsets.UTF_16LE;
		Charset cs5= StandardCharsets.ISO_8859_1;
		Charset cs6= StandardCharsets.US_ASCII;
		String result= cs1.toString();
	}
}
""";

	// Keep unrelated imports and the recovery fixture's existing source shape.
	public static final String LEGACY_IMPORTS = """
package test1;

import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.io.FileNotFoundException;

public class E1 {
    void method(String filename) {
        Charset cs1= StandardCharsets.UTF_8;
        Charset cs1b= StandardCharsets.UTF_8;
        Charset cs2= StandardCharsets.UTF_16;
        Charset cs3= StandardCharsets.UTF_16BE;
        Charset cs4= StandardCharsets.UTF_16LE;
        Charset cs5= StandardCharsets.ISO_8859_1;
        Charset cs6= StandardCharsets.US_ASCII;
        String result= cs1.toString();
       }
    }
}
""";
}
