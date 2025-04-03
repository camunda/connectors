# Switch from a JRE image to a JDK image
FROM eclipse-temurin:21.0.6_7-jdk

RUN apt-get update && \
    apt-get install --no-install-recommends -y procps=2:4.0.4-7 && \
    rm -rf /var/lib/apt/lists/*

VOLUME /tmp

RUN mkdir /opt/app

# Copy your fat jar into /opt/app
COPY target/*-with-dependencies.jar /opt/app/

# Create an unprivileged user / group and switch to that user
RUN groupadd --gid 1001 camunda && useradd --no-create-home --gid 1001 --uid 1001 camunda
USER 1001:1001

# This environment variable will inject JVM options automatically
# at startup, even if someone overrides ENTRYPOINT or CMD later.
# Alternatively, you can bake them into your final ENTRYPOINT
ENV JAVA_TOOL_OPTIONS="-XX:NativeMemoryTracking=summary -XX:+UnlockDiagnosticVMOptions -XX:+PrintNMTStatistics"

ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors
# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
