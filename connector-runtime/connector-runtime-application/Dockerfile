FROM eclipse-temurin:17.0.9_9-jre

RUN mkdir /opt/app

COPY start.sh /start.sh
RUN chmod +x start.sh

# Copy Connector runtime from local build
COPY target/*-with-dependencies.jar /opt/app/
# It can also be downloaded from Maven Central instead
# ADD https://s01.oss.sonatype.org/content/repositories/releases/io/camunda/connector/connector-runtime-application/${VERSION}/connector-runtime-application-${VERSION}-with-dependencies.jar /opt/app/runtime.jar

# Create an unprivileged user / group and switch to that user
RUN addgroup --gid 1001 1001 && useradd -g 1001 -u 1001 -M 1001
USER 1001:1001

# Use entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["/start.sh"]
