# Dockerfile for Sandbox Cleanup Application GitHub Action
# This creates a containerized version of the sandbox cleanup application
# that can be used in GitHub Actions workflows

FROM eclipse/eclipse-temurin:21-jdk

LABEL maintainer="Sandbox Project"
LABEL description="Eclipse JDT Cleanup Application for automated code cleanup"
LABEL version="1.0.0"

# Install required dependencies
RUN apt-get update && apt-get install -y \
    xvfb \
    xauth \
    libgtk-3-0 \
    libxext6 \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libxrandr2 \
    libxfixes3 \
    libnss3 \
    libgbm1 \
    maven \
    git \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /workspace

# Copy the sandbox project
COPY . /sandbox

# Build the sandbox cleanup application and product
WORKDIR /sandbox
RUN xvfb-run --auto-servernum mvn -B -V \
    -Dtycho.localArtifacts=ignore \
    clean install -DskipTests

# Find and extract the Eclipse product
RUN PRODUCT_ZIP=$(find /sandbox/sandbox_product/target/products -name "*.tar.gz" -o -name "*.zip" | head -1) && \
    if [ -z "$PRODUCT_ZIP" ]; then \
        echo "ERROR: No Eclipse product found"; \
        exit 1; \
    fi && \
    mkdir -p /eclipse && \
    if [[ "$PRODUCT_ZIP" == *.zip ]]; then \
        unzip -q "$PRODUCT_ZIP" -d /eclipse; \
    else \
        tar -xzf "$PRODUCT_ZIP" -C /eclipse; \
    fi && \
    chmod +x /eclipse/eclipse/eclipse

# Copy entrypoint script
COPY .github/actions/cleanup-action/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Set environment variables
ENV DISPLAY=:99
ENV ECLIPSE_HOME=/eclipse/eclipse

WORKDIR /github/workspace

ENTRYPOINT ["/entrypoint.sh"]
