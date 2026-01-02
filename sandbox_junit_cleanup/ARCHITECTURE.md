# JUnit Cleanup Plugin Architecture

## Overview

The JUnit cleanup plugin provides automated migration from JUnit 3/4 to JUnit 5 (Jupiter). This document describes the architecture after the refactoring that extracted helper classes from the monolithic `AbstractTool` class.

## Design Goals

1. **Separation of Concerns**: Each helper class handles a specific aspect of JUnit migration
2. **Maintainability**: Reduced code complexity through smaller, focused classes
3. **Reusability**: Helper classes can be used independently by different cleanup tools
4. **Testability**: Smaller classes are easier to test in isolation
5. **Backward Compatibility**: Public API remains unchanged

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                      AbstractTool                            │
│                   (Orchestration Layer)                      │
│  - find() - Identifies transformation opportunities          │
│  - rewrite() - Applies AST transformations                   │
│  - process2Rewrite() - Coordinates helper classes            │
└───────────────────────┬─────────────────────────────────────┘
                        │
                        │ delegates to
                        ↓
        ┌───────────────────────────────────────┐
        │         Helper Classes                 │
        │      (Specialized Services)            │
        └───────────────────────────────────────┘
                        │
        ┌───────────────┴───────────────────────────────────┐
        │                                                     │
        ↓                                                     ↓
┌──────────────────┐                              ┌──────────────────┐
│ JUnitConstants   │                              │  ImportHelper    │
│ - Annotations    │                              │  - Add imports   │
│ - FQCNs          │                              │  - Transform     │
│ - Method sets    │                              │    imports       │
└──────────────────┘                              └──────────────────┘
        │                                                     │
        ↓                                                     ↓
┌──────────────────┐                              ┌──────────────────┐
│ AssertionRefact. │                              │ LifecycleMethod  │
│ - Reorder params │                              │    Adapter       │
│ - Message-last   │                              │  - @Before/@After│
│                  │                              │  - Annotations   │
└──────────────────┘                              └──────────────────┘
        │                                                     │
        ↓                                                     ↓
┌──────────────────┐                              ┌──────────────────┐
│ ExternalResource │                              │ TestNameRefact.  │
│   Refactorer     │                              │  - TestName rule │
│ - Callback migr. │                              │  - TestInfo param│
│ - Before/AfterAll│                              │  - Method inv.   │
└──────────────────┘                              └──────────────────┘
        │
        ↓
┌──────────────────┐
│ DocumentHelper   │
│ - AST operations │
│ - Change creation│
└──────────────────┘
```

## Core Components

### 1. AbstractTool (Orchestrator)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.AbstractTool`

**Responsibilities**:
- Defines the public API for JUnit cleanup operations
- Coordinates helper classes to perform complex transformations
- Maintains the overall transformation workflow

**Key Methods**:
- `find()` - Identifies transformation opportunities in the AST
- `rewrite()` - Applies transformations using AST rewriting
- `process2Rewrite()` - Orchestrates helper classes for complex transformations

**Size**: 502 lines (reduced from 1,629 lines - 69% reduction)

### 2. JUnitConstants (Data)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.JUnitConstants`

**Responsibilities**:
- Centralized repository for all JUnit-related constants
- Provides annotation names, fully qualified class names (FQCNs)
- Defines method name sets for different assertion types

**Key Constants**:
- `ORG_JUNIT_TEST`, `ORG_JUNIT_BEFORE`, `ORG_JUNIT_AFTER` - JUnit 4 annotations
- `ANNOTATION_TEST`, `ANNOTATION_BEFORE_EACH`, `ANNOTATION_AFTER_EACH` - JUnit 5 annotations
- `ONEPARAM_ASSERTIONS`, `TWOPARAM_ASSERTIONS` - Method classification sets
- `METHOD_BEFORE_EACH`, `METHOD_AFTER_EACH`, `METHOD_BEFORE_ALL`, `METHOD_AFTER_ALL` - Lifecycle method names

**Usage**: All plugin classes import constants statically to avoid duplication

### 3. ImportHelper (Service)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ImportHelper`

**Responsibilities**:
- Manages import transformations during JUnit 4→5 migration
- Removes obsolete JUnit 4 imports
- Adds required JUnit 5 imports
- Handles static imports and wildcard imports

**Key Methods**:
- `addJUnit5Imports()` - Adds necessary JUnit 5 imports
- `removeJUnit4Imports()` - Removes JUnit 4 imports
- `transformStaticImports()` - Converts JUnit 4 static imports to JUnit 5

**Integration**: Used by all cleanup tools that modify imports

### 4. AssertionRefactorer (Service)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.AssertionRefactorer`

**Responsibilities**:
- Reorders assertion method parameters for JUnit 5 compatibility
- Handles the message parameter position change (first in JUnit 4, last in JUnit 5)
- Supports both standard assertions and assumptions

**Key Methods**:
- `reorderParameters()` - Reorders method invocation arguments
- `needsReordering()` - Determines if a method requires parameter reordering
- `createReorderedArguments()` - Builds the new argument list

**Transformation Example**:
```java
// JUnit 4
assertEquals("message", expected, actual);

// JUnit 5
assertEquals(expected, actual, "message");
```

### 5. LifecycleMethodAdapter (Service)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.LifecycleMethodAdapter`

**Responsibilities**:
- Transforms JUnit 4 lifecycle annotations to JUnit 5
- Handles `@Before` → `@BeforeEach`, `@After` → `@AfterEach`
- Handles `@BeforeClass` → `@BeforeAll`, `@AfterClass` → `@AfterAll`
- Creates callback methods for extension-based lifecycle management

**Key Methods**:
- `transformLifecycleAnnotation()` - Converts annotation type
- `createLifecycleCallbackMethod()` - Creates JUnit 5 callback method
- `removeSuperLifecycleCalls()` - Removes invalid super calls in interfaces

**Static Method Requirements**:
- `@BeforeAll` and `@AfterAll` methods must be static in JUnit 5
- Adapter ensures proper static modifier based on context

### 6. ExternalResourceRefactorer (Service)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.ExternalResourceRefactorer`

**Responsibilities**:
- Migrates JUnit 4 `ExternalResource` to JUnit 5 extension callbacks
- Handles both named classes and anonymous classes
- Manages static vs. instance field distinctions
- Implements appropriate callback interfaces based on field modifiers

**Key Methods**:
- `refactorExternalResource()` - Main entry point for migration
- `refactorNamedClassToImplementCallbacks()` - Handles named class transformations
- `refactorAnonymousClassToImplementCallbacks()` - Handles anonymous classes
- `ensureClassInstanceRewrite()` - Updates superclass and imports

**Callback Selection Logic**:
```java
// Static field → BeforeAllCallback, AfterAllCallback
@RegisterExtension
static ExternalResource resource = ...;

// Instance field → BeforeEachCallback, AfterEachCallback
@RegisterExtension
ExternalResource resource = ...;
```

### 7. TestNameRefactorer (Service)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.TestNameRefactorer`

**Responsibilities**:
- Migrates JUnit 4 `TestName` rule to JUnit 5 `TestInfo` parameter injection
- Updates field declarations and method invocations
- Handles inheritance hierarchies
- Manages method signature changes

**Key Methods**:
- `refactorTestNameRule()` - Main transformation orchestrator
- `addTestInfoParameter()` - Injects `TestInfo` parameter into test methods
- `updateTestNameInvocations()` - Replaces field access with parameter usage
- `handleInheritedTestNameUsage()` - Manages usage in subclasses

**Transformation Example**:
```java
// JUnit 4
@Rule
public TestName testName = new TestName();

@Test
public void myTest() {
    String name = testName.getMethodName();
}

// JUnit 5
@Test
public void myTest(TestInfo testInfo) {
    String name = testInfo.getDisplayName();
}
```

### 8. DocumentHelper (Utility)

**Location**: `org.sandbox.jdt.internal.corext.fix.helper.DocumentHelper`

**Responsibilities**:
- Provides low-level AST and document manipulation utilities
- Creates compilation unit changes
- Manages document connections
- Applies text edits

**Key Methods**:
- `createChangeForRewrite()` - Creates a CompilationUnitChange from AST rewrite
- `connectDocument()` - Establishes document connection for editing
- `disconnectDocument()` - Cleans up document connection
- `applyEdit()` - Applies text edits to documents

**Usage Pattern**:
```java
Document doc = DocumentHelper.connectDocument(compilationUnit);
try {
    // Perform edits
    CompilationUnitChange change = DocumentHelper.createChangeForRewrite(cu, rewrite);
} finally {
    DocumentHelper.disconnectDocument(compilationUnit, doc);
}
```

## Plugin Classes (Concrete Implementations)

Each plugin class extends `AbstractTool` and specializes for a specific JUnit migration scenario:

### AfterClassJUnitPlugin
- Migrates `@AfterClass` to `@AfterAll`
- Ensures static modifier requirement

### BeforeClassJUnitPlugin
- Migrates `@BeforeClass` to `@BeforeAll`
- Ensures static modifier requirement

### AssertJUnitPlugin
- Migrates `org.junit.Assert` imports to `org.junit.jupiter.api.Assertions`
- Reorders assertion parameters
- Handles both instance and static imports

### AssumeJUnitPlugin
- Migrates `org.junit.Assume` to `org.junit.jupiter.api.Assumptions`
- Reorders assumption parameters
- Uses `MULTI_PARAM_ASSUMPTIONS` constant set

### ExternalResourceJUnitPlugin
- Orchestrates `ExternalResourceRefactorer` for field-level transformations
- Manages `@Rule` and `@ClassRule` to `@RegisterExtension` migration

### TestJUnit3Plugin
- Migrates JUnit 3 test cases (extending `TestCase`) to JUnit 5
- Handles assertion parameter reordering
- Updates test discovery from naming convention to `@Test` annotation

### TestNameJUnitPlugin
- Orchestrates `TestNameRefactorer` for TestName rule migration
- Manages method signature updates across test hierarchies

### LostTestFinderJUnitPlugin
- Detects "lost" JUnit 3 tests that were not properly migrated during regex-based migrations
- Identifies `public void test*()` methods missing `@Test` annotation in classes already containing `@Test` methods
- Checks class hierarchy (including superclasses) to determine if class is a test class
- Excludes lifecycle methods (`@Before`, `@After`, `@BeforeEach`, `@AfterEach`, etc.)
- Version-aware: adds `org.junit.Test` or `org.junit.jupiter.api.Test` based on existing imports
- Supports wildcard imports (`import org.junit.*;`)
- Conservative approach: only adds `@Test` to methods in confirmed test classes to avoid false positives

**Detection Logic**:
```java
// Method is a "lost test" if ALL conditions are met:
// 1. Class (or superclass) contains @Test methods
// 2. Method name starts with "test"
// 3. No @Test annotation present
// 4. public void signature with no parameters
// 5. Not annotated with lifecycle/skip annotations
```

**Transformation Example**:
```java
// Before
public class CalculatorTest {
    @Test
    public void testAddition() { }
    
    public void testEdgeCase() { }  // Lost during migration
}

// After
public class CalculatorTest {
    @Test
    public void testAddition() { }
    
    @Test  // Added by LostTestFinderJUnitPlugin
    public void testEdgeCase() { }
}
```

## Data Flow

### Typical Transformation Flow

1. **Discovery** (`find()` method)
   ```
   CompilationUnit → AST Visitor → Identify transformation points
   ```

2. **Transformation** (`rewrite()` method)
   ```
   AST Node → Helper Class → AST Rewrite → TextEdit
   ```

3. **Application** (`process2Rewrite()` method)
   ```
   TextEdit → Document → CompilationUnitChange → Applied to editor
   ```

### Example: Assert Statement Migration

```
User triggers cleanup
    ↓
AssertJUnitPlugin.find()
    ↓ identifies assertEquals calls
AssertJUnitPlugin.rewrite()
    ↓ delegates to
ImportHelper.transformStaticImports()
    ↓ and
AssertionRefactorer.reorderParameters()
    ↓ creates
AST Rewrite with changes
    ↓ wrapped by
DocumentHelper.createChangeForRewrite()
    ↓ returns
CompilationUnitChange
    ↓ applied to
Source file
```

## Design Patterns

### 1. Strategy Pattern
Each helper class implements a specific transformation strategy, allowing AbstractTool to delegate without knowing implementation details.

### 2. Template Method Pattern
AbstractTool defines the transformation workflow skeleton (`find()`, `rewrite()`, `process2Rewrite()`), with concrete plugin classes filling in specific behavior.

### 3. Facade Pattern
Helper classes provide simplified interfaces to complex AST operations, hiding Eclipse JDT API complexity.

### 4. Delegation Pattern
AbstractTool delegates specialized operations to helper classes rather than implementing everything itself.

## Key Design Decisions

### 1. Static vs. Instance Methods
Helper classes use static methods to emphasize their stateless, utility nature. This avoids unnecessary object creation and makes the code more functional.

### 2. Centralized Constants
All JUnit-related constants are in `JUnitConstants` to:
- Avoid duplication across plugin classes
- Provide single source of truth
- Simplify maintenance when JUnit APIs change
- Enable easy static imports

### 3. ImportRewrite Usage
The code uses Eclipse's `ImportRewrite` API to:
- Automatically determine when to use simple vs. fully qualified names
- Handle import conflicts intelligently
- Generate clean, properly formatted imports

### 4. Separate Callback Interfaces
`ExternalResourceRefactorer` selects different callback interfaces based on field modifiers:
- Static fields → `BeforeAllCallback`, `AfterAllCallback`
- Instance fields → `BeforeEachCallback`, `AfterEachCallback`

This ensures generated code matches JUnit 5 semantics.

## Extension Points

### Adding New Cleanup Operations

1. **Create a new plugin class** extending `AbstractTool`
2. **Implement required methods**:
   - `find()` - Identify transformation opportunities
   - `rewrite()` - Apply transformations
   - `process2Rewrite()` - Coordinate with helpers

3. **Use helper classes** as needed:
   ```java
   ImportHelper.addJUnit5Imports(importRewriter);
   AssertionRefactorer.reorderParameters(methodInv, rewriter, group, ...);
   ```

4. **Register in plugin.xml** under `org.eclipse.jdt.ui.cleanUps` extension point

### Adding New Helper Classes

1. **Identify cross-cutting concern** that appears in multiple plugins
2. **Create focused helper class** with static methods
3. **Extract common logic** from plugin classes
4. **Add constants to JUnitConstants** if applicable
5. **Update this architecture documentation**

## Testing Strategy

### Unit Tests
Each helper class should have dedicated unit tests in the `*_test` module:
- `JUnitConstantsTest` - Validates constant definitions
- `ImportHelperTest` - Tests import transformations
- `AssertionRefactorerTest` - Tests parameter reordering
- `LifecycleMethodAdapterTest` - Tests annotation transformations
- `ExternalResourceRefactorerTest` - Tests callback migrations
- `TestNameRefactorerTest` - Tests TestInfo injection
- `DocumentHelperTest` - Tests AST operations

### Integration Tests
Plugin tests in `JUnitMigrationCleanUpTest` validate end-to-end transformations using real code samples.

## Performance Considerations

1. **Lazy Initialization**: Helper classes are stateless and methods are static, avoiding object creation overhead

2. **Single AST Pass**: Where possible, transformations are applied in a single pass to avoid multiple AST traversals

3. **ImportRewrite Efficiency**: Using ImportRewrite's built-in conflict detection avoids manual string manipulation

4. **Selective Transformation**: The `find()` method identifies specific nodes to transform, avoiding unnecessary processing

## Future Improvements

1. **Parameterized Tests Migration**: Extract helper for JUnit 4 parameterized tests → JUnit 5 `@ParameterizedTest`

2. **Rule Migration**: Generalize ExternalResourceRefactorer to handle other JUnit 4 rules

3. **Assertion Library Support**: Add helpers for AssertJ, Hamcrest migration to JUnit 5 style

4. **Configuration**: Allow users to customize transformation preferences (e.g., prefer static imports)

5. **Batch Processing**: Optimize for processing multiple compilation units in a single operation

## Related Documentation

- [Eclipse JDT Core Documentation](https://help.eclipse.org/latest/topic/org.eclipse.jdt.doc.isv/reference/api/index.html)
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [JUnit 4 to 5 Migration Guide](https://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4)

## Documentation Requirements

### Feature Properties

The corresponding feature module `sandbox_junit_cleanup_feature` MUST maintain:

1. **feature.properties** - English language properties file containing:
   - `description` - Clear description of the feature's purpose and capabilities
   - `copyright` - Copyright notice with appropriate years
   - `licenseURL` - URL to the Eclipse Public License
   - `license` - Eclipse Public License text or reference

2. **feature_de.properties** - German translation of all properties

These files enable Eclipse's built-in localization mechanism and provide user-facing documentation in the Eclipse IDE. When updating feature capabilities, ensure both property files are updated accordingly.

## Maintainers

This architecture was established during the refactoring effort to improve maintainability and modularity of the JUnit cleanup plugin. For questions or suggestions, please open an issue in the repository.
