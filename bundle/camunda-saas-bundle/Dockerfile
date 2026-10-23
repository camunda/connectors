FROM reg.mini.dev/1212/openjre-base:v25.0.5-dev@sha256:b90c5ee1385bc0c486b8351c81abefaf50076a4f6a4c64bb7461bc84ff09112f

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
# FROM eclipse-temurin:21.0.10_7-jre

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
ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors

# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
