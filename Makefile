# Makefile for Sandbox Build Acceleration
# Provides convenient targets for different build scenarios

.PHONY: help dev product repo release test clean

# Default target: show help
help:
	@echo "Sandbox Build Targets:"
	@echo "====================="
	@echo ""
	@echo "  make dev       - Fast development build (bundles + features, skips tests)"
	@echo "  make product   - Build with Eclipse product materialization (requires xvfb for tests)"
	@echo "  make repo      - Build with p2 update site repository (requires xvfb for tests)"
	@echo "  make release   - Full release build (product + repo + coverage, requires xvfb for tests)"
	@echo "  make test      - Run tests with coverage (requires xvfb)"
	@echo "  make clean     - Clean all build artifacts"
	@echo ""
	@echo "Build Flags:"
	@echo "  -T 1C         - Parallel build (1 thread per CPU core)"
	@echo "  -DskipTests   - Skip test execution"
	@echo ""

# Fast development build - no product/repo
dev:
	mvn -T 1C -DskipTests verify

# Build with product materialization
product:
	xvfb-run --auto-servernum mvn -Pproduct -T 1C verify

# Build with p2 repository
repo:
	xvfb-run --auto-servernum mvn -Prepo -T 1C verify

# Full release build with coverage
release:
	xvfb-run --auto-servernum mvn -Pproduct,repo,jacoco -T 1C verify

# Run tests with coverage (requires xvfb on Linux)
test:
	xvfb-run --auto-servernum mvn -Pjacoco -T 1C verify

# Clean all build artifacts
clean:
	mvn clean
