package org.sandbox.jdt.internal.corext.fix.helper;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WhileStatement;

public class Hit {

	public boolean self;
	public VariableDeclarationStatement iteratordeclaration;
//	public String iteratorvariablename;
	public SimpleName collectionsimplename;
	public String loopvarname;
	public VariableDeclarationStatement loopvardeclaration;
	public WhileStatement whilestatement;

}
