# Sandbox project

This repository serves as a sandbox to experiment with various tools and build strategies.

# Build Instructions

To build the project, including a WAR file that contains the update site, run:

`mvn -Dinclude=web -Pjacoco verify`

The product will be located in

"sandbox_product/target"

The WAR file will be located in

"sandbox_web/target".

main(2025-03): 

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)

[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)

[![pmd](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)


2022-12:

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)

[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)

[![pmd](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-12)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)


2022-09:

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)

[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)

[![pmd](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-09)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)


2022-06:

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)

[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)

[![pmd](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg?branch=2022-06)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)



# What is included

What’s Included

Java version per branch:
	•	Since 2024-06: Java 21
	•	From 2022-12 onwards: Java 17
	•	Up to 2022-06: Java 11

Topics covered:
	•	Building for different Eclipse versions via GitHub Actions
	•	Creating custom JDT cleanups
	•	Setting up the SpotBugs Maven plugin to fail the build on issues
	•	Writing JUnit 5-based tests for JDT cleanups
	•	Configuring JaCoCo for test coverage
	•	Building an Eclipse product including new features
	•	Automatically building a WAR file including a P2 update site

# Projects

All projects are considered work in progress unless otherwise noted.

	1. sandbox_cleanup_application
	
	Placeholder for a CLI-based cleanup application, similar to the Java code formatting tool:
	
	eclipse -nosplash -consolelog -debug -application org.eclipse.jdt.core.JavaCodeFormatter -verbose -config MyCodingStandards.ini MyClassToBeFormatted.java
	
	see https://bugs.eclipse.org/bugs/show_bug.cgi?id=75333
	
	2. sandbox_encoding_quickfix
	
	Partly implementation what should help to make usage of platform encoding visible by change of api use.
	See https://openjdk.java.net/jeps/400 for affected api when *not* specifying default encoding.
	
	3. sandbox_extra_search
	
	Partly implementation of what should offer a curated search for interesting classes that have to be taken into account
	when updating a java code base to newer eclipse and java version.
	
	4. sandbox_usage_view
	
	Table view that lists all objects in the code and sorts it by name. That should help to find uses of the similar variable name for 
	what in fact is refering to the same object. Sometimes in an old code base you find deviations like camelcase or not, underscores and other small changes.
	These changes are able to confuse a developer 
	
	5. sandbox_platform_helper
	
	Poc for quickfix to change code according to new possibilities in 
	https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation.
	
	6. sandbox_tools
	
	While to ForLoop Converter
	Already merged into Eclipse jdt
	
	7. sandbox_functional_converter
	
	Iterator to functional loop converter
	https://github.com/carstenartur/sandbox/wiki/Functional-Converter
	Or see netbeans to learn how it works
 
  	8. sandbox_junit

	cleanup to migrate junit 4 based tests to junit 5
 <a href="/marketplace-client-intro?mpc_install=6454408" class="drag" title="Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client">
<img style="width:80px;" typeof="foaf:Image" class="img-responsive" src="https://marketplace.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_marketplace/images/btn-install.svg" alt="Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client" />
</a>



 

# Installation

You can use the p2 update site 

https://github.com/carstenartur/sandbox/raw/main

Warning: Use only with a fresh Eclipse installation that can be discarded after testing.
It may break your setup. Don’t say you weren’t warned…
