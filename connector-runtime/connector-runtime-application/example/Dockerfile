# This Dockerfile illustrates how you can bundle your own connectors with the connector runtime

# Step 1. Configure the connector runtime version & out-of-the-box connectors version
ARG CAMUNDA_CONNECTORS_VERSION=0.20.0

# Step 2. Use the Connector runtime as base image
FROM camunda/connectors:${RUNTIME_VERSION}

# Step 3. Download some connectors from maven central (or copy from local build)
# This example uses some of the out-of-the-box connectors developed by Camunda.
# Add your own connectors to the /opt/app/ directory of the image
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-http-json/${CAMUNDA_CONNECTORS_VERSION}/connector-http-json-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-graphql/${CAMUNDA_CONNECTORS_VERSION}/connector-graphql-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-rabbitmq/${CAMUNDA_CONNECTORS_VERSION}/connector-rabbitmq-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sendgrid/${CAMUNDA_CONNECTORS_VERSION}/connector-sendgrid-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-slack/${CAMUNDA_CONNECTORS_VERSION}/connector-slack-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sns/${CAMUNDA_CONNECTORS_VERSION}/connector-sns-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sqs/${CAMUNDA_CONNECTORS_VERSION}/connector-sqs-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-kafka/${CAMUNDA_CONNECTORS_VERSION}/connector-kafka-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-microsoft-teams/${CAMUNDA_CONNECTORS_VERSION}/connector-microsoft-teams-${CAMUNDA_CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
