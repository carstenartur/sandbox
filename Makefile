.PHONY: dev dev-notests product repo release test clean help

help:
	@echo "Available targets:"
	@echo "  dev         - Fast development build (no product/repo)"
	@echo "  dev-notests - Fast development build without running tests"
	@echo "  product     - Build with Eclipse product"
	@echo "  repo        - Build with P2 update site"
	@echo "  release     - Full release build (product + repo + coverage)"
	@echo "  test        - Run tests with coverage (requires xvfb for UI tests)"
	@echo "  clean       - Clean all build artifacts"

dev:
	mvn -T 1C verify

dev-notests:
	mvn -T 1C -DskipTests verify

product:
	mvn -Pproduct -T 1C verify

repo:
	mvn -Prepo -T 1C verify

release:
	mvn -Pproduct,repo,jacoco -T 1C verify

test:
	xvfb-run --auto-servernum mvn -Pjacoco -T 1C verify

clean:
	mvn clean
