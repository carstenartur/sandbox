/* Copyright (c) 2026 Carsten Hammer. SPDX-License-Identifier: EPL-2.0 */
package org.sandbox.math.qa;

import static de.regelsuche.ast.BinaryOperator.DIV;
import static de.regelsuche.ast.BinaryOperator.POW;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.sandbox.jdt.internal.corext.fix.math.JavaProjectiveRewritePlan;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Invocation;
import org.sandbox.jdt.internal.corext.fix.math.JavaStructuralRewritePlan.Method;

import de.regelsuche.ast.BinaryExpr;
import de.regelsuche.ast.Expr;
import de.regelsuche.ast.NumberExpr;
import de.regelsuche.ast.VariableExpr;
import de.regelsuche.math.algorithms.iteration.ProjectiveHandoff;
import de.regelsuche.sdk.optimization.OptimizationBudget;
import de.regelsuche.symbol.SymbolId;

@EnabledIfSystemProperty(named = "sandbox.math.bc.projective.base", matches = ".+")
class BouncyCastleProjectivePlanTest {
    private static final String PREFIX = "org.bouncycastle.crypto.bls.";
    private static final List<String> NAMES = List.of("BLS12_381G2HashToCurve", "BLS12_381G2Point", "BLS12_381SubgroupCheck",
        "Fp2Element", "BLS12_381G1", "BLS12_381G2");
    private static final String DENOMINATOR_RECEIPT = "aa79ef7e1bd2da00bf0760d3ad9f0799da9c6aca5b64c787377ed03f2fb9647d";
    private static final String PROJECTIVE_RECEIPT = "0d3b0ed667332aab8c299502780f22c3e267003febd72124de0bc0b064699f8c";

    @Test void appliesTheAuditedG2ProposalAndReversesTheWholeGroup() throws Exception {
        var original = snapshot(System.getProperty("sandbox.math.bc.projective.base"), DENOMINATOR_RECEIPT);
        var proposal = snapshot(System.getProperty("sandbox.math.bc.projective.candidate"), PROJECTIVE_RECEIPT);
        String field = PREFIX + "Fp2Element", point = PREFIX + "BLS12_381G2Point", mapper = PREFIX + "BLS12_381G2HashToCurve";
        Method hash = new Method(mapper, "hashToCurve", List.of("byte[]"), point);
        var edges = List.of(
            new Invocation(hash, new Method(mapper, "mapToCurveAndIso3", List.of(field), point), 2, 0),
            new Invocation(hash, new Method(mapper, "mapToCurveAndIso3Jacobian", List.of(field), field + "[]"), 0, 2),
            new Invocation(hash, new Method(point, "fromJacobian", List.of(field + "[]"), point), 0, 1));

        var space = new UUID(0, 381);
        Expr xNumerator = VariableExpr.scoped(new SymbolId(space, 1));
        Expr yNumerator = VariableExpr.scoped(new SymbolId(space, 2));
        Expr denominator = VariableExpr.scoped(new SymbolId(space, 3));
        List<Expr> affine = List.of(new BinaryExpr(xNumerator, DIV, new BinaryExpr(denominator, POW, new NumberExpr(2))),
            new BinaryExpr(yNumerator, DIV, new BinaryExpr(denominator, POW, new NumberExpr(3))));
        var carrier = new ProjectiveHandoff.Candidate(denominator, List.of(2, 3), List.of(xNumerator, yNumerator));
        var result = JavaProjectiveRewritePlan.verify(original, proposal,
            List.of(System.getProperty("sandbox.math.bc.projective.classes")), 8, edges, affine, carrier,
            OptimizationBudget.DEFAULT, null);
        assertEquals(3, result.sources().changedUnits().size());
        var applied = result.sources().apply(original);
        assertEquals(proposal, applied);
        assertEquals(original, result.sources().undo(applied));
        assertEquals(List.of(2, 3), result.handoff().candidate().weights());
    }

    private static Map<String, String> snapshot(String root, String expectedReceipt) throws Exception {
        var result = new LinkedHashMap<String, String>();
        var fileHash = MessageDigest.getInstance("SHA-256");
        var receipt = MessageDigest.getInstance("SHA-256");
        for (String name : NAMES) {
            String file = PREFIX.replace('.', '/') + name + ".java";
            byte[] bytes = Files.readAllBytes(Path.of(root).resolve(file));
            receipt.update(fileHash.digest(bytes));
            result.put(file, new String(bytes, StandardCharsets.UTF_8));
        }
        assertEquals(expectedReceipt, HexFormat.of().formatHex(receipt.digest()), root);
        return result;
    }
}
