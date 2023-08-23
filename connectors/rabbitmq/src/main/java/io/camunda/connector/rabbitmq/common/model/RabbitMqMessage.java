/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import io.camunda.connector.rabbitmq.outbound.ValidationPropertiesUtil;
import io.camunda.connector.rabbitmq.supplier.ObjectMapperSupplier;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;
import java.util.Optional;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RabbitMqMessage {
  private static final Logger LOGGER = LoggerFactory.getLogger(RabbitMqMessage.class);
  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperSupplier.instance();

  private Object properties;
  @NotNull private Object body;

  public AMQP.BasicProperties getPropertiesAsAmqpBasicProperties() {
    return Optional.ofNullable(properties)
        .map(properties -> OBJECT_MAPPER.convertValue(properties, JsonNode.class))
        .map(ValidationPropertiesUtil::validateAmqpBasicPropertiesOrThrowException)
        .map(
            jsonProperties ->
                OBJECT_MAPPER.convertValue(jsonProperties, AMQP.BasicProperties.class))
        .orElse(null);
  }

  public byte[] getBodyAsByteArray() {
    if (body instanceof String) {
      try {
        JsonNode jsonElement =
            OBJECT_MAPPER.readTree(StringEscapeUtils.unescapeJson(body.toString()));

        if (jsonElement.isValueNode()) {
          return ((String) body).getBytes();
        } else {
          body = jsonElement;
        }
      } catch (JsonProcessingException e) {
        // this is plain text value, and not JSON. For example, "some input text".
        LOGGER.debug("Expected exception when parsing a plain text value : {}", body, e);
        return body.toString().getBytes();
      }
    }

    return Optional.of(body)
        .map(
            body -> {
              try {
                return OBJECT_MAPPER.writeValueAsString(body);
              } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
              }
            })
        .map(StringEscapeUtils::unescapeJson)
        .map(String::getBytes)
        .orElseThrow(() -> new RuntimeException("Parse error to byte array"));
  }

  public Object getProperties() {
    return properties;
  }

  public void setProperties(final Object properties) {
    this.properties = properties;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RabbitMqMessage message = (RabbitMqMessage) o;
    return Objects.equals(properties, message.properties) && Objects.equals(body, message.body);
  }

  @Override
  public int hashCode() {
    return Objects.hash(properties, body);
  }

  @Override
  public String toString() {
    return "RabbitMqMessage{" + "properties=" + properties + ", body=" + body + "}";
  }
}
