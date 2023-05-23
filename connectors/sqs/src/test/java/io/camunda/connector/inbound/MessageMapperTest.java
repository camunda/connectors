/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.google.gson.JsonElement;
import io.camunda.connector.common.suppliers.SqsGsonComponentSupplier;
import io.camunda.connector.inbound.model.message.SqsInboundMessage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageMapperTest {
  private final String MESSAGE_ID = "messageID";
  private final String RECEIPT_HANDLE = "receipt handle";
  private final String MD5_OF_MESSAGE_ATTRIBUTES = "withMD5OfMessageAttributes";
  private final String JSON_BODY = "{\"key\":{\"innerKey\":\"value\"}}";
  private final String STRING_BODY = "just string message";
  private final String ATTRIBUTE_DATA_TYPE = "data type";
  private final String ATTRIBUTE_STRING_VALUE = "string value";
  private final String ATTRIBUTE_KEY = "attributeKey";
  private final ByteBuffer ATTRIBUTE_BINARY_TYPE = ByteBuffer.wrap("binary value".getBytes());

  private final Map<String, String> ATTRIBUTES = Map.of(ATTRIBUTE_KEY, "attributeValue");
  private Map<String, MessageAttributeValue> messageAttributes;
  private Message awsMessage;

  @BeforeEach
  public void setUp() {
    messageAttributes = new HashMap<>();
    MessageAttributeValue attributeValue =
        new MessageAttributeValue()
            .withStringValue(ATTRIBUTE_STRING_VALUE)
            .withBinaryValue(ATTRIBUTE_BINARY_TYPE)
            .withDataType(ATTRIBUTE_DATA_TYPE)
            .withBinaryListValues(List.of(ATTRIBUTE_BINARY_TYPE))
            .withStringListValues(List.of(ATTRIBUTE_STRING_VALUE));
    messageAttributes.put(ATTRIBUTE_KEY, attributeValue);

    awsMessage =
        new Message()
            .withMessageId(MESSAGE_ID)
            .withReceiptHandle(RECEIPT_HANDLE)
            .withMD5OfMessageAttributes(MD5_OF_MESSAGE_ATTRIBUTES)
            .withBody(STRING_BODY)
            .withMD5OfBody(JSON_BODY)
            .withAttributes(ATTRIBUTES)
            .withMessageAttributes(messageAttributes);
  }

  @Test
  public void toSqsInboundMessage_shouldMapAllProperties() {
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    assertThat(sqsInboundMessage.getMessageId()).isEqualTo(MESSAGE_ID);
    assertThat(sqsInboundMessage.getReceiptHandle()).isEqualTo(RECEIPT_HANDLE);
    assertThat(sqsInboundMessage.getMD5OfMessageAttributes()).isEqualTo(MD5_OF_MESSAGE_ATTRIBUTES);
    assertThat(sqsInboundMessage.getAttributes()).isEqualTo(ATTRIBUTES);
    assertThat(sqsInboundMessage.getmD5OfBody()).isEqualTo(JSON_BODY);
    assertThat(sqsInboundMessage.getBody()).isEqualTo(STRING_BODY);

    io.camunda.connector.inbound.model.message.MessageAttributeValue
        sqsInboundMessageAttributeValue =
            sqsInboundMessage.getMessageAttributes().get("attributeKey");
    assertThat(sqsInboundMessageAttributeValue.getStringValue()).isEqualTo(ATTRIBUTE_STRING_VALUE);
    assertThat(sqsInboundMessageAttributeValue.getDataType()).isEqualTo(ATTRIBUTE_DATA_TYPE);
    assertThat(sqsInboundMessageAttributeValue.getBinaryValue()).isEqualTo(ATTRIBUTE_BINARY_TYPE);
    assertThat(sqsInboundMessageAttributeValue.getStringListValues())
        .isEqualTo(List.of(ATTRIBUTE_STRING_VALUE));
    assertThat(sqsInboundMessageAttributeValue.getBinaryListValues())
        .isEqualTo(List.of(ATTRIBUTE_BINARY_TYPE));
  }

  @Test
  public void toSqsInboundMessage_shouldMapStringJsonBodyToObject() {
    // Given
    awsMessage.setBody(JSON_BODY);
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    JsonElement jsonElement =
        SqsGsonComponentSupplier.gsonInstance().toJsonTree(sqsInboundMessage.getBody());
    String actualValue =
        jsonElement.getAsJsonObject().get("key").getAsJsonObject().get("innerKey").getAsString();
    assertThat(actualValue).isEqualTo("value");
  }

  @Test
  public void toSqsInboundMessage_shouldWorkWithEmptyAttributes() {
    // Given
    awsMessage.setMessageAttributes(new HashMap<>());
    awsMessage.setAttributes(new HashMap<>());
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    assertThat(sqsInboundMessage.getAttributes()).isEqualTo(new HashMap<>());
    assertThat(sqsInboundMessage.getMessageAttributes()).isEqualTo(new HashMap<>());
  }
}
