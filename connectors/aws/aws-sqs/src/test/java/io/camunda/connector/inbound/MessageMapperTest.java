/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.aws.ObjectMapperSupplier;
import io.camunda.connector.inbound.model.message.SqsInboundMessage;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;

class MessageMapperTest {
  private final String MESSAGE_ID = "messageID";
  private final String RECEIPT_HANDLE = "receipt handle";
  private final String MD5_OF_MESSAGE_ATTRIBUTES = "withMD5OfMessageAttributes";
  private final String JSON_BODY = "{\"key\":{\"innerKey\":\"value\"}}";
  private final String STRING_BODY = "just string message";
  private final String ATTRIBUTE_DATA_TYPE = "data type";
  private final String ATTRIBUTE_STRING_VALUE = "string value";
  private final String ATTRIBUTE_KEY =
      MessageSystemAttributeName.APPROXIMATE_FIRST_RECEIVE_TIMESTAMP.toString();
  private final ByteBuffer ATTRIBUTE_BINARY_TYPE = ByteBuffer.wrap("binary value".getBytes());

  private static final ObjectMapper objectMapper = ObjectMapperSupplier.getMapperInstance();
  private final Map<String, String> ATTRIBUTES = Map.of(ATTRIBUTE_KEY, "attributeValue");
  private Map<String, MessageAttributeValue> messageAttributes;
  private Message awsMessage;

  @BeforeEach
  public void setUp() {
    messageAttributes = new HashMap<>();
    MessageAttributeValue attributeValue =
        MessageAttributeValue.builder()
            .stringValue(ATTRIBUTE_STRING_VALUE)
            .binaryValue(SdkBytes.fromByteBuffer(ATTRIBUTE_BINARY_TYPE))
            .dataType(ATTRIBUTE_DATA_TYPE)
            .binaryListValues(List.of(SdkBytes.fromByteBuffer(ATTRIBUTE_BINARY_TYPE)))
            .stringListValues(List.of(ATTRIBUTE_STRING_VALUE))
            .build();
    messageAttributes.put(ATTRIBUTE_KEY, attributeValue);

    awsMessage =
        Message.builder()
            .messageId(MESSAGE_ID)
            .receiptHandle(RECEIPT_HANDLE)
            .md5OfMessageAttributes(MD5_OF_MESSAGE_ATTRIBUTES)
            .body(STRING_BODY)
            .md5OfBody(JSON_BODY)
            .attributes(
                ATTRIBUTES.entrySet().stream()
                    .collect(
                        Collectors.toMap(
                            entry -> MessageSystemAttributeName.fromValue(entry.getKey()),
                            Map.Entry::getValue)))
            .messageAttributes(messageAttributes)
            .build();
  }

  @Test
  public void toSqsInboundMessage_shouldMapAllProperties() {
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    assertThat(sqsInboundMessage.messageId()).isEqualTo(MESSAGE_ID);
    assertThat(sqsInboundMessage.receiptHandle()).isEqualTo(RECEIPT_HANDLE);
    assertThat(sqsInboundMessage.mD5OfMessageAttributes()).isEqualTo(MD5_OF_MESSAGE_ATTRIBUTES);
    assertThat(sqsInboundMessage.attributes()).isEqualTo(ATTRIBUTES);
    assertThat(sqsInboundMessage.mD5OfBody()).isEqualTo(JSON_BODY);
    assertThat(sqsInboundMessage.body()).isEqualTo(STRING_BODY);

    io.camunda.connector.inbound.model.message.MessageAttributeValue
        sqsInboundMessageAttributeValue = sqsInboundMessage.messageAttributes().get(ATTRIBUTE_KEY);
    assertThat(sqsInboundMessageAttributeValue.stringValue()).isEqualTo(ATTRIBUTE_STRING_VALUE);
    assertThat(sqsInboundMessageAttributeValue.dataType()).isEqualTo(ATTRIBUTE_DATA_TYPE);
    assertThat(sqsInboundMessageAttributeValue.binaryValue()).isEqualTo(ATTRIBUTE_BINARY_TYPE);
    assertThat(sqsInboundMessageAttributeValue.stringListValues())
        .isEqualTo(List.of(ATTRIBUTE_STRING_VALUE));
    assertThat(sqsInboundMessageAttributeValue.binaryListValues())
        .isEqualTo(List.of(ATTRIBUTE_BINARY_TYPE));
  }

  @Test
  public void toSqsInboundMessage_shouldMapStringJsonBodyToObject() {
    // Given
    awsMessage = awsMessage.toBuilder().body(JSON_BODY).build();
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    JsonNode jsonNode = objectMapper.convertValue(sqsInboundMessage.body(), JsonNode.class);
    String actualValue = jsonNode.get("key").get("innerKey").asText();
    assertThat(actualValue).isEqualTo("value");
  }

  @Test
  public void toSqsInboundMessage_shouldWorkWithEmptyAttributes() {
    // Given
    awsMessage = awsMessage.toBuilder().messageAttributes(new HashMap<>()).build();
    awsMessage = awsMessage.toBuilder().attributes(new HashMap<>()).build();
    // When
    SqsInboundMessage sqsInboundMessage = MessageMapper.toSqsInboundMessage(awsMessage);
    // then
    assertThat(sqsInboundMessage.attributes()).isEqualTo(new HashMap<>());
    assertThat(sqsInboundMessage.messageAttributes()).isEqualTo(new HashMap<>());
  }
}
