AWS_LAMBDA_VERSION=0.3.0
GOOGLE_DRIVE_VERSION=0.4.0
HTTP_JSON_VERSION=0.9.0
SENDGRID_VERSION=0.11.0
SLACK_VERSION=0.4.0
SQS_VERSION=0.2.0

wget -i - << EOF
https://github.com/camunda/connector-aws-lambda/releases/download/$AWS_LAMBDA_VERSION/aws-lambda-connector.json
https://github.com/camunda/connector-google-drive/releases/download/$GOOGLE_DRIVE_VERSION/google-drive-connector.json
https://github.com/camunda/connector-http-json/releases/download/$HTTP_JSON_VERSION/http-json-connector.json
https://github.com/camunda/connector-sendgrid/releases/download/$SENDGRID_VERSION/sendgrid-connector.json
https://github.com/camunda/connector-slack/releases/download/$SLACK_VERSION/slack-connector.json
https://github.com/camunda/connector-sqs/releases/download/$SQS_VERSION/aws-sqs-connector.json
EOF
