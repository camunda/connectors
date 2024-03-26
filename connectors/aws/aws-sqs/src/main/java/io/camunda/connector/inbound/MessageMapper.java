/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.inbound.model.message.SqsInboundMessage;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageMapper {
  private static final Logger LOGGER = LoggerFactory.getLogger(MessageMapper.class);

  public static SqsInboundMessage toSqsInboundMessage(final Message message) {
    Map<String, io.camunda.connector.inbound.model.message.MessageAttributeValue>
        sqsInboundMessageAttributes = new HashMap<>();
    for (Map.Entry<String, MessageAttributeValue> entry :
        message.getMessageAttributes().entrySet()) {
      io.camunda.connector.inbound.model.message.MessageAttributeValue sqsInboundMessageAttribute =
          toSqsInboundMessageAttribute(entry.getValue());

      sqsInboundMessageAttributes.put(entry.getKey(), sqsInboundMessageAttribute);
    }

    return new SqsInboundMessage(
        message.getMessageId(),
        message.getReceiptHandle(),
        message.getMD5OfBody(),
        toObjectIfPossible(message.getBody()),
        message.getAttributes(),
        message.getMD5OfMessageAttributes(),
        sqsInboundMessageAttributes);
  }

  private static Object toObjectIfPossible(final String body) {
    try {
      return ObjectMapperSupplier.getMapperInstance().readValue(body, Object.class);
    } catch (JsonProcessingException e) {
      LOGGER.debug("Cannot parse value to JSON object -> using the raw value");
    }
    return body;
  }

  private static io.camunda.connector.inbound.model.message.MessageAttributeValue
      toSqsInboundMessageAttribute(final MessageAttributeValue attributeValue) {
    return new io.camunda.connector.inbound.model.message.MessageAttributeValue(
        attributeValue.getStringValue(),
        attributeValue.getBinaryValue(),
        attributeValue.getStringListValues(),
        attributeValue.getBinaryListValues(),
        attributeValue.getDataType());
  }
}
