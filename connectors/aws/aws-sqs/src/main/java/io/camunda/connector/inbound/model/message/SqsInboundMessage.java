/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model.message;

import java.util.Map;

public record SqsInboundMessage(
    String messageId,
    String receiptHandle,
    String mD5OfBody,
    Object body,
    Map<String, String> attributes,
    String mD5OfMessageAttributes,
    Map<String, MessageAttributeValue> messageAttributes) {}
