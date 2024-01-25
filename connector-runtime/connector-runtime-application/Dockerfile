FROM eclipse-temurin:21.0.2_13-jre

VOLUME /tmp

# The /opt/app is used for the Connectors runtime, and out-of-the-box connectors
# Use the /opt/custom to mount your own connectors, secret providers, or include other jars into the classpath
RUN mkdir /opt/app && mkdir /opt/custom

COPY start.sh /start.sh
RUN chmod +x start.sh

# Copy Connector runtime from local build
COPY target/*-with-dependencies.jar /opt/app/
# It can also be downloaded from Maven Central instead
# ADD https://s01.oss.sonatype.org/content/repositories/releases/io/camunda/connector/connector-runtime-application/${VERSION}/connector-runtime-application-${VERSION}-with-dependencies.jar /opt/app/runtime.jar

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 camunda && useradd --no-create-home --gid 1001 --uid 1001 camunda
USER 1001:1001

# Use entry point to allow downstream images to add JVM arguments using CMD
ENV ZEEBE_CLIENT_CONFIG_PATH=/tmp/connectors
ENTRYPOINT ["/start.sh"]
