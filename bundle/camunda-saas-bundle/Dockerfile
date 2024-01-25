FROM eclipse-temurin:21.0.2_13-jre

VOLUME /tmp

RUN mkdir /opt/app

# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && useradd --no-create-home --gid 1001 --uid 1001 camunda
USER 1001:1001

ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors
# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
