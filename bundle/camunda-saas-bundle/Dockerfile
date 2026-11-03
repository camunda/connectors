FROM reg.mini.dev/1212/openjre-base:v21.0.13-dev@sha256:51fe22c4124fd2777b82a18415f3a3e7353a45b955a242c725eb034d42294709

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
# FROM eclipse-temurin:21.0.10_7-jre

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
