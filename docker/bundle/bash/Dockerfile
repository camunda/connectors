# Configure the connector runtime version
ARG RUNTIME_VERSION=0.3.2
FROM camunda/connectors:${RUNTIME_VERSION}

# TODO: consider passing version as an argument
ARG CONNECTORS_VERSION=0.14.0

# TODO: use CONNECTORS_VERSION when these connectors are moved to mono repo
ARG SLACK_VERSION=0.6.0
ARG HTTP_JSON_VERSION=0.11.0

# Download connectors from maven central
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-aws-lambda/${CONNECTORS_VERSION}/connector-aws-lambda-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-google-drive/${CONNECTORS_VERSION}/connector-google-drive-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-http-json/${HTTP_JSON_VERSION}/connector-http-json-${HTTP_JSON_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-rabbitmq/${CONNECTORS_VERSION}/connector-rabbitmq-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sendgrid/${CONNECTORS_VERSION}/connector-sendgrid-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-slack/${SLACK_VERSION}/connector-slack-${SLACK_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sns/${CONNECTORS_VERSION}/connector-sns-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sqs/${CONNECTORS_VERSION}/connector-sqs-${CONNECTORS_VERSION}-with-dependencies.jar /opt/app/

