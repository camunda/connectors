FROM reg.mini.dev/1212/openjre-base:v26.0.1-dev

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER root

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
RUN mkdir /opt/app

COPY target/*-with-dependencies.jar /opt/app/

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda
USER 1001:1001

ENV CAMUNDA_CONNECTOR_RUNTIME_SAAS=true
ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENV HEALTHCHECK_URL=http://localhost:9080/actuator/health/readiness

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=30s \
  CMD java -cp /opt/app/*-with-dependencies.jar \
      -Dloader.main=io.camunda.connector.runtime.healthcheck.HealthCheckCommand \
      org.springframework.boot.loader.launch.PropertiesLauncher

ENTRYPOINT ["java", "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "-cp", "/opt/app/*", "org.springframework.boot.loader.launch.PropertiesLauncher"]
