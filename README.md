# Sandbox project

To try out some tools and create build strategy sample.

To build including war file that contains update site use

mvn -Dinclude=web -Pjacoco verify

The product can be found in "sandbox_product/target" and the war file in "sandbox_web/target".

[![CodeQL](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codeql.yml)

[![Java CI with Maven](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/maven.yml)

[![Codacy Security Scan](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/codacy.yml)

[![pmd](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml/badge.svg)](https://github.com/carstenartur/sandbox/actions/workflows/pmd.yml)


# What is included

Code based on Java 11

- how to create a cleanup
- how to setup spotbugs maven plugin to fail build in case of spotbugs error
- how to create a junit 5 based test for cleanup
- how to create a jacoco code coverage configuration for the test
- how to create a product based on jdt including the new feature
- how to setup automatic build for a war file including the p2 update site for the products new features

# Projects

Everything is work in progress unless explicitly noted

1) sandbox_cleanup_application

Only placeholder for what should start a cleanup as commandline application similar to the codeformatting wrapper

eclipse -nosplash -consolelog -debug -application org.eclipse.jdt.core.JavaCodeFormatter -verbose -config MyCodingStandards.ini MyClassToBeFormatted.java

see https://bugs.eclipse.org/bugs/show_bug.cgi?id=75333

2) sandbox_encoding_quickfix

Partly implementation what should help to make usage of platform encoding visible by change of api use.
See https://openjdk.java.net/jeps/400 for affected api when *not* specifying default encoding.

3) sandbox_extra_search

Partly implementation of what should offer a curated search for interesting classes that have to be taken into account
when updating a java code base to newer eclipse and java version.

4) sandbox_usage_view

Table view that lists all objects in the code and sorts it by name. That should help to find uses of the similar variable name for 
what in fact is refering to the same object. Sometimes in an old code base you find deviations like camelcase or not, underscores and other small changes.
These changes are able to confuse a developer 

5) sandbox_platform_helper

Poc for quickfix to change code according to new possibilities in 
https://www.eclipse.org/eclipse/news/4.20/platform_isv.php#simpler-status-creation.

# Installation

You can use the p2 update site 

https://github.com/carstenartur/sandbox/raw/main

Only use it on a separate fresh installation that you can throw away after a test as it may destroy your eclipse installation.
Don't tell me nobody has warned you...
