/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import agenticai_adhoctoolsschema_outbound_connector from "./element-templates/agenticai-adhoctoolsschema-outbound-connector.json" with { type: "json" };
import agenticai_aiagent_outbound_connector_8_8_0_alpha4 from "./element-templates/agenticai-aiagent-outbound-connector-8.8.0-alpha4.json" with { type: "json" };
import agenticai_aiagent_outbound_connector from "./element-templates/agenticai-aiagent-outbound-connector.json" with { type: "json" };
import agenticai_mcp_client_outbound_connector from "./element-templates/agenticai-mcp-client-outbound-connector.json" with { type: "json" };
import agenticai_mcp_remote_client_outbound_connector from "./element-templates/agenticai-mcp-remote-client-outbound-connector.json" with { type: "json" };
import asana_connector from "./element-templates/asana-connector.json" with { type: "json" };
import automation_anywhere_outbound_connector from "./element-templates/automation-anywhere-outbound-connector.json" with { type: "json" };
import aws_bedrock_outbound_connector from "./element-templates/aws-bedrock-outbound-connector.json" with { type: "json" };
import aws_comprehend_outbound_connector from "./element-templates/aws-comprehend-outbound-connector.json" with { type: "json" };
import aws_dynamodb_outbound_connector from "./element-templates/aws-dynamodb-outbound-connector.json" with { type: "json" };
import aws_eventbridge_connector_boundary from "./element-templates/aws-eventbridge-connector-boundary.json" with { type: "json" };
import aws_eventbridge_connector_intermediate from "./element-templates/aws-eventbridge-connector-intermediate.json" with { type: "json" };
import aws_eventbridge_connector_message_start from "./element-templates/aws-eventbridge-connector-message-start.json" with { type: "json" };
import aws_eventbridge_connector_start_event from "./element-templates/aws-eventbridge-connector-start-event.json" with { type: "json" };
import aws_eventbridge_outbound_connector from "./element-templates/aws-eventbridge-outbound-connector.json" with { type: "json" };
import aws_lambda_outbound_connector from "./element-templates/aws-lambda-outbound-connector.json" with { type: "json" };
import aws_s3_outbound_connector from "./element-templates/aws-s3-outbound-connector.json" with { type: "json" };
import aws_sagemaker_outbound_connector from "./element-templates/aws-sagemaker-outbound-connector.json" with { type: "json" };
import aws_sns_inbound_boundary from "./element-templates/aws-sns-inbound-boundary.json" with { type: "json" };
import aws_sns_inbound_intermediate from "./element-templates/aws-sns-inbound-intermediate.json" with { type: "json" };
import aws_sns_inbound_message_start from "./element-templates/aws-sns-inbound-message-start.json" with { type: "json" };
import aws_sns_inbound_start_event from "./element-templates/aws-sns-inbound-start-event.json" with { type: "json" };
import aws_sns_outbound_connector from "./element-templates/aws-sns-outbound-connector.json" with { type: "json" };
import aws_sqs_boundary_connector from "./element-templates/aws-sqs-boundary-connector.json" with { type: "json" };
import aws_sqs_inbound_intermediate_connector from "./element-templates/aws-sqs-inbound-intermediate-connector.json" with { type: "json" };
import aws_sqs_outbound_connector from "./element-templates/aws-sqs-outbound-connector.json" with { type: "json" };
import aws_sqs_start_event_connector from "./element-templates/aws-sqs-start-event-connector.json" with { type: "json" };
import aws_sqs_start_message from "./element-templates/aws-sqs-start-message.json" with { type: "json" };
import aws_textract_outbound_connector from "./element-templates/aws-textract-outbound-connector.json" with { type: "json" };
import azure_open_ai_connector from "./element-templates/azure-open-ai-connector.json" with { type: "json" };
import blue_prism_connector from "./element-templates/blue-prism-connector.json" with { type: "json" };
import box_outbound_connector from "./element-templates/box-outbound-connector.json" with { type: "json" };
import easy_post_connector from "./element-templates/easy-post-connector.json" with { type: "json" };
import email_inbound_connector_boundary from "./element-templates/email-inbound-connector-boundary.json" with { type: "json" };
import email_inbound_connector_intermediate from "./element-templates/email-inbound-connector-intermediate.json" with { type: "json" };
import email_message_start_event_connector from "./element-templates/email-message-start-event-connector.json" with { type: "json" };
import email_outbound_connector from "./element-templates/email-outbound-connector.json" with { type: "json" };
import embeddings_vector_database_outbound_connector from "./element-templates/embeddings-vector-database-outbound-connector.json" with { type: "json" };
import github_connector from "./element-templates/github-connector.json" with { type: "json" };
import github_webhook_connector_boundary from "./element-templates/github-webhook-connector-boundary.json" with { type: "json" };
import github_webhook_connector_intermediate from "./element-templates/github-webhook-connector-intermediate.json" with { type: "json" };
import github_webhook_connector_message_start from "./element-templates/github-webhook-connector-message-start.json" with { type: "json" };
import github_webhook_connector_start_event from "./element-templates/github-webhook-connector-start-event.json" with { type: "json" };
import gitlab_connector from "./element-templates/gitlab-connector.json" with { type: "json" };
import google_drive_outbound_connector from "./element-templates/google-drive-outbound-connector.json" with { type: "json" };
import google_gemini_outbound_connector from "./element-templates/google-gemini-outbound-connector.json" with { type: "json" };
import google_maps_platform_connector from "./element-templates/google-maps-platform-connector.json" with { type: "json" };
import google_sheets_outbound_connector from "./element-templates/google-sheets-outbound-connector.json" with { type: "json" };
import graphql_outbound_connector from "./element-templates/graphql-outbound-connector.json" with { type: "json" };
import http_json_connector from "./element-templates/http-json-connector.json" with { type: "json" };
import http_polling_boundary_catch_event_connector from "./element-templates/http-polling-boundary-catch-event-connector.json" with { type: "json" };
import http_polling_connector from "./element-templates/http-polling-connector.json" with { type: "json" };
import hubspot_outbound_connector from "./element-templates/hubspot-outbound-connector.json" with { type: "json" };
import hugging_face_connector from "./element-templates/hugging-face-connector.json" with { type: "json" };
import idp_extraction_outbound_connector from "./element-templates/idp-extraction-outbound-connector.json" with { type: "json" };
import jdbc_outbound_connector from "./element-templates/jdbc-outbound-connector.json" with { type: "json" };
import kafka_inbound_connector_boundary from "./element-templates/kafka-inbound-connector-boundary.json" with { type: "json" };
import kafka_inbound_connector_intermediate from "./element-templates/kafka-inbound-connector-intermediate.json" with { type: "json" };
import kafka_inbound_connector_start_message from "./element-templates/kafka-inbound-connector-start-message.json" with { type: "json" };
import kafka_outbound_connector from "./element-templates/kafka-outbound-connector.json" with { type: "json" };
import microsoft_office365_mail_connector from "./element-templates/microsoft-office365-mail-connector.json" with { type: "json" };
import microsoft_teams_outbound_connector from "./element-templates/microsoft-teams-outbound-connector.json" with { type: "json" };
import openai_connector from "./element-templates/openai-connector.json" with { type: "json" };
import operate_connector from "./element-templates/operate-connector.json" with { type: "json" };
import rabbitmq_inbound_connector_boundary from "./element-templates/rabbitmq-inbound-connector-boundary.json" with { type: "json" };
import rabbitmq_inbound_connector_intermediate from "./element-templates/rabbitmq-inbound-connector-intermediate.json" with { type: "json" };
import rabbitmq_inbound_connector_message_start from "./element-templates/rabbitmq-inbound-connector-message-start.json" with { type: "json" };
import rabbitmq_inbound_connector_start_event from "./element-templates/rabbitmq-inbound-connector-start-event.json" with { type: "json" };
import rabbitmq_outbound_connector from "./element-templates/rabbitmq-outbound-connector.json" with { type: "json" };
import rpa_connector from "./element-templates/rpa-connector.json" with { type: "json" };
import salesforce_connector from "./element-templates/salesforce-connector.json" with { type: "json" };
import send_message_connector_intermediate_throw_event from "./element-templates/send-message-connector-intermediate-throw-event.json" with { type: "json" };
import send_message_connector_message_end_event from "./element-templates/send-message-connector-message-end-event.json" with { type: "json" };
import send_message_connector_send_task from "./element-templates/send-message-connector-send-task.json" with { type: "json" };
import sendgrid_outbound_connector from "./element-templates/sendgrid-outbound-connector.json" with { type: "json" };
import slack_inbound_boundary from "./element-templates/slack-inbound-boundary.json" with { type: "json" };
import slack_inbound_intermediate from "./element-templates/slack-inbound-intermediate.json" with { type: "json" };
import slack_inbound_message_start from "./element-templates/slack-inbound-message-start.json" with { type: "json" };
import slack_inbound_start_event from "./element-templates/slack-inbound-start-event.json" with { type: "json" };
import slack_outbound_connector from "./element-templates/slack-outbound-connector.json" with { type: "json" };
import soap_outbound_connector from "./element-templates/soap-outbound-connector.json" with { type: "json" };
import twilio_connector from "./element-templates/twilio-connector.json" with { type: "json" };
import twilio_webhook_boundary_connector from "./element-templates/twilio-webhook-boundary-connector.json" with { type: "json" };
import twilio_webhook_connector from "./element-templates/twilio-webhook-connector.json" with { type: "json" };
import twilio_webhook_intermediate_connector from "./element-templates/twilio-webhook-intermediate-connector.json" with { type: "json" };
import twilio_webhook_message_start_connector from "./element-templates/twilio-webhook-message-start-connector.json" with { type: "json" };
import uipath_connector from "./element-templates/uipath-connector.json" with { type: "json" };
import webhook_connector_boundary from "./element-templates/webhook-connector-boundary.json" with { type: "json" };
import webhook_connector_intermediate from "./element-templates/webhook-connector-intermediate.json" with { type: "json" };
import webhook_connector_start_event from "./element-templates/webhook-connector-start-event.json" with { type: "json" };
import webhook_connector_start_message from "./element-templates/webhook-connector-start-message.json" with { type: "json" };
import whatsapp_connector from "./element-templates/whatsapp-connector.json" with { type: "json" };

const allConnectors = [
  agenticai_adhoctoolsschema_outbound_connector,
  agenticai_aiagent_outbound_connector_8_8_0_alpha4,
  agenticai_aiagent_outbound_connector,
  agenticai_mcp_client_outbound_connector,
  agenticai_mcp_remote_client_outbound_connector,
  asana_connector,
  automation_anywhere_outbound_connector,
  aws_bedrock_outbound_connector,
  aws_comprehend_outbound_connector,
  aws_dynamodb_outbound_connector,
  aws_eventbridge_connector_boundary,
  aws_eventbridge_connector_intermediate,
  aws_eventbridge_connector_message_start,
  aws_eventbridge_connector_start_event,
  aws_eventbridge_outbound_connector,
  aws_lambda_outbound_connector,
  aws_s3_outbound_connector,
  aws_sagemaker_outbound_connector,
  aws_sns_inbound_boundary,
  aws_sns_inbound_intermediate,
  aws_sns_inbound_message_start,
  aws_sns_inbound_start_event,
  aws_sns_outbound_connector,
  aws_sqs_boundary_connector,
  aws_sqs_inbound_intermediate_connector,
  aws_sqs_outbound_connector,
  aws_sqs_start_event_connector,
  aws_sqs_start_message,
  aws_textract_outbound_connector,
  azure_open_ai_connector,
  blue_prism_connector,
  box_outbound_connector,
  easy_post_connector,
  email_inbound_connector_boundary,
  email_inbound_connector_intermediate,
  email_message_start_event_connector,
  email_outbound_connector,
  embeddings_vector_database_outbound_connector,
  github_connector,
  github_webhook_connector_boundary,
  github_webhook_connector_intermediate,
  github_webhook_connector_message_start,
  github_webhook_connector_start_event,
  gitlab_connector,
  google_drive_outbound_connector,
  google_gemini_outbound_connector,
  google_maps_platform_connector,
  google_sheets_outbound_connector,
  graphql_outbound_connector,
  http_json_connector,
  http_polling_boundary_catch_event_connector,
  http_polling_connector,
  hubspot_outbound_connector,
  hugging_face_connector,
  idp_extraction_outbound_connector,
  jdbc_outbound_connector,
  kafka_inbound_connector_boundary,
  kafka_inbound_connector_intermediate,
  kafka_inbound_connector_start_message,
  kafka_outbound_connector,
  microsoft_office365_mail_connector,
  microsoft_teams_outbound_connector,
  openai_connector,
  operate_connector,
  rabbitmq_inbound_connector_boundary,
  rabbitmq_inbound_connector_intermediate,
  rabbitmq_inbound_connector_message_start,
  rabbitmq_inbound_connector_start_event,
  rabbitmq_outbound_connector,
  rpa_connector,
  salesforce_connector,
  send_message_connector_intermediate_throw_event,
  send_message_connector_message_end_event,
  send_message_connector_send_task,
  sendgrid_outbound_connector,
  slack_inbound_boundary,
  slack_inbound_intermediate,
  slack_inbound_message_start,
  slack_inbound_start_event,
  slack_outbound_connector,
  soap_outbound_connector,
  twilio_connector,
  twilio_webhook_boundary_connector,
  twilio_webhook_connector,
  twilio_webhook_intermediate_connector,
  twilio_webhook_message_start_connector,
  uipath_connector,
  webhook_connector_boundary,
  webhook_connector_intermediate,
  webhook_connector_start_event,
  webhook_connector_start_message,
  whatsapp_connector,
];

export default allConnectors;
