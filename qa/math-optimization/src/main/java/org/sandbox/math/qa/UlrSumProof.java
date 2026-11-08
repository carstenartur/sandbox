/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Set;
import java.util.UUID;
import org.sandbox.functional.core.model.FunctionalExpression.TypedBody;
import org.sandbox.functional.core.model.NumericIteration.CountedSum;
import org.sandbox.functional.core.model.UlrExpression;
import org.sandbox.jdt.internal.corext.fix.math.UlrSumProjection;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.NumberExpr;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.sdk.optimization.iteration.PolynomialSumOptimizer;
import de.regelsuche.sdk.optimization.iteration.PolynomialSumVerifier;
import de.regelsuche.symbol.SymbolId;

/** Versioned, local transport for the Roslyn adapter's counted integer fragment. */
public final class UlrSumProof {
    private static final int MAX_REQUEST_BYTES = 4_096;
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final OptimizationBudget BUDGET = new OptimizationBudget(100_000, 10, 10, 5_000);

    private UlrSumProof() { }

    public static void main(String[] args) throws IOException {
        if (args.length != 0) throw new IllegalArgumentException("UlrSumProof reads the request from stdin");
        byte[] bytes = System.in.readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) throw unsupported();
        System.out.println(verify(JSON.readTree(bytes)));
    }

    private static ObjectNode verify(JsonNode request) {
        if (request == null || !request.isObject() || request.size() != 8
                || !integer(request.path("version"), 1) || !request.path("kind").asText().equals("counted-sum")
                || !integer(request.path("bits"), 32) || !integer(request.path("start"), 0)
                || !integer(request.path("stride"), 1) || !integer(request.path("initial"), 0)
                || !request.path("term").isObject() || request.path("term").size() != 1
                || !request.path("term").path("op").asText().equals("index")) {
            throw unsupported();
        }
        var overflow = switch (request.path("overflow").asText()) {
            case "wrap" -> UlrExpression.Overflow.WRAP;
            case "checked" -> UlrExpression.Overflow.CHECKED;
            default -> throw unsupported();
        };
        var symbol = new SymbolId(new UUID(0, 0), 1);
        var index = new UlrExpression.Read(symbol.canonicalText(), UlrExpression.Type.INT32);
        var zero = new UlrExpression.Constant(BigInteger.ZERO, UlrExpression.Type.INT32);
        var sum = new CountedSum(new TypedBody(index, index), zero, BigInteger.ONE, zero, overflow);
        var source = UlrSumProjection.project(sum, Set.of(), () -> {});
        var result = new PolynomialSumOptimizer().search(source, BUDGET, () -> false);
        var plan = result.plan().orElseThrow(() -> new IllegalArgumentException(result.reason()));
        var proof = new PolynomialSumVerifier().verify(source, plan, BUDGET, () -> false);
        if (!proof.verified()) throw new IllegalArgumentException(proof.reason());
        var response = JSON.createObjectNode();
        response.put("version", 1);
        response.set("request", request);
        response.put("initial", constant(plan.initial()));
        var coefficients = response.putArray("coefficients");
        for (var coefficient : plan.coefficients()) coefficients.add(constant(coefficient));
        return response;
    }

    private static boolean integer(JsonNode node, int expected) {
        return node.isIntegralNumber() && node.canConvertToInt() && node.intValue() == expected;
    }

    private static BigInteger constant(Expr expression) {
        if (expression instanceof NumberExpr number && number.value().isInteger()) return number.value().numerator();
        throw unsupported();
    }

    private static IllegalArgumentException unsupported() {
        return new IllegalArgumentException("Unsupported ULR counted sum transport");
    }
}
