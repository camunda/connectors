/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;
import io.camunda.connector.rabbitmq.inbound.model.RabbitMqMessageProperties;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AMQPPropertyUtil {
  private static final Logger LOG = LoggerFactory.getLogger(AMQPPropertyUtil.class);

  public static RabbitMqMessageProperties toProperties(AMQP.BasicProperties properties) {
    return new RabbitMqMessageProperties(
        properties.getContentType(),
        properties.getContentEncoding(),
        parseHeaders(properties.getHeaders()),
        properties.getDeliveryMode(),
        properties.getPriority(),
        properties.getCorrelationId(),
        properties.getReplyTo(),
        properties.getExpiration(),
        properties.getMessageId(),
        properties.getTimestamp(),
        properties.getType(),
        properties.getUserId(),
        properties.getAppId(),
        properties.getClusterId());
  }

  private static Map<String, Object> parseHeaders(Map<String, Object> headers) {
    if (headers == null) {
      return null;
    }
    // headers are represented as byte arrays in the AMQP.BasicProperties
    // we need to convert them to strings
    Map<String, Object> processedHeaders = new HashMap<>();

    for (Map.Entry<String, Object> entry : headers.entrySet()) {
      processedHeaders.put(entry.getKey(), handleHeaderValue(entry.getValue()));
    }
    return processedHeaders;
  }

  private static Object handleHeaderValue(Object value) {
    if (value instanceof LongString longString) {
      return longString.toString();
    } else if (value instanceof List<?> list) {
      return list.stream().map(AMQPPropertyUtil::handleHeaderValue).toList();
    } else {
      // long, boolean are represented as their respective types, so we don't need to do anything
      LOG.debug(
          "Unhandled header value type: {}. Original value will be returned", value.getClass());
      return value;
    }
  }
}
