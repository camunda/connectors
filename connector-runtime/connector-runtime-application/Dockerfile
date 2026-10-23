FROM reg.mini.dev/1212/openjre-base:v25.0.5-dev@sha256:b90c5ee1385bc0c486b8351c81abefaf50076a4f6a4c64bb7461bc84ff09112f

# If you don't have access to Minimus hardened base images, you can use public
# base images like this instead on your own risk:
#FROM eclipse-temurin:21.0.9_10-jre

VOLUME /tmp

WORKDIR /

# Switch to root to allow setting up our own user and dirs
USER 0

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
# Use the /opt/custom to mount your own connectors, secret providers, or include other jars into the classpath
RUN mkdir /opt/app && mkdir /opt/custom

COPY start.sh /start.sh
RUN chmod +x /start.sh

# Copy Connector runtime from local build
COPY target/*-with-dependencies.jar /opt/app/
# It can also be downloaded from Maven Central instead
# ADD https://s01.oss.sonatype.org/content/repositories/releases/io/camunda/connector/connector-runtime-application/${VERSION}/connector-runtime-application-${VERSION}-with-dependencies.jar /opt/app/runtime.jar

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && adduser -S -G camunda -u 1001 --no-create-home camunda
USER 1001:1001

# Use entry point to allow downstream images to add JVM arguments using CMD
ENV CAMUNDA_CLIENT_AUTH_CREDENTIALS_CACHE_PATH=/tmp/connectors
ENTRYPOINT ["/start.sh"]
