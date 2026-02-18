# Sandbox Cleanup CLI Docker Image
# Runs the Eclipse JDT cleanup application in a container.
#
# Usage:
#   docker build -t sandbox-cleanup .
#   docker run -v /path/to/project:/workspace sandbox-cleanup \
#       --config /workspace/cleanup.properties --mode check --source /workspace

FROM eclipse-temurin:21-jre-noble

LABEL org.opencontainers.image.title="Sandbox Cleanup CLI" \
      org.opencontainers.image.description="Eclipse JDT cleanup transformations in a container" \
      org.opencontainers.image.source="https://github.com/carstenartur/sandbox" \
      org.opencontainers.image.licenses="EPL-2.0"

# Install minimal dependencies for headless Eclipse
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        xvfb \
        x11-utils \
        libgtk-3-0 \
        libxext6 \
        libxrender1 \
        libxtst6 \
        libxi6 \
        libxrandr2 \
        libxfixes3 \
        libnss3 \
        libgbm1 && \
    rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN groupadd -r sandbox && useradd -r -g sandbox -d /home/sandbox sandbox && \
    mkdir -p /home/sandbox /opt/sandbox-cleanup /workspace && \
    chown -R sandbox:sandbox /home/sandbox /opt/sandbox-cleanup /workspace

# Copy the CLI distribution
COPY --chown=sandbox:sandbox dist/ /opt/sandbox-cleanup/

# Make launcher executable
RUN chmod +x /opt/sandbox-cleanup/bin/sandbox-cleanup

# Copy entrypoint
COPY --chown=sandbox:sandbox entrypoint.sh /opt/sandbox-cleanup/entrypoint.sh
RUN chmod +x /opt/sandbox-cleanup/entrypoint.sh

USER sandbox
WORKDIR /workspace

ENTRYPOINT ["/opt/sandbox-cleanup/entrypoint.sh"]
CMD ["--help"]
