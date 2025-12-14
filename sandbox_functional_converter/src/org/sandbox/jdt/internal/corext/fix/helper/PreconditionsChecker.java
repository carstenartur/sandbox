/*******************************************************************************
c * Copyright (c) 2021 Alexandru Gyori and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Alexandru Gyori original code
 *     Carsten Hammer initial port to Eclipse
 *******************************************************************************/
package org.sandbox.jdt.internal.corext.fix.helper;


import java.util.Set;

import org.eclipse.jdt.core.dom.EnhancedForStatement;
import org.eclipse.jdt.core.dom.*;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import java.util.*;

public class PreconditionsChecker {
    private final Statement loop;
//    private final CompilationUnit compilationUnit;
    private final Set<VariableDeclarationFragment> innerVariables = new HashSet<>();
    private boolean containsBreak = false;
    private boolean containsContinue = false;
    private boolean containsReturn = false;
    private boolean throwsException = false;
    private boolean containsNEFs = false;
    private boolean iteratesOverIterable = false;

    public PreconditionsChecker(Statement loop, CompilationUnit compilationUnit) {
        this.loop = loop;
//        this.compilationUnit = compilationUnit;
        analyzeLoop();
    }

    /** (1) Prüft, ob die Schleife sicher in eine Stream-Operation umgewandelt werden kann. */
    public boolean isSafeToRefactor() {
        return !throwsException && !containsBreak && !containsContinue && !containsReturn && !containsNEFs;
    }

    /** (2) Überprüft, ob die Schleife eine Exception wirft. */
    public boolean throwsException() {
        return throwsException;
    }

    /** (3) Prüft, ob die Schleife Variablen enthält, die nicht effektiv final sind. */
    public boolean containsNEFs() {
        return containsNEFs;
    }

    /** (4) Prüft, ob die Schleife `break` enthält. */
    public boolean containsBreak() {
        return containsBreak;
    }

    /** (5) Prüft, ob die Schleife `continue` enthält. */
    public boolean containsContinue() {
        return containsContinue;
    }

    /** (6) Prüft, ob die Schleife `return` enthält. */
    public boolean containsReturn() {
        return containsReturn;
    }

    /** (7) Gibt die innerhalb der Schleife definierten Variablen zurück. */
    public Set<VariableDeclarationFragment> getInnerVariables() {
        return innerVariables;
    }

    /** (8) Überprüft, ob die Schleife über eine Iterable-Struktur iteriert. */
    public boolean iteratesOverIterable() {
        return iteratesOverIterable;
    }

    /** Methode zur Analyse der Schleife und Identifikation relevanter Elemente. */
    private void analyzeLoop() {
        loop.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationFragment node) {
                innerVariables.add(node);
                return super.visit(node);
            }

            @Override
            public boolean visit(BreakStatement node) {
                containsBreak = true;
                return super.visit(node);
            }

            @Override
            public boolean visit(ContinueStatement node) {
                containsContinue = true;
                return super.visit(node);
            }

            @Override
            public boolean visit(ReturnStatement node) {
                containsReturn = true;
                return super.visit(node);
            }

            @Override
            public boolean visit(ThrowStatement node) {
                throwsException = true;
                return super.visit(node);
            }
            
            @Override
            public boolean visit(EnhancedForStatement node) {
                iteratesOverIterable = true;
                return super.visit(node);
            }
        });
        analyzeEffectivelyFinalVariables();
    }
    
    /** Prüft, ob Variablen innerhalb der Schleife effektiv final sind. */
    private void analyzeEffectivelyFinalVariables() {
        for (VariableDeclarationFragment var : innerVariables) {
            if (!isEffectivelyFinal(var)) {
                containsNEFs = true;
                break;
            }
        }
    }
    
    /** Hilfsmethode zur Prüfung, ob eine Variable effektiv final ist. */
    private boolean isEffectivelyFinal(VariableDeclarationFragment var) {
        final boolean[] modified = {false};
        ASTNode methodBody = getEnclosingMethodBody(var);
        if (methodBody != null) {
            methodBody.accept(new ASTVisitor() {
                @Override
                public boolean visit(Assignment node) {
                    if (node.getLeftHandSide() instanceof SimpleName) {
                        SimpleName name = (SimpleName) node.getLeftHandSide();
                        if (name.getIdentifier().equals(var.getName().getIdentifier())) {
                            modified[0] = true;
                        }
                    }
                    return super.visit(node);
                }
            });
        }
        return !modified[0];
    }
    
    /** Hilfsmethode: Findet den umgebenden Methodenrumpf einer Variablendeklaration. */
    private ASTNode getEnclosingMethodBody(ASTNode node) {
        MethodDeclaration method = ASTNodes.getFirstAncestorOrNull(node, MethodDeclaration.class);
        return (method != null) ? method.getBody() : null;
    }
}