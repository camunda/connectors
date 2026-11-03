FROM reg.mini.dev/1212/openjre-base:v25.0.5-dev@sha256:b90c5ee1385bc0c486b8351c81abefaf50076a4f6a4c64bb7461bc84ff09112f

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
# FROM eclipse-temurin:21.0.10_7-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER 0

# INC-8766 / CVE-2026-114820: after eight eMudhra intermediate CAs were compromised, Oracle removes
# emSign Root CA - G1, G2, emSign ECC Root CA - G3 and AAA Certificate Services from the JDK cacerts.
# The base image follows Mozilla and still trusts G1. Deselect it in the OS CA config and regenerate
# the stores, so it also stays out when a derived image runs update-ca-certificates again. The build
# fails if G1, G3 or AAA is still trusted, so this keeps holding after a base image bump.
RUN sed -i 's|^mozilla/emSign_Root_CA_-_G1\.crt$|!&|' /etc/ca-certificates.conf && \
    update-ca-certificates && \
    fps=$(keytool -list -v -cacerts -storepass changeit | grep 'SHA256:' | tr -d ': \t' | cut -c7-) && \
    [ "$(echo "$fps" | grep -c .)" -gt 100 ] && \
    ! echo "$fps" | grep -qxE '40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367|86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B|D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4'

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
