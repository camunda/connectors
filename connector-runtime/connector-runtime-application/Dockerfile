FROM eclipse-temurin:17.0.8_7-jre

RUN mkdir /opt/app

COPY start.sh /start.sh
RUN chmod +x start.sh

# Copy Connector runtime from local build
COPY target/*-with-dependencies.jar /opt/app/
# It can also be downloaded from Maven Central instead
# ADD https://s01.oss.sonatype.org/content/repositories/releases/io/camunda/connector/connector-runtime-application/${VERSION}/connector-runtime-application-${VERSION}-with-dependencies.jar /opt/app/runtime.jar

# Use entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["/start.sh"]
