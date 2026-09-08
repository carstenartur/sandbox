/*******************************************************************************
 * Copyright (c) 2026 Carsten Hammer and others.
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0/
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.sandbox.functional.core.operation;

import java.util.Objects;
import java.util.Set;

/** A Java stream's boxing or primitive widening step, with no callback. */
public record StreamTypeConversionOp(Kind kind, String inputType) implements Operation {
    public enum Kind {
        BOXED("boxed"), AS_LONG("asLongStream"), AS_DOUBLE("asDoubleStream");
        private final String method;
        Kind(String method) { this.method = method; }
        public static Kind fromMethod(String method) {
            for (Kind kind : values()) {
                if (kind.method.equals(method)) return kind;
            }
            throw new IllegalArgumentException("Unsupported stream type conversion: " + method);
        }
    }

    public StreamTypeConversionOp {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(inputType, "input type must not be null");
        if (!Set.of("int", "long", "double").contains(inputType)
                || kind == Kind.AS_LONG && !"int".equals(inputType)
                || kind == Kind.AS_DOUBLE && "double".equals(inputType)) {
            throw new IllegalArgumentException("Invalid " + kind.method + " input: " + inputType);
        }
    }

    public String outputType() {
        return switch (kind) {
            case AS_LONG -> "long";
            case AS_DOUBLE -> "double";
            case BOXED -> switch (inputType) {
                case "int" -> "java.lang.Integer";
                case "long" -> "java.lang.Long";
                default -> "java.lang.Double";
            };
        };
    }

    @Override
    public String expression() { return null; }

    @Override
    public String operationType() { return kind.method; }
}
