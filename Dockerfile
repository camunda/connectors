# Configure the connector runtime version
ARG RUNTIME_VERSION=0.2.2
FROM camunda/connectors:${RUNTIME_VERSION}

# Configure versions of included connectors
ARG AWS_LAMBDA_VERSION=0.3.2
ARG GOOGLE_DRIVE_VERSION=0.4.0
ARG HTTP_JSON_VERSION=0.9.0
ARG SENDGRID_VERSION=0.11.0
ARG SLACK_VERSION=0.4.0
ARG SQS_VERSION=0.2.0

# Download connectors from maven central
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-aws-lambda/${AWS_LAMBDA_VERSION}/connector-aws-lambda-${AWS_LAMBDA_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-google-drive/${GOOGLE_DRIVE_VERSION}/connector-google-drive-${GOOGLE_DRIVE_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-http-json/${HTTP_JSON_VERSION}/connector-http-json-${HTTP_JSON_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sendgrid/${SENDGRID_VERSION}/connector-sendgrid-${SENDGRID_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-slack/${SLACK_VERSION}/connector-slack-${SLACK_VERSION}-with-dependencies.jar /opt/app/
ADD https://repo1.maven.org/maven2/io/camunda/connector/connector-sqs/${SQS_VERSION}/connector-sqs-${SQS_VERSION}-with-dependencies.jar /opt/app/

