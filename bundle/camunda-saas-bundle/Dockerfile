FROM reg.mini.dev/1212/openjre-base:v25.0.5-dev@sha256:a842c75d2f4ddaabdcc8261d04bf6f80f77004386a6f69ea65b4eb310f146bb0

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
# -XX:-UseBASE64Intrinsics mitigates CVE-2026-114938 (Java4Shell). Remove once the base JRE ships the fix.
ENTRYPOINT ["java", "-XX:-UseBASE64Intrinsics", "-cp", "/opt/app/*", "io.camunda.connector.runtime.saas.SaaSConnectorRuntimeApplication"]
