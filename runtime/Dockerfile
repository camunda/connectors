ARG SPRING_ZEEBE_VERSION=8.1.4
FROM eclipse-temurin:17.0.5_8-jre

RUN mkdir /opt/app
# Download connectors from maven central
ADD https://repo1.maven.org/maven2/io/camunda/spring-zeebe-connector-runtime/${AWS_LAMBDA_VERSION}/spring-zeebe-connector-runtime-${SPRING_ZEEBE_VERSION}.jar /opt/app/
# Using entry point to allow downstream images to add JVM arguments using CMD
ENTRYPOINT ["java", "-cp", "/opt/app/*", "io.camunda.connector.runtime.ConnectorRuntimeApplication"]
