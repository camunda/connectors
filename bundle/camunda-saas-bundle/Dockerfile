FROM eclipse-temurin:25.0.2_10-jre

VOLUME /tmp

RUN mkdir /opt/app

COPY target/*-with-dependencies.jar /opt/app/

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Create an unprivileged user / group and switch to that user
RUN groupadd --gid 1001 camunda && useradd --no-create-home --gid 1001 --uid 1001 camunda
USER 1001:1001

ENV CAMUNDA_CONNECTOR_RUNTIME_SAAS=true
ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors

# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "-cp", "/opt/app/*", "org.springframework.boot.loader.launch.PropertiesLauncher"]
