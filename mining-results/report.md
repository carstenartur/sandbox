# Refactoring Mining Report — 2026-07-01

## Summary
| Eclipse Project | Files | Matches | Rules |
|----------------|-------|---------|-------|
| eclipse.jdt.core | 227 | 219 | 9 |
| eclipse.jdt.ui | 1470 | 80 | 6 |
| eclipse.platform.ui | 1147 | 130 | 8 |
| eclipse.platform | 313 | 9 | 3 |
| eclipse.platform.text | 0 | 0 | 0 |
| eclipse.platform.debug | 0 | 0 | 0 |
| sandbox | 937 | 89 | 5 |

## Details
### eclipse.jdt.core
#### Rule: `collections` → `collections7`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CatchClause.java:57` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AssertStatement.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/UsesDirective.java:47` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SingleMemberAnnotation.java:57` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodRef.java:66` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ArrayCreation.java:74` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/WhileStatement.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/QualifiedType.java:113` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/QualifiedType.java:119` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Javadoc.java:84` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Javadoc.java:90` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Javadoc.java:95` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ConstructorInvocation.java:65` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ConstructorInvocation.java:70` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/LambdaExpression.java:74` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ArrayInitializer.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Dimension.java:55` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/EnhancedForStatement.java:64` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PatternInstanceofExpression.java:71` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PatternInstanceofExpression.java:77` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TextElement.java:52` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/IfStatement.java:62` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/JavaDocTextElement.java:50` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/LabeledStatement.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Modifier.java:368` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/EnumConstantDeclaration.java:86` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Initializer.java:79` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Initializer.java:86` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/JavaDocRegion.java:83` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ConditionalExpression.java:63` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BooleanLiteral.java:50` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/InfixExpression.java:217` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/InstanceofExpression.java:55` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationFragment.java:85` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationFragment.java:92` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationTypeDeclaration.java:84` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeParameter.java:68` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeParameter.java:74` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BlockComment.java:50` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationExpression.java:88` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationExpression.java:95` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ExpressionMethodReference.java:60` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Assignment.java:184` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SingleVariableDeclaration.java:132` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SingleVariableDeclaration.java:141` — `new ArrayList(7)` → `new java.util.ArrayList<>(7)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SingleVariableDeclaration.java:151` — `new ArrayList(8)` → `new java.util.ArrayList<>(8)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SimpleName.java:71` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SimpleName.java:76` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ReturnStatement.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/FieldDeclaration.java:98` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/FieldDeclaration.java:106` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/EmptyStatement.java:42` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/WildcardType.java:72` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/WildcardType.java:78` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodInvocation.java:79` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodInvocation.java:86` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/YieldStatement.java:52` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodDeclaration.java:225` — `new ArrayList(10)` → `new java.util.ArrayList<>(10)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodDeclaration.java:238` — `new ArrayList(11)` → `new java.util.ArrayList<>(11)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodDeclaration.java:252` — `new ArrayList(13)` → `new java.util.ArrayList<>(13)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodDeclaration.java:268` — `new ArrayList(14)` → `new java.util.ArrayList<>(14)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MarkerAnnotation.java:47` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ParameterizedType.java:61` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TagProperty.java:87` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NullLiteral.java:37` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTNode.java:1501` — `new ArrayList(0)` → `new java.util.ArrayList<>(0)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTNode.java:1684` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTNode.java:2155` — `new ArrayList(propertyList.size())` → `new java.util.ArrayList<>(propertyList.size())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTNode.java:3231` — `new ArrayList(nodes.size())` → `new java.util.ArrayList<>(nodes.size())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationStatement.java:91` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/VariableDeclarationStatement.java:98` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ContinueStatement.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperMethodReference.java:61` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/FieldAccess.java:86` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NameQualifiedType.java:72` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CreationReference.java:55` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TryStatement.java:104` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TryStatement.java:111` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TryStatement.java:119` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecordPattern.java:56` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ThisExpression.java:54` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/GuardedPattern.java:65` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/QualifiedName.java:63` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ExportsDirective.java:53` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AST.java:2499` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/UnionType.java:50` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodRefParameter.java:80` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MethodRefParameter.java:86` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleDeclaration.java:74` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SwitchStatement.java:61` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RequiresDirective.java:52` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TagElement.java:83` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TagElement.java:89` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TextBlock.java:50` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ClassInstanceCreation.java:106` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ClassInstanceCreation.java:114` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ProvidesDirective.java:53` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CharacterLiteral.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SwitchExpression.java:60` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CaseDefaultExpression.java:38` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/Block.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:338` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:500` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:538` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MemberRef.java:59` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ArrayAccess.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ThrowStatement.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeDeclarationStatement.java:73` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeDeclarationStatement.java:78` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ExpressionStatement.java:52` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleModifier.java:190` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperMethodInvocation.java:80` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperMethodInvocation.java:87` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/LineComment.java:47` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeDeclaration.java:172` — `new ArrayList(8)` → `new java.util.ArrayList<>(8)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeDeclaration.java:183` — `new ArrayList(9)` → `new java.util.ArrayList<>(9)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeDeclaration.java:195` — `new ArrayList(10)` → `new java.util.ArrayList<>(10)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:297` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PrefixExpression.java:150` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NullPattern.java:37` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/MemberValuePair.java:58` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ImportDeclaration.java:88` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ImportDeclaration.java:94` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ImportDeclaration.java:101` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PrimitiveType.java:190` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PrimitiveType.java:195` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ModuleQualifiedName.java:58` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SimpleType.java:74` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SimpleType.java:79` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/RecordDeclaration.java:131` — `new ArrayList(8)` → `new java.util.ArrayList<>(8)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SynchronizedStatement.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnnotationTypeMemberDeclaration.java:84` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/EitherOrMultiPattern.java:51` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/EnumDeclaration.java:94` — `new ArrayList(6)` → `new java.util.ArrayList<>(6)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperConstructorInvocation.java:73` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperConstructorInvocation.java:79` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ForStatement.java:86` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BreakStatement.java:66` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PostfixExpression.java:134` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/DoStatement.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnit.java:119` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnit.java:126` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypePattern.java:63` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypePattern.java:68` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeMethodReference.java:60` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ArrayType.java:89` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ArrayType.java:94` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/AnonymousClassDeclaration.java:53` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageDeclaration.java:75` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/PackageDeclaration.java:80` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SwitchCase.java:75` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SwitchCase.java:80` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/OpensDirective.java:52` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NumberLiteral.java:47` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/StringLiteral.java:48` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/TypeLiteral.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/NormalAnnotation.java:52` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/IntersectionType.java:50` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/SuperFieldAccess.java:62` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ParenthesizedExpression.java:49` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CastExpression.java:56` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:384` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:530` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:610` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:651` — `new ArrayList(2)` → `new java.util.ArrayList<>(2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:671` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:728` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:762` — `new ArrayList(childEvents.length)` → `new java.util.ArrayList<>(childEvents.length)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:791` — `new ArrayList(childEvents.length)` → `new java.util.ArrayList<>(childEvents.length)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ASTRewriteAnalyzer.java:1546` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/SourceModifier.java:46` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ListRewriteEvent.java:39` — `new ArrayList(originalNodes)` → `new java.util.ArrayList<>(originalNodes)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ListRewriteEvent.java:47` — `new ArrayList(children.length * 2)` → `new java.util.ArrayList<>(children.length * 2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ListRewriteEvent.java:48` — `new ArrayList(children.length * 2)` → `new java.util.ArrayList<>(children.length * 2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ListRewriteEvent.java:61` — `new ArrayList(nNodes * 2)` → `new java.util.ArrayList<>(nNodes * 2)`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ListRewriteEvent.java:103` — `new ArrayList(entries.size())` → `new java.util.ArrayList<>(entries.size())`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ASTRewriteFormatter.java:58` — `new ArrayList()` → `new java.util.ArrayList<>()`

#### Rule: `collections` → `collections4`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:115` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:153` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:164` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:317` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:5297` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:5304` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:719` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:848` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/NodeInfoStore.java:110` — `new HashSet()` → `new java.util.HashSet<>()`

#### Rule: `collections` → `collections5`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:115` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:153` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:164` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/BindingComparator.java:317` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:5297` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/ASTConverter.java:5304` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:719` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:848` — `new HashSet()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/NodeInfoStore.java:110` — `new HashSet()` → `new java.util.HashSet<>()`

#### Rule: `collections` → `collections6`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:338` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:500` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/rewrite/ImportRewrite.java:538` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/CompilationUnitResolver.java:297` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:530` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:610` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/RewriteEventStore.java:728` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ASTRewriteAnalyzer.java:1546` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/SourceModifier.java:46` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/ASTRewriteFormatter.java:58` — `new ArrayList()` → `new java.util.ArrayList<>()`

#### Rule: `collections` → `collections8`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/InternalASTRewrite.java:59` — `new Hashtable()` → `new java.util.Hashtable<>()`

#### Rule: `collections` → `collections9`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/core/dom/InternalASTRewrite.java:59` — `new Hashtable()` → `new java.util.Hashtable<>()`

#### Rule: `collections` → `collections3`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ConflictIdentifier.java:150` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteConfiguration.java:197` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteAnalyzer.java:627` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteAnalyzer.java:640` — `Collections.emptySet()` → `java.util.Set.of()`

#### Rule: `collections` → `collections2`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/RemovedImportCommentReassigner.java:117` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportEditor.java:160` — `Collections.emptyMap()` → `java.util.Map.of()`

#### Rule: `collections` → `collections1`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportEditor.java:302` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportEditor.java:419` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportEditor.java:482` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteConfiguration.java:227` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteAnalyzer.java:141` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.core/dom/org/eclipse/jdt/internal/core/dom/rewrite/imports/ImportRewriteAnalyzer.java:174` — `Collections.emptyList()` → `java.util.List.of()`

### eclipse.jdt.ui
#### Rule: `collections` → `collections1`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/ExternalizeStringsAction.java:299` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/SelfEncapsulateFieldAction.java:138` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/ui/actions/RefreshAction.java:88` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaMethodParameterCodeMiningProvider.java:67` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaMethodParameterCodeMiningProvider.java:77` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaElementCodeMiningProvider.java:85` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaElementCodeMiningProvider.java:102` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/codemining/JavaElementCodeMiningProvider.java:112` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/javaeditor/CompilationUnitDocumentProvider.java:466` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetModel.java:250` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDialog.java:811` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleSelectionDialog.java:186` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ExternalAnnotationsAttachmentBlock.java:447` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesAdapter.java:455` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/CPListElement.java:465` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/buildpaths/ModuleDependenciesPage.java:764` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/javadoc/HTMLTagCompletionProposalComputer.java:127` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/javadoc/HTMLTagCompletionProposalComputer.java:137` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/javadoc/HTMLTagCompletionProposalComputer.java:140` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/javadoc/HTMLTagCompletionProposalComputer.java:226` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/WordCompletionProposalComputer.java:109` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/WordCompletionProposalComputer.java:121` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/correction/ClasspathFixProcessorDescriptor.java:78` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/JavaCompletionProposalComputer.java:208` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/JavaCompletionProposalComputer.java:220` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/JavaCompletionProposalComputer.java:226` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ChainCompletionProposalComputer.java:72` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ChainCompletionProposalComputer.java:75` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ChainCompletionProposalComputer.java:161` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ChainCompletionProposalComputer.java:217` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/AbstractTemplateCompletionProposalComputer.java:62` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/AbstractTemplateCompletionProposalComputer.java:68` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/AbstractTemplateCompletionProposalComputer.java:73` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/AbstractTemplateCompletionProposalComputer.java:125` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/ContentAssistHistory.java:309` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:343` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:349` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:375` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:382` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:397` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:403` — `Collections.emptyList()` → `java.util.List.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/java/CompletionProposalComputerDescriptor.java:427` — `Collections.emptyList()` → `java.util.List.of()`

#### Rule: `collections` → `collections7`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/workingsets/WorkingSetConfigurationDialog.java:343` — `new ArrayList(Arrays.asList(checked))` → `new java.util.ArrayList<>(Arrays.asList(checked))`

#### Rule: `collections` → `collections3`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/wizards/importer/JDTProjectNatureImportConfigurator.java:87` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/StringBuilderCleanUp.java:456` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:88` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:91` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:94` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:132` — `Collections.emptySet()` → `java.util.Set.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/SpellCheckEngine.java:135` — `Collections.emptySet()` → `java.util.Set.of()`

#### Rule: `collections` → `collections2`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/MapCloningCleanUp.java:65` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/MapMethodCleanUp.java:57` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/SingleUsedFieldCleanUp.java:100` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/PullUpAssignmentCleanUp.java:71` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/ReduceIndentationCleanUp.java:150` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UselessContinueCleanUp.java:62` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/LambdaExpressionAndMethodRefCleanUp.java:42` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/TernaryOperatorCleanUp.java:56` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/OperandFactorizationCleanUp.java:73` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/StringBuilderCleanUp.java:92` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/StrictlyEqualOrDifferentCleanUp.java:62` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/LazyLogicalCleanUp.java:53` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UnloopedWhileCleanUp.java:187` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/ObjectsEqualsCleanUp.java:66` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/NumberSuffixCleanUp.java:52` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/MergeConditionalBlocksCleanUp.java:60` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/InstanceofCleanUp.java:56` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UnreachableBlockCleanUp.java:56` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/HashCleanUp.java:91` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/PushDownNegationCleanUp.java:58` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/PrimitiveSerializationCleanUp.java:61` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/JoinCleanUp.java:85` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UnboxingCleanUp.java:69` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/TryWithResourceCleanUp.java:38` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/PrimitiveParsingCleanUp.java:58` — `Collections.emptyMap()` → `java.util.Map.of()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/fix/UselessReturnCleanUp.java:58` — `Collections.emptyMap()` → `java.util.Map.of()`

#### Rule: `collections` → `collections4`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultSpellChecker.java:130` — `new HashSet<ISpellDictionary>()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultSpellChecker.java:135` — `new HashSet<String>()` → `new java.util.HashSet<>()`

#### Rule: `collections` → `collections5`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultSpellChecker.java:130` — `new HashSet<ISpellDictionary>()` → `new java.util.HashSet<>()`
- `org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/text/spelling/engine/DefaultSpellChecker.java:135` — `new HashSet<String>()` → `new java.util.HashSet<>()`

### eclipse.platform.ui
#### Rule: `collections` → `collections3`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchActivityHelper.java:235` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchActivityHelper.java:288` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchActivityHelper.java:292` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchActivityHelper.java:335` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchActivityHelper.java:378` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/WorkbenchTriggerPointAdvisor.java:101` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/activities/ActivityCategoryPreferencePage.java:304` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessDialog.java:453` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessDialog.java:457` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/MutableActivityManager.java:634` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/MutableActivityManager.java:638` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/MutableActivityManager.java:711` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/Identifier.java:35` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/PopupMenuExtender.java:196` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressManager.java:209` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/preferences/WorkbenchPreferenceExtensionNode.java:104` — `Collections.emptySet()` → `java.util.Set.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/preferences/PropertyListenerList.java:76` — `Collections.emptySet()` → `java.util.Set.of()`

#### Rule: `collections` → `collections7`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/operations/NonLocalUndoUserApprover.java:260` — `new ArrayList(elements.length)` → `new java.util.ArrayList<>(elements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/TwoPaneElementSelector.java:213` — `new ArrayList(indices.length * 5)` → `new java.util.ArrayList<>(indices.length * 5)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/SelectionDialog.java:46` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/SelectionDialog.java:164` — `new ArrayList(selectedElements.length)` → `new java.util.ArrayList<>(selectedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/part/MultiPageEditorSite.java:531` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/part/MultiPageEditorSite.java:540` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/model/AdaptableList.java:45` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/model/AdaptableList.java:55` — `new ArrayList(initialCapacity)` → `new java.util.ArrayList<>(initialCapacity)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/SlaveCommandService.java:54` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchWindowConfigurer.java:111` — `new ArrayList(3)` → `new java.util.ArrayList<>(3)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/statushandlers/StatusHandlerDescriptorsMap.java:55` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:148` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:162` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:232` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:257` — `new ArrayList(seen.size())` → `new java.util.ArrayList<>(seen.size())`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:314` — `new ArrayList(5)` → `new java.util.ArrayList<>(5)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:374` — `new ArrayList(Arrays.asList(Platform.getAdapterManager().computeAdapterTypes(...` → `new java.util.ArrayList<>(Arrays.asList(Platform.getAdapterManager().computeA...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:378` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:398` — `new ArrayList(contributors)` → `new java.util.ArrayList<>(contributors)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:515` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:535` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:574` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:585` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:619` — `new ArrayList(contributors)` → `new java.util.ArrayList<>(contributors)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:645` — `new ArrayList(set.size())` → `new java.util.ArrayList<>(set.size())`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:673` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:686` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:708` — `new ArrayList(otherClasses)` → `new java.util.ArrayList<>(otherClasses)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:752` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:753` — `new ArrayList(4)` → `new java.util.ArrayList<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:857` — `new ArrayList(result)` → `new java.util.ArrayList<>(result)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:875` — `new ArrayList(objects.size())` → `new java.util.ArrayList<>(objects.size())`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:163` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:226` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/WizardContentProvider.java:38` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/WizardContentProvider.java:50` — `new ArrayList(children.length)` → `new java.util.ArrayList<>(children.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ViewActionBuilder.java:58` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ProductProperties.java:85` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/util/Util.java:220` — `new ArrayList(list)` → `new java.util.ArrayList<>(list)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/util/Util.java:434` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/decorators/LightweightDecoratorManager.java:319` — `new ArrayList(1)` → `new java.util.ArrayList<>(1)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:199` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:33` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:35` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:37` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/SelectionConversionService.java:56` — `new ArrayList()` → `new java.util.ArrayList<>()`

#### Rule: `collections` → `collections6`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/dialogs/SelectionDialog.java:46` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/model/AdaptableList.java:45` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/SlaveCommandService.java:54` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/statushandlers/StatusHandlerDescriptorsMap.java:55` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:232` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:378` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:535` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:574` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:585` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:686` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:163` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:226` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/WizardContentProvider.java:38` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ViewActionBuilder.java:58` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ProductProperties.java:85` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/util/Util.java:434` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:199` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:33` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:35` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/SlaveEvaluationService.java:37` — `new ArrayList()` → `new java.util.ArrayList<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/SelectionConversionService.java:56` — `new ArrayList()` → `new java.util.ArrayList<>()`

#### Rule: `collections` → `collections1`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:436` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:438` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessContents.java:497` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/quickaccess/QuickAccessDialog.java:402` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:22` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:25` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:27` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:33` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:35` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/activities/AbstractActivityRegistry.java:37` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/databinding/MultiSelectionProperty.java:47` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:266` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/registry/WorkingSetRegistry.java:143` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressManager.java:942` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/ProgressManager.java:946` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/FinishedJobs.java:235` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/FinishedJobs.java:241` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/FinishedJobs.java:245` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/progress/FinishedJobs.java:266` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/CloseAllHandler.java:143` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/SelectionAdapterFactory.java:34` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:2901` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/WorkbenchPage.java:4523` — `Collections.emptyList()` → `java.util.List.of()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/e4/compatibility/ModeledPageLayout.java:88` — `Collections.emptyList()` → `java.util.List.of()`

#### Rule: `collections` → `collections5`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/CommandImageManager.java:182` — `new HashSet(3)` → `new java.util.HashSet<>(3)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/SlaveCommandService.java:61` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:104` — `new HashSet(5)` → `new java.util.HashSet<>(5)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:641` — `new HashSet(list)` → `new java.util.HashSet<>(list)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:847` — `new HashSet(4)` → `new java.util.HashSet<>(4)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PreferenceNodeFilter.java:28` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/util/Util.java:257` — `new HashSet(set)` → `new java.util.HashSet<>(set)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:107` — `new HashSet(oldElements.size() + selectedElements.length)` → `new java.util.HashSet<>(oldElements.size() + selectedElements.length)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:226` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/SelectWorkingSetsAction.java:66` — `new HashSet(Arrays.asList(getWindow().getActivePage().getWorkingSets()))` → `new java.util.HashSet<>(Arrays.asList(getWindow().getActivePage().getWorkingS...`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/themes/CascadingMap.java:43` — `new HashSet(base.keySet())` → `new java.util.HashSet<>(base.keySet())`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ActionSetManager.java:61` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/MenuSourceProvider.java:52` — `new HashSet()` → `new java.util.HashSet<>()`

#### Rule: `collections` → `collections4`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/commands/SlaveCommandService.java:61` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PreferenceNodeFilter.java:28` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/actions/ModifyWorkingSetDelegate.java:226` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ActionSetManager.java:61` — `new HashSet()` → `new java.util.HashSet<>()`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/services/MenuSourceProvider.java:52` — `new HashSet()` → `new java.util.HashSet<>()`

#### Rule: `collections` → `collections9`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:103` — `new Hashtable(5)` → `new java.util.Hashtable<>(5)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/ObjectContributorManager.java:337` — `new Hashtable(5)` → `new java.util.Hashtable<>(5)`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:154` — `new Hashtable()` → `new java.util.Hashtable<>()`

#### Rule: `collections` → `collections8`
- `bundles/org.eclipse.ui.workbench/eclipseui/org/eclipse/ui/internal/dialogs/PropertyPageContributorManager.java:154` — `new Hashtable()` → `new java.util.Hashtable<>()`

### eclipse.platform
#### Rule: `collections` → `collections2`
- `runtime/bundles/org.eclipse.core.runtime/src/org/eclipse/core/internal/runtime/InternalPlatform.java:300` — `Collections.emptyMap()` → `java.util.Map.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/Resource.java:745` — `Collections.emptyMap()` → `java.util.Map.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/events/BuildManager.java:694` — `Collections.emptyMap()` → `java.util.Map.of()`

#### Rule: `collections` → `collections1`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/CheckMissingNaturesListener.java:285` — `Collections.emptyList()` → `java.util.List.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/CheckMissingNaturesListener.java:290` — `Collections.emptyList()` → `java.util.List.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/CheckMissingNaturesListener.java:301` — `Collections.emptyList()` → `java.util.List.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/CheckMissingNaturesListener.java:307` — `Collections.emptyList()` → `java.util.List.of()`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ProjectDescription.java:299` — `Collections.emptyList()` → `java.util.List.of()`

#### Rule: `collections` → `collections3`
- `resources/bundles/org.eclipse.core.resources/src/org/eclipse/core/internal/resources/ComputeProjectOrder.java:711` — `Collections.emptySet()` → `java.util.Set.of()`

### sandbox
#### Rule: `collections` → `collections3`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/colum/ConflictHighlightingLabelProvider.java:35` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/colum/ConflictHighlightingLabelProvider.java:51` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox_usage_view/src/org/sandbox/jdt/ui/helper/views/colum/ConflictHighlightingLabelProvider.java:67` — `Collections.emptySet()` → `java.util.Set.of()`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:938` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/PomFileStrategy.java:43` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/GenericTextFileStrategy.java:41` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/GenericTextFileStrategy.java:46` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/PropertiesFileStrategy.java:55` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/JavaFileStrategy.java:49` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/PluginXmlFileStrategy.java:41` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/search/strategies/XmlFileStrategy.java:45` — `Collections.emptySet()` → `java.util.Set.of()`
- `sandbox-jgit-storage-hibernate/src/org/eclipse/jgit/storage/hibernate/objects/HibernateObjDatabase.java:57` — `Collections.emptySet()` → `java.util.Set.of()`

#### Rule: `collections` → `collections2`
- `sandbox_xml_cleanup/src/org/sandbox/jdt/internal/ui/fix/XMLCleanUp.java:29` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_int_to_enum/src/org/sandbox/jdt/internal/ui/fix/IntToEnumCleanUp.java:30` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/HintFileCleanUp.java:36` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/ObsoleteCollectionCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/ShiftOutOfRangeCleanUp.java:35` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/WrongStringComparisonCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/StringSimplificationCleanUp.java:35` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/OverridableCallInConstructorCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/SystemOutCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/MissingHashCodeCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/PrintStackTraceCleanUp.java:34` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_triggerpattern/src/org/sandbox/jdt/internal/ui/fix/ThreadingCleanUp.java:36` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_use_general_type/src/org/sandbox/jdt/internal/ui/fix/UseGeneralTypeCleanUp.java:30` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Match.java:72` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/GuardContext.java:52` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/GuardContext.java:65` — `java.util.Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/PatternIndex.java:144` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_functional_converter/src/org/sandbox/jdt/internal/ui/fix/UseFunctionalCallCleanUp.java:28` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_jface_cleanup/src/org/sandbox/jdt/internal/ui/fix/JFaceCleanUp.java:29` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_test/src/org/sandbox/jdt/triggerpattern/test/GuardExpressionTest.java:539` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_common_test/src/org/sandbox/jdt/triggerpattern/test/WorkspaceHintFileTest.java:212` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_tools/src/org/sandbox/jdt/internal/ui/fix/UseIteratorToForLoopCleanUp.java:29` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_junit_cleanup/src/org/sandbox/jdt/internal/ui/fix/JUnitCleanUp.java:49` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_encoding_quickfix/src/org/sandbox/jdt/internal/ui/fix/UseExplicitEncodingCleanUp.java:32` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/UpdateNeededSearchPage.java:379` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/UpdateNeededSearchPage.java:398` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_method_reuse/src/org/sandbox/jdt/internal/ui/fix/MethodReuseCleanUp.java:30` — `Collections.emptyMap()` → `java.util.Map.of()`
- `sandbox_platform_helper/src/org/sandbox/jdt/internal/ui/fix/SimplifyPlatformStatusCleanUp.java:62` — `Collections.emptyMap()` → `java.util.Map.of()`

#### Rule: `collections` → `collections1`
- `sandbox_css_cleanup/src/org/sandbox/jdt/internal/css/core/CSSValidationResult.java:29` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_css_cleanup_test/src/org/sandbox/jdt/internal/css/core/CSSValidationResultTest.java:35` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/DryRunReporter.java:88` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/TransformationRule.java:90` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/Match.java:121` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/BatchTransformationProcessor.java:118` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/BatchTransformationProcessor.java:123` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/api/PatternIndex.java:128` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/EmbeddedJavaCompiler.java:103` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/EmbeddedJavaCompiler.java:104` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common_core/src/main/java/org/sandbox/jdt/triggerpattern/internal/EmbeddedJavaCompiler.java:244` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/cleanup/DslPluginRegistry.java:98` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:328` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:332` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:464` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:472` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:486` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:503` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_common/src/org/sandbox/jdt/triggerpattern/internal/HintFileRegistry.java:507` — `Collections.emptyList()` → `java.util.List.of()`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:1315` — `Collections.emptyList()` → `java.util.List.of()`
- `org/eclipse/jdt/internal/corext/dom/ASTNodes.java:2344` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/RepoEntry.java:26` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/RepoEntry.java:34` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/RepoEntry.java:58` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/MiningConfig.java:47` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/MiningConfig.java:55` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/MiningConfig.java:216` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_core/src/main/java/org/sandbox/mining/core/config/MiningConfig.java:240` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/RepoEntry.java:26` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/RepoEntry.java:34` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/RepoEntry.java:58` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/MiningConfig.java:31` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/MiningConfig.java:32` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/MiningConfig.java:128` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_mining_cli/src/main/java/org/sandbox/mining/config/MiningConfig.java:136` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/SemanticCodeSearchResult.java:38` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/SemanticCodeSearchResult.java:57` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:275` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:281` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:284` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:292` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:298` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:301` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:309` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:315` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/SemanticSearchClient.java:318` — `Collections.emptyList()` → `java.util.List.of()`
- `sandbox_extra_search/src/org/sandbox/jdt/internal/ui/search/gitindex/EGitRepositoryTracker.java:119` — `Collections.emptyList()` → `java.util.List.of()`

#### Rule: `collections` → `collections6`
- `sandbox-ast-api/src/main/java/org/sandbox/ast/api/expr/InfixExpr.java:127` — `new java.util.ArrayList<ASTExpr>()` → `new java.util.ArrayList<>()`

#### Rule: `collections` → `collections7`
- `sandbox-ast-api/src/main/java/org/sandbox/ast/api/expr/InfixExpr.java:127` — `new java.util.ArrayList<ASTExpr>()` → `new java.util.ArrayList<>()`


<!-- report-hash: 264e1fa193ff1838bc45dd5abfd90f2933d2743444dec5cf2bb4c3eaffcc5242 -->
