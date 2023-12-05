FROM eclipse-temurin:17.0.8.1_1-jre

VOLUME /tmp

RUN mkdir /opt/app

# Download connectors from maven central
COPY target/*-with-dependencies.jar /opt/app/

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 1001 && useradd -g 1001 -u 1001 -M 1001
USER 1001:1001

# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
