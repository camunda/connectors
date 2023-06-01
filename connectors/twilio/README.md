# Camunda Twilio Connector

The Camunda Twilio Connector allows you to send SMS messages or retrieve message information using the Twilio API in your Camunda workflows.

## Overview

The Twilio Connector for Camunda is a powerful integration tool that enables businesses to streamline communication processes by integrating Twilio SMS capabilities with Camunda workflow automation. With an OOTB Inbound Twilio Connector, an Outbound Twilio Connector, and message retrieval and listing capabilities, the Twilio Connector for Camunda simplifies the integration process and enhances productivity, ultimately improving customer engagement.

## Features

- Outbound: Send an SMS, Get Message, List Messages
- Inbound: Receive incoming message with Twilio service

## Usage

To use the Twilio Connector in your Camunda workflows, follow these steps:

1. Add a Service Task to your BPMN process.
2. Configure the Service Task's connector properties to use the Twilio Connector.
3. Set the connector properties for the specific operation you want to perform (sendSms, getMessage, listMessages).
4. Map the response values to workflow variables using the **Response mapping** property.
5. Use the **Error handling** property to specify how you want to handle errors that may occur during the Twilio operation.

For more information about the Twilio Connector and how to use it in your Camunda workflows, please refer to the documentation provided in the link below.

## Documentation

[https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/twilio/](https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/twilio/)
