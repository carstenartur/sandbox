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
RUN set -e; \
    PRODUCT_DIR="/sandbox/sandbox_product/target/products"; \
    echo "Searching for Eclipse product in ${PRODUCT_DIR}..."; \
    PRODUCT_ZIPS=$(find "$PRODUCT_DIR" -maxdepth 1 \( -name "*.tar.gz" -o -name "*.zip" \) 2>/dev/null || true); \
    PRODUCT_COUNT=$(printf '%s\n' "$PRODUCT_ZIPS" | sed '/^$/d' | wc -l); \
    if [ "$PRODUCT_COUNT" -eq 0 ]; then \
        echo "ERROR: No Eclipse product found in $PRODUCT_DIR"; \
        ls -la "$PRODUCT_DIR" || echo "Directory does not exist"; \
        exit 1; \
    fi; \
    if [ "$PRODUCT_COUNT" -gt 1 ]; then \
        echo "WARNING: Multiple Eclipse products found in $PRODUCT_DIR:"; \
        printf '  %s\n' $PRODUCT_ZIPS; \
        echo "Using first product found"; \
    fi; \
    PRODUCT_ZIP=$(printf '%s\n' "$PRODUCT_ZIPS" | head -1); \
    echo "Using Eclipse product archive: $PRODUCT_ZIP"; \
    mkdir -p /eclipse; \
    case "$PRODUCT_ZIP" in \
        *.zip) \
            echo "Extracting ZIP archive..."; \
            unzip -q "$PRODUCT_ZIP" -d /eclipse \
            ;; \
        *.tar.gz) \
            echo "Extracting TAR.GZ archive..."; \
            tar -xzf "$PRODUCT_ZIP" -C /eclipse \
            ;; \
        *) \
            echo "ERROR: Unsupported Eclipse product archive format: $PRODUCT_ZIP"; \
            exit 1; \
            ;; \
    esac; \
    if [ ! -f /eclipse/eclipse/eclipse ]; then \
        echo "ERROR: Eclipse launcher not found at /eclipse/eclipse/eclipse after extraction"; \
        echo "Contents of /eclipse:"; \
        ls -la /eclipse; \
        exit 1; \
    fi; \
    chmod +x /eclipse/eclipse/eclipse; \
    echo "Eclipse product extracted successfully"

# Copy entrypoint script
COPY .github/actions/cleanup-action/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Set environment variables
ENV DISPLAY=:99
ENV ECLIPSE_HOME=/eclipse/eclipse

WORKDIR /github/workspace

ENTRYPOINT ["/entrypoint.sh"]
