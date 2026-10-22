FROM reg.mini.dev/1212/openjre-base:v21.0.13-dev@sha256:b516bb84b4641b88759bf2274e41868d39d467ea435c2c0f9d9bf3d21a717149

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
# FROM eclipse-temurin:21.0.12_8-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER 0

RUN mkdir /opt/app

# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda
USER 1001:1001

ENV CAMUNDA_CONNECTOR_RUNTIME_SAAS=true
ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors

# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
