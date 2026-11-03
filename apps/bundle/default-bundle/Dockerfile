FROM reg.mini.dev/1212/openjre-base:v27.0.1-dev@sha256:208f8507e0637ab4341b41e06c109687d44a4ddb8b268bedc25bdcca3893e661

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#FROM eclipse-temurin:25.0.1_8-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER 0

# INC-8766 / CVE-2026-114820: after eight eMudhra intermediate CAs were compromised, Oracle removes
# emSign Root CA - G1, G2, emSign ECC Root CA - G3 and AAA Certificate Services from the JDK cacerts.
# The base image follows Mozilla and still trusts G1. Deselect it in the OS CA config and regenerate
# the stores, so it also stays out when a derived image runs update-ca-certificates again. The build
# fails if G1, G3 or AAA is still trusted, so this keeps holding after a base image bump.
SHELL ["/bin/ash", "-o", "pipefail", "-c"]
RUN sed -i 's|^mozilla/emSign_Root_CA_-_G1\.crt$|!&|' /etc/ca-certificates.conf && \
    update-ca-certificates && \
    fps=$(keytool -list -v -cacerts -storepass changeit | grep 'SHA256:' | tr -d ': \t' | cut -c7-) && \
    [ "$(echo "$fps" | grep -c .)" -gt 100 ] && \
    [ "$(echo "$fps" | grep -cxE '40F6AF0346A99AA1CD1D555A4E9CCE62C7F9634603EE406615833DC8C8D00367|86A1ECBA089C4A8D3BBE2734C612BA341D813E043CF9E8A862CD5C57A36BBE6B|D7A7A0FB5D7E2731D771E9484EBCDEF71D5F0C3E0A2948782BC83EE0EA699EF4' || :)" = 0 ]

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
# Use the /opt/custom to mount your own connectors, secret providers, or include other jars into the classpath
RUN mkdir /opt/app && mkdir /opt/custom

COPY target/*-with-dependencies.jar /opt/app/

# Using the start script from the base connector runtime image
COPY start.sh /start.sh
RUN chmod +x /start.sh

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda

USER 1001:1001

ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENV HEALTHCHECK_URL=http://localhost:8080/actuator/health/readiness

HEALTHCHECK --interval=30s --timeout=5s --retries=5 --start-period=30s \
  CMD ["java", "-cp", "/opt/app/*", "-Dloader.main=io.camunda.connector.runtime.healthcheck.HealthCheckCommand", "org.springframework.boot.loader.launch.PropertiesLauncher"]

ENTRYPOINT ["/start.sh"]
