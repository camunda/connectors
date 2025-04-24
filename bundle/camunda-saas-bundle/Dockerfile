FROM eclipse-temurin:17.0.15_6-jre

VOLUME /tmp

RUN mkdir /opt/app

# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create an unprivileged user / group and switch to that user
RUN groupadd --gid 1003 connectors && useradd -g connectors -u 1003 -M connectors
USER connectors:connectors

ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors
# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
