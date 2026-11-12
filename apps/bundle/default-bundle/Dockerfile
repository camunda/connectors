# Unpack the Spring Boot fat jar into an exploded directory (same layout, launcher included).
# Running from the nested jar makes virtual threads deadlock on the jar's shared lock on JDK 24+
# (camunda/connectors#10102); the exploded layout gives every library its own JarFile.
# The extracted files are platform-independent, so run this stage natively (no QEMU on multi-arch builds)
FROM --platform=$BUILDPLATFORM reg.mini.dev/1212/openjre-base:v27.0.1-dev@sha256:208f8507e0637ab4341b41e06c109687d44a4ddb8b268bedc25bdcca3893e661 AS extract
COPY target/*-with-dependencies.jar /tmp/runtime.jar
RUN java -Djarmode=tools -jar /tmp/runtime.jar extract --launcher --destination /tmp/runtime

FROM reg.mini.dev/1212/openjre-base:v27.0.1-dev@sha256:208f8507e0637ab4341b41e06c109687d44a4ddb8b268bedc25bdcca3893e661

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

COPY --from=extract /tmp/runtime /opt/app/runtime

# Using the start script from the base connector runtime image
COPY start.sh /start.sh
RUN chmod +x /start.sh

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda

USER 1001:1001

ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENV HEALTHCHECK_URL=http://localhost:8080/actuator/health/readiness

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=30s \
  CMD ["java", "-cp", "/opt/app/runtime:/opt/app/*", "-Dloader.main=io.camunda.connector.runtime.healthcheck.HealthCheckCommand", "org.springframework.boot.loader.launch.PropertiesLauncher"]

ENTRYPOINT ["/start.sh"]
