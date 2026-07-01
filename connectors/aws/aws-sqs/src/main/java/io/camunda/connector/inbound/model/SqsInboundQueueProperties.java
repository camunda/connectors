/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record SqsInboundQueueProperties(
    @Deprecated @TemplateProperty(ignore = true) String region,
    @TemplateProperty(
            id = "queue.url",
            label = "Queue URL",
            group = "queueProperties",
            tooltip = "URL of the SQS queue to subscribe to.",
            feel = FeelMode.disabled)
        @NotBlank
        String url,
    @TemplateProperty(
            id = "queue.attributeNames",
            label = "Attribute names",
            group = "input",
            tooltip =
                "Array of queue attribute names. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/?amazonsqs=inbound\" target=\"_blank\">Amazon SQS connector guide</a>.",
            feel = FeelMode.optional)
        List<String> attributeNames,
    @TemplateProperty(
            id = "queue.messageAttributeNames",
            label = "Message attribute names",
            group = "input",
            tooltip =
                "Array of message attribute names. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/?amazonsqs=inbound\" target=\"_blank\">Amazon SQS connector guide</a>.",
            feel = FeelMode.optional)
        List<String> messageAttributeNames,
    @TemplateProperty(
            id = "queue.pollingWaitTime",
            label = "Polling wait time",
            group = "messagePollingProperties",
            defaultValue = "20",
            tooltip =
                "The duration (in seconds) for which the call waits for a message to arrive in the queue before returning. See the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/amazon-sqs/?amazonsqs=inbound\" target=\"_blank\">Amazon SQS connector guide</a>. A value of 0 will automatically be overridden to 1.",
            feel = FeelMode.disabled)
        @Pattern(regexp = "^([0-9]?|1[0-9]|20|secrets\\..+)$")
        @NotBlank
        String pollingWaitTime) {}
