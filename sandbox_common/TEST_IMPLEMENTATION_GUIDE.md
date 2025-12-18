# Sandbox Common - Test Implementation Guide

## Overview
This document provides guidance for implementing unit tests for the `sandbox_common` module utilities. Tests should be created in a new `sandbox_common_test` module following the pattern of existing test modules.

## Creating the Test Module

### 1. Module Structure
Create a new module `sandbox_common_test` with the following structure:
```
sandbox_common_test/
├── pom.xml
├── fragment.xml
├── META-INF/
│   └── MANIFEST.MF
├── build.properties
└── src/
    └── org/
        └── sandbox/
            └── jdt/
                └── internal/
                    ├── common/
                    │   ├── LibStandardNamesTest.java
                    │   └── ReferenceHolderTest.java
                    └── corext/
                        └── util/
                            ├── AnnotationUtilsTest.java
                            ├── NamingUtilsTest.java
                            └── ASTNavigationUtilsTest.java
```

### 2. POM Configuration
```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/maven-v4_0_0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.sandbox</groupId>
        <artifactId>central</artifactId>
        <version>1.2.2-SNAPSHOT</version>
    </parent>
    <artifactId>sandbox_common_test</artifactId>
    <name>Sandbox common test</name>
    <packaging>eclipse-test-plugin</packaging>
    <dependencies>
        <dependency>
            <groupId>org.sandbox</groupId>
            <artifactId>sandbox_common</artifactId>
        </dependency>
    </dependencies>
    <build>
        <testSourceDirectory>src</testSourceDirectory>
        <plugins>
            <plugin>
                <groupId>org.eclipse.tycho</groupId>
                <artifactId>tycho-surefire-plugin</artifactId>
                <version>${tycho-version}</version>
                <configuration>
                    <useJDK>BREE</useJDK>
                    <useUIHarness>true</useUIHarness>
                    <dependencies>
                        <dependency>
                            <type>eclipse-plugin</type>
                            <groupId>org.sandbox</groupId>
                            <artifactId>sandbox_common</artifactId>
                            <version>0.0.0</version>
                        </dependency>
                    </dependencies>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3. Add Module to Parent POM
Add to `/pom.xml` modules section:
```xml
<module>sandbox_common_test</module>
```

## Test Examples

### NamingUtilsTest.java
```java
package org.sandbox.jdt.internal.corext.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class NamingUtilsTest {
    
    @Test
    public void testToUpperCamelCase() {
        assertEquals("HelloWorld", NamingUtils.toUpperCamelCase("hello_world"));
        assertEquals("MyVariableName", NamingUtils.toUpperCamelCase("my_variable_name"));
        assertEquals("", NamingUtils.toUpperCamelCase(""));
        assertNull(NamingUtils.toUpperCamelCase(null));
    }
    
    @Test
    public void testToLowerCamelCase() {
        assertEquals("helloWorld", NamingUtils.toLowerCamelCase("hello_world"));
        assertEquals("myVariableName", NamingUtils.toLowerCamelCase("my_variable_name"));
    }
    
    @Test
    public void testToSnakeCase() {
        assertEquals("hello_world", NamingUtils.toSnakeCase("HelloWorld"));
        assertEquals("hello_world", NamingUtils.toSnakeCase("helloWorld"));
    }
    
    @Test
    public void testIsValidJavaIdentifier() {
        assertTrue(NamingUtils.isValidJavaIdentifier("validName"));
        assertTrue(NamingUtils.isValidJavaIdentifier("_validName"));
        assertFalse(NamingUtils.isValidJavaIdentifier("class")); // keyword
        assertFalse(NamingUtils.isValidJavaIdentifier("123invalid")); // starts with digit
        assertFalse(NamingUtils.isValidJavaIdentifier("")); // empty
        assertFalse(NamingUtils.isValidJavaIdentifier(null)); // null
    }
}
```

### LibStandardNamesTest.java
```java
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

public class LibStandardNamesTest {
    
    @Test
    public void testAllConstantsArePublic() throws IllegalAccessException {
        Field[] fields = LibStandardNames.class.getDeclaredFields();
        for (Field field : fields) {
            int modifiers = field.getModifiers();
            assertTrue(Modifier.isPublic(modifiers), 
                "Field " + field.getName() + " should be public");
            assertTrue(Modifier.isStatic(modifiers),
                "Field " + field.getName() + " should be static");
            assertTrue(Modifier.isFinal(modifiers),
                "Field " + field.getName() + " should be final");
            assertEquals(String.class, field.getType(),
                "Field " + field.getName() + " should be String");
        }
    }
    
    @Test
    public void testSpecificConstants() {
        assertEquals("getProperty", LibStandardNames.METHOD_GET_PROPERTY);
        assertEquals("defaultCharset", LibStandardNames.METHOD_DEFAULT_CHARSET);
        assertEquals("forEach", LibStandardNames.METHOD_FOREACH);
        assertEquals("UTF_8", LibStandardNames.FIELD_UTF8);
    }
}
```

### AnnotationUtilsTest.java (Requires Eclipse AST Setup)
```java
package org.sandbox.jdt.internal.corext.util;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sandbox.jdt.ui.tests.quickfix.rules.AbstractEclipseJava;
import org.sandbox.jdt.ui.tests.quickfix.rules.EclipseJava17;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.dom.*;

public class AnnotationUtilsTest {
    
    @RegisterExtension
    AbstractEclipseJava context = new EclipseJava17();
    
    @Test
    public void testFindAnnotation() throws Exception {
        IPackageFragment pack = context.getSourceFolder().createPackageFragment("test", true, null);
        String code = """
            package test;
            @Deprecated
            public class Test {
                @Override
                public String toString() { return ""; }
            }
            """;
        ICompilationUnit cu = pack.createCompilationUnit("Test.java", code, true, null);
        
        CompilationUnit astRoot = context.getASTRoot(cu);
        TypeDeclaration type = (TypeDeclaration) astRoot.types().get(0);
        
        // Test finding annotation on class
        Annotation deprecatedAnnotation = AnnotationUtils.findAnnotation(
            type.modifiers(), "java.lang.Deprecated");
        assertNotNull(deprecatedAnnotation);
        
        // Test not finding non-existent annotation
        Annotation missing = AnnotationUtils.findAnnotation(
            type.modifiers(), "java.lang.SuppressWarnings");
        assertNull(missing);
    }
}
```

### ReferenceHolderTest.java
```java
package org.sandbox.jdt.internal.common;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class ReferenceHolderTest {
    
    @Test
    public void testBasicOperations() {
        ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
        
        // Test put and get
        holder.put("key1", 100);
        assertEquals(100, holder.get("key1"));
        
        // Test size
        assertEquals(1, holder.size());
        
        // Test containsKey
        assertTrue(holder.containsKey("key1"));
        assertFalse(holder.containsKey("key2"));
        
        // Test remove
        holder.remove("key1");
        assertFalse(holder.containsKey("key1"));
    }
    
    @Test
    public void testNullKeyNotAllowed() {
        ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
        assertThrows(NullPointerException.class, () -> holder.put(null, 100));
    }
    
    @Test
    public void testNullValueNotAllowed() {
        ReferenceHolder<String, Integer> holder = new ReferenceHolder<>();
        assertThrows(NullPointerException.class, () -> holder.put("key", null));
    }
}
```

## Running Tests

After creating the test module:

```bash
# Run all tests
mvn test

# Run specific test module
cd sandbox_common_test
mvn test

# Run with coverage
mvn -Pjacoco verify
```

## Coverage Goals

Aim for high test coverage:
- `NamingUtils`: 100% (all methods are pure utility functions)
- `AnnotationUtils`: 90%+ (AST-dependent methods)
- `ASTNavigationUtils`: 90%+ (AST-dependent methods)
- `LibStandardNames`: 100% (constant validation)
- `ReferenceHolder`: 95%+ (concurrent operations)

## Notes

- Tests requiring AST nodes should use `AbstractEclipseJava` test infrastructure
- Simple utility tests (like `NamingUtils`) don't need Eclipse infrastructure
- Follow JUnit 5 conventions (`@Test`, `@BeforeEach`, etc.)
- Use descriptive test method names that explain what is being tested
