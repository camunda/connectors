# Unpack the Spring Boot fat jar into an exploded directory (same layout, launcher included).
# Running from the nested jar makes virtual threads deadlock on the jar's shared lock on JDK 24+
# (camunda/connectors#10102); the exploded layout gives every library its own JarFile.
# The jar is only ever treated as data here (unzip), never executed: the PR image workflow builds
# this Dockerfile on a privileged runner with PR-supplied jars. Platform-independent, so run natively.
FROM --platform=$BUILDPLATFORM alpine:3.24@sha256:f59abe656b692d901e184be3445e084786602108599d2a51d229c0f5a48c71bf AS extract
COPY target/*-with-dependencies.jar /tmp/runtime.jar
RUN unzip -q /tmp/runtime.jar -d /tmp/runtime

FROM reg.mini.dev/1212/openjre-base:v25.0.5-dev@sha256:f3e627a73f325894e82c0f41b7ef2eb11d587a9c8b2dabc30bd07a42ff25c83c

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#FROM eclipse-temurin:25.0.1_8-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER 0

# INC-8766 / CVE-2026-114820: the base image still trusts emSign Root CA - G1, which Oracle removes
# from the JDK cacerts. Deselect it and regenerate the stores; the build fails if G1 is still trusted.
RUN sed -i 's|^mozilla/emSign_Root_CA_-_G1\.crt$|!&|' /etc/ca-certificates.conf && \
    update-ca-certificates && \
    certs=$(keytool -list -v -cacerts -storepass changeit) && \
    case "$certs" in *'CN=emSign Root CA - G1,'*) echo "emSign Root CA - G1 is still trusted" >&2; exit 1 ;; esac

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
# Use the /opt/custom to mount your own connectors, secret providers, or include other jars into the classpath
RUN mkdir /opt/app && mkdir /opt/custom

COPY start.sh /start.sh
RUN chmod +x /start.sh

# Copy the exploded Connector runtime from the extract stage
COPY --from=extract /tmp/runtime /opt/app/runtime

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda
USER 1001:1001

# Use entry point to allow downstream images to add JVM arguments using CMD
ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENV HEALTHCHECK_URL=http://localhost:8080/actuator/health/readiness

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=30s \
  CMD ["java", "-cp", "/opt/app/runtime:/opt/app/*", "-Dloader.main=io.camunda.connector.runtime.healthcheck.HealthCheckCommand", "org.springframework.boot.loader.launch.PropertiesLauncher"]

ENTRYPOINT ["/start.sh"]
