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
package org.sandbox.jdt.internal.corext.fix.helper;

import java.util.List;
import java.util.Set;

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.fix.CompilationUnitRewriteOperationsFixCore.CompilationUnitRewriteOperationWithSourceRange;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.text.edits.TextEditGroup;
import org.sandbox.jdt.internal.common.ReferenceHolder;
import org.sandbox.jdt.internal.corext.fix.JfaceCleanUpFixCore;

/**
 * Cleanup transformation for migrating from {@code Image(Device, ImageData)} to {@code Image(Device, ImageDataProvider)}.
 * 
 * <p>This helper transforms image creation patterns in Eclipse SWT code to support DPI/zoom awareness:</p>
 * <ul>
 * <li>Converts {@code new Image(device, imageData)} to {@code new Image(device, (ImageDataProvider) zoom -> {...})}</li>
 * <li>Inlines ImageData creation into lambda for zoom-aware scaling</li>
 * <li>Removes unused local ImageData variable declarations</li>
 * </ul>
 * 
 * <p><b>Migration Pattern:</b></p>
 * <pre>
 * // Before:
 * ImageData imageData = new ImageData(1, 1, 1, palette);
 * Image image = new Image(device, imageData);
 * 
 * // After:
 * Image image = new Image(device, (ImageDataProvider) zoom -> {
 *     return new ImageData(1, 1, 1, palette);
 * });
 * </pre>
 * 
 * @see org.eclipse.swt.graphics.Image
 * @see org.eclipse.swt.graphics.ImageData
 * @see org.eclipse.swt.graphics.ImageDataProvider
 * @see <a href="https://github.com/eclipse-platform/eclipse.platform.ui/pull/3004">Eclipse Platform UI PR #3004</a>
 */
public class ImageDataProviderPlugin extends
AbstractTool<ReferenceHolder<Integer, ImageDataProviderPlugin.ImageDataHolder>> {

	/** SWT Device class */
	private static final String DEVICE = "org.eclipse.swt.graphics.Device"; //$NON-NLS-1$
	
	/** SWT Image class */
	private static final String IMAGE = "org.eclipse.swt.graphics.Image"; //$NON-NLS-1$
	
	/** SWT ImageData class */
	private static final String IMAGE_DATA = "org.eclipse.swt.graphics.ImageData"; //$NON-NLS-1$
	
	/** SWT ImageDataProvider interface */
	private static final String IMAGE_DATA_PROVIDER = "org.eclipse.swt.graphics.ImageDataProvider"; //$NON-NLS-1$

	/**
	 * Holder for Image creation transformation data.
	 * Tracks Image constructor calls that can be transformed to use ImageDataProvider.
	 */
	public static class ImageDataHolder {
		/** ClassInstanceCreation of Image(Device, ImageData) to transform */
		public ClassInstanceCreation imageCreation;
		/** ImageData variable declaration statement to remove (if applicable) */
		public VariableDeclarationStatement imageDataVarDecl;
		/** ImageData initialization expression to inline into lambda */
		public Expression imageDataInitializer;
		/** Nodes that have been processed to avoid duplicate transformations */
		public Set<ASTNode> nodesprocessed;
	}

	/**
	 * Finds and identifies Image(Device, ImageData) patterns to be transformed.
	 * 
	 * <p>This method scans the compilation unit for:</p>
	 * <ul>
	 * <li>ClassInstanceCreation matching {@code new Image(device, imageData)}</li>
	 * <li>ImageData argument is a local variable with an initializer</li>
	 * <li>ImageData variable is only used in the Image constructor</li>
	 * </ul>
	 * 
	 * @param fixcore the cleanup fix core instance
	 * @param compilationUnit the compilation unit to analyze
	 * @param operations set to collect identified cleanup operations
	 * @param nodesprocessed set of nodes already processed to avoid duplicates
	 * @param createForOnlyIfVarUsed flag to control when operations are created (unused in this implementation)
	 */
	@Override
	public void find(JfaceCleanUpFixCore fixcore, CompilationUnit compilationUnit,
			Set<CompilationUnitRewriteOperationWithSourceRange> operations, Set<ASTNode> nodesprocessed,
			boolean createForOnlyIfVarUsed) {
		
		compilationUnit.accept(new ASTVisitor() {
			
			@Override
			public boolean visit(ClassInstanceCreation node) {
				// Check if this is a new Image(...) creation
				ITypeBinding typeBinding = node.getType().resolveBinding();
				if (typeBinding == null || !IMAGE.equals(typeBinding.getQualifiedName())) {
					return true;
				}
				
				// Check if it has exactly 2 arguments
				@SuppressWarnings("unchecked")
				List<Expression> arguments = node.arguments();
				if (arguments.size() != 2) {
					return true;
				}
				
				// Check first argument is Device (or subtype)
				Expression deviceArg = arguments.get(0);
				ITypeBinding deviceType = deviceArg.resolveTypeBinding();
				if (deviceType == null || !isOfType(deviceType, DEVICE)) {
					return true;
				}
				
				// Check second argument is ImageData
				Expression imageDataArg = arguments.get(1);
				ITypeBinding imageDataType = imageDataArg.resolveTypeBinding();
				if (imageDataType == null || !IMAGE_DATA.equals(imageDataType.getQualifiedName())) {
					return true;
				}
				
				// Check if imageDataArg is a SimpleName (local variable reference)
				if (!(imageDataArg instanceof SimpleName)) {
					return true;
				}
				
				SimpleName imageDataVar = (SimpleName) imageDataArg;
				
				// Try to find the variable declaration in the same method/block
				VariableDeclarationStatement varDecl = findImageDataVarDecl(imageDataVar);
				if (varDecl == null) {
					return true;
				}
				
				// Get the initializer expression
				VariableDeclarationFragment fragment = findFragment(varDecl, imageDataVar);
				if (fragment == null || fragment.getInitializer() == null) {
					return true;
				}
				
				// Check that the ImageData variable is only used once (in this Image constructor)
				// This ensures it's safe to remove the variable declaration
				int usageCount = countVariableReferences(imageDataVar);
				if (usageCount != 1) {
					return true;
				}
				
				// Create a transformation holder
				ReferenceHolder<Integer, ImageDataHolder> dataholder = new ReferenceHolder<>();
				ImageDataHolder holder = new ImageDataHolder();
				holder.imageCreation = node;
				holder.imageDataVarDecl = varDecl;
				holder.imageDataInitializer = fragment.getInitializer();
				holder.nodesprocessed = nodesprocessed;
				dataholder.put(0, holder);
				
				// Register the operation
				operations.add(fixcore.rewrite(dataholder));
				
				return true;
			}
		});
	}

	/**
	 * Finds the VariableDeclarationStatement for a given variable name in the same method/block.
	 * 
	 * @param varName the variable name to find
	 * @return the VariableDeclarationStatement or null if not found
	 */
	private VariableDeclarationStatement findImageDataVarDecl(SimpleName varName) {
		// Navigate up to find the enclosing method or block
		ASTNode parent = varName.getParent();
		while (parent != null && !(parent instanceof MethodDeclaration) && !(parent instanceof Block)) {
			parent = parent.getParent();
		}
		
		if (parent == null) {
			return null;
		}
		
		// Search for the variable declaration
		final VariableDeclarationStatement[] result = new VariableDeclarationStatement[1];
		final String varIdentifier = varName.getIdentifier();
		
		parent.accept(new ASTVisitor() {
			@Override
			public boolean visit(VariableDeclarationStatement node) {
				ITypeBinding typeBinding = node.getType().resolveBinding();
				if (typeBinding != null && IMAGE_DATA.equals(typeBinding.getQualifiedName())) {
					@SuppressWarnings("unchecked")
					List<VariableDeclarationFragment> fragments = node.fragments();
					for (VariableDeclarationFragment fragment : fragments) {
						if (fragment.getName().getIdentifier().equals(varIdentifier)) {
							result[0] = node;
							return false;
						}
					}
				}
				return true;
			}
		});
		
		return result[0];
	}

	/**
	 * Finds the VariableDeclarationFragment for a given variable name.
	 * 
	 * @param varDecl the variable declaration statement
	 * @param varName the variable name to find
	 * @return the VariableDeclarationFragment or null if not found
	 */
	private VariableDeclarationFragment findFragment(VariableDeclarationStatement varDecl, SimpleName varName) {
		@SuppressWarnings("unchecked")
		List<VariableDeclarationFragment> fragments = varDecl.fragments();
		for (VariableDeclarationFragment fragment : fragments) {
			if (fragment.getName().getIdentifier().equals(varName.getIdentifier())) {
				return fragment;
			}
		}
		return null;
	}

	/**
	 * Counts how many times a variable is referenced in the enclosing method/block.
	 * 
	 * @param varName the variable name to count references for
	 * @return the number of references to the variable
	 */
	private int countVariableReferences(SimpleName varName) {
		// Navigate up to find the enclosing method or block
		ASTNode parent = varName.getParent();
		while (parent != null && !(parent instanceof MethodDeclaration) && !(parent instanceof Block)) {
			parent = parent.getParent();
		}
		
		if (parent == null) {
			return 0;
		}
		
		// Count references to the variable
		final int[] count = new int[1];
		final String varIdentifier = varName.getIdentifier();
		
		parent.accept(new ASTVisitor() {
			@Override
			public boolean visit(SimpleName node) {
				// Check if this is a reference to our variable (not the declaration)
				if (node.getIdentifier().equals(varIdentifier)) {
					// Make sure it's actually referencing a variable, not declaring it
					ASTNode nodeParent = node.getParent();
					if (!(nodeParent instanceof VariableDeclarationFragment)) {
						count[0]++;
					}
				}
				return true;
			}
		});
		
		return count[0];
	}

	/**
	 * Rewrites AST nodes to transform Image creation to use ImageDataProvider.
	 * 
	 * <p>Performs transformation:</p>
	 * <ol>
	 * <li>Replace {@code new Image(device, imageData)} with lambda-based ImageDataProvider</li>
	 * <li>Inline ImageData creation into lambda body</li>
	 * <li>Remove now-unused ImageData variable declaration</li>
	 * <li>Add ImageDataProvider import</li>
	 * </ol>
	 * 
	 * @param upp the cleanup fix core instance
	 * @param hit the holder containing identified Image patterns to transform
	 * @param cuRewrite the compilation unit rewrite context
	 * @param group the text edit group for tracking changes
	 */
	@Override
	public void rewrite(JfaceCleanUpFixCore upp, final ReferenceHolder<Integer, ImageDataHolder> hit,
			final CompilationUnitRewrite cuRewrite, TextEditGroup group) {
		
		if (hit.isEmpty()) {
			return;
		}
		
		ImageDataHolder holder = hit.get(0);
		if (holder == null || holder.imageCreation == null) {
			return;
		}
		
		ASTRewrite rewrite = cuRewrite.getASTRewrite();
		AST ast = cuRewrite.getAST();
		
		// Get the device argument from the original Image creation
		@SuppressWarnings("unchecked")
		List<Expression> originalArgs = holder.imageCreation.arguments();
		Expression deviceArg = originalArgs.get(0);
		
		// Create lambda: zoom -> { return <imageDataInitializer>; }
		LambdaExpression lambda = ast.newLambdaExpression();
		
		// Lambda parameter: zoom
		VariableDeclarationFragment zoomParam = ast.newVariableDeclarationFragment();
		zoomParam.setName(ast.newSimpleName("zoom")); //$NON-NLS-1$
		lambda.parameters().add(zoomParam);
		
		// Lambda body: { return <imageDataInitializer>; }
		Block lambdaBody = ast.newBlock();
		ReturnStatement returnStmt = ast.newReturnStatement();
		returnStmt.setExpression((Expression) ASTNode.copySubtree(ast, holder.imageDataInitializer));
		lambdaBody.statements().add(returnStmt);
		lambda.setBody(lambdaBody);
		
		// Create cast: (ImageDataProvider) lambda
		CastExpression castExpr = ast.newCastExpression();
		castExpr.setType(ast.newSimpleType(addImport(IMAGE_DATA_PROVIDER, cuRewrite, ast)));
		castExpr.setExpression(lambda);
		
		// Create new Image creation: new Image(device, (ImageDataProvider) lambda)
		ClassInstanceCreation newImageCreation = ast.newClassInstanceCreation();
		newImageCreation.setType(ast.newSimpleType(ast.newSimpleName("Image"))); //$NON-NLS-1$
		newImageCreation.arguments().add(ASTNodes.createMoveTarget(rewrite, deviceArg));
		newImageCreation.arguments().add(castExpr);
		
		// Replace the old Image creation with the new one
		ASTNodes.replaceButKeepComment(rewrite, holder.imageCreation, newImageCreation, group);
		
		// Remove the ImageData variable declaration if it exists
		if (holder.imageDataVarDecl != null) {
			// Check if this is the only fragment in the declaration
			@SuppressWarnings("unchecked")
			List<VariableDeclarationFragment> fragments = holder.imageDataVarDecl.fragments();
			if (fragments.size() == 1) {
				// Remove the entire statement
				rewrite.remove(holder.imageDataVarDecl, group);
			} else {
				// Remove only the specific fragment (multiple variables declared)
				// Find the fragment that matches our imageData variable
				for (VariableDeclarationFragment fragment : fragments) {
					if (fragment.getInitializer() == holder.imageDataInitializer) {
						ListRewrite listRewrite = rewrite.getListRewrite(holder.imageDataVarDecl, 
								VariableDeclarationStatement.FRAGMENTS_PROPERTY);
						listRewrite.remove(fragment, group);
						break;
					}
				}
			}
		}
	}

	@Override
	public String getPreview(boolean afterRefactoring) {
		if (!afterRefactoring) {
			return """
					import org.eclipse.swt.graphics.Device;
					import org.eclipse.swt.graphics.Image;
					import org.eclipse.swt.graphics.ImageData;
					import org.eclipse.swt.graphics.PaletteData;
					public class Test {
						public Image createPattern(Device device) {
							PaletteData palette = new PaletteData(new RGB(0, 0, 0));
							ImageData imageData = new ImageData(1, 1, 1, palette);
							Image image = new Image(device, imageData);
							return image;
						}
					}
				"""; //$NON-NLS-1$
		}
		return """
				import org.eclipse.swt.graphics.Device;
				import org.eclipse.swt.graphics.Image;
				import org.eclipse.swt.graphics.ImageDataProvider;
				import org.eclipse.swt.graphics.PaletteData;
				public class Test {
					public Image createPattern(Device device) {
						PaletteData palette = new PaletteData(new RGB(0, 0, 0));
						Image image = new Image(device, (ImageDataProvider) zoom -> {
							return new ImageData(1, 1, 1, palette);
						});
						return image;
					}
				}
			"""; //$NON-NLS-1$
	}
}
