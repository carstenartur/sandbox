SUPPORTED_JAVA_MAJOR := 21

.PHONY: check-java dev dev-notests product repo release test clean help cli-dist

help:
	@echo "Available targets:"
	@echo "  check-java  - Verify that Maven runs on the supported JDK 21"
	@echo "  dev         - Fast development build with tests"
	@echo "  dev-notests - Fast development build without tests"
	@echo "  product     - Build with Eclipse product"
	@echo "  repo        - Build with P2 update site"
	@echo "  release     - Full release build (product + repo + coverage)"
	@echo "  test        - Run tests with coverage (requires xvfb)"
	@echo "  cli-dist    - Build CLI distribution (requires product)"
	@echo "  clean       - Clean all build artifacts"

check-java:
	@command -v mvn >/dev/null 2>&1 || { \
		echo "ERROR: Maven was not found on PATH." >&2; \
		exit 1; \
	}
	@JAVA_MAJOR="$$(mvn --version 2>&1 | sed -n 's/^Java version: \([0-9][0-9]*\).*/\1/p' | head -n 1)"; \
	if [ -z "$$JAVA_MAJOR" ]; then \
		echo "ERROR: Could not determine the Java runtime used by Maven." >&2; \
		echo "Run 'mvn --version' and verify JAVA_HOME and PATH." >&2; \
		exit 1; \
	fi; \
	if [ "$$JAVA_MAJOR" != "$(SUPPORTED_JAVA_MAJOR)" ]; then \
		echo "ERROR: Sandbox builds are supported with JDK $(SUPPORTED_JAVA_MAJOR), but Maven is using JDK $$JAVA_MAJOR." >&2; \
		echo "Set JAVA_HOME to a JDK $(SUPPORTED_JAVA_MAJOR) installation and retry." >&2; \
		echo "Fedora: sudo dnf install java-$(SUPPORTED_JAVA_MAJOR)-openjdk-devel" >&2; \
		echo "See docs/JAVA_BUILD_RUNTIME.md for details." >&2; \
		exit 1; \
	fi

dev: check-java
	mvn -T 1C verify

dev-notests: check-java
	mvn -T 1C -DskipTests verify

product: check-java
	mvn -Pproduct -T 1C verify

repo: check-java
	mvn -Prepo -T 1C verify

release: check-java
	mvn -Pproduct,repo -Pjacoco -T 1C verify

cli-dist: check-java
	mvn -Pcli-dist -T 1C verify

test: check-java
	xvfb-run --auto-servernum mvn -Pjacoco -T 1C verify

clean:
	mvn clean
