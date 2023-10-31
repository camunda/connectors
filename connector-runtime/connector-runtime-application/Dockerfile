FROM eclipse-temurin:21.0.1_12-jre

RUN mkdir /opt/app

COPY start.sh /start.sh
RUN chmod +x start.sh

# Copy Connector runtime from local build
COPY target/*-with-dependencies.jar /opt/app/
# It can also be downloaded from Maven Central instead
# ADD https://s01.oss.sonatype.org/content/repositories/releases/io/camunda/connector/connector-runtime-application/${VERSION}/connector-runtime-application-${VERSION}-with-dependencies.jar /opt/app/runtime.jar

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1003 connectors && useradd -g connectors -u 1003 -M connectors
USER connectors:connectors

# Use entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["/start.sh"]
