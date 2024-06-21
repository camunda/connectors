/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.camunda.connector.rabbitmq.outbound.model.RabbitMqMessage;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageUtil {

  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqMessage.class);
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.instance();

  private MessageUtil() {}

  public static AMQP.BasicProperties toAmqpBasicProperties(final Object properties) {
    return Optional.ofNullable(properties)
        .map(pr -> OBJECT_MAPPER.convertValue(pr, JsonNode.class))
        .map(ValidationPropertiesUtil::validateAmqpBasicPropertiesOrThrowException)
        .map(
            jsonProperties ->
                OBJECT_MAPPER.convertValue(jsonProperties, AMQP.BasicProperties.class))
        .orElse(null);
  }

  public static byte[] getBodyAsByteArray(final Object body) {
    Object resBody = body;
    if (body instanceof String) {
      try {
        JsonNode jsonElement = OBJECT_MAPPER.readTree(body.toString());

        if (jsonElement.isValueNode()) {
          return ((String) body).getBytes();
        } else {
          resBody = jsonElement;
        }
      } catch (JsonProcessingException e) {
        // this is plain text value, and not JSON. For example, "some input text".
        LOGGER.debug("Expected exception when parsing a plain text value : {}", body, e);
        return body.toString().getBytes();
      }
    }

    return Optional.of(resBody)
        .map(
            b -> {
              try {
                return OBJECT_MAPPER.writeValueAsString(b);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(String::getBytes)
        .orElseThrow(() -> new RuntimeException("Parse error to byte array"));
  }
}
