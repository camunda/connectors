FROM reg.mini.dev/1212/openjre-base:v21.0.13-dev@sha256:b516bb84b4641b88759bf2274e41868d39d467ea435c2c0f9d9bf3d21a717149

VOLUME /tmp

RUN mkdir /opt/app

# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create an unprivileged user / group and switch to that user
RUN groupadd --gid 1001 camunda && useradd --no-create-home --gid 1001 --uid 1001 camunda
USER 1001:1001

ENV CAMUNDA_CONNECTOR_RUNTIME_SAAS=true
ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors

# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
