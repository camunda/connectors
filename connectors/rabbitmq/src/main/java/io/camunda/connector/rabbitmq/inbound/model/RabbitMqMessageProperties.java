package io.camunda.connector.rabbitmq.inbound.model;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.LongString;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Model of the RabbitMQ message properties. Mirrors the {@link AMQP.BasicProperties}.
 */
public record RabbitMqMessageProperties(
    String contentType,
    String contentEncoding,
    Map<String, Object> headers,
    Integer deliveryMode,
    Integer priority,
    String correlationId,
    String replyTo,
    String expiration,
    String messageId,
    Date timestamp,
    String type,
    String userId,
    String appId,
    String clusterId) {

  private static final Logger LOG = LoggerFactory.getLogger(RabbitMqMessageProperties.class);

  public RabbitMqMessageProperties(AMQP.BasicProperties properties) {
    this(properties.getContentType(),
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
      return list.stream()
          .map(RabbitMqMessageProperties::handleHeaderValue)
          .toList();
    } else {
      // long, boolean are represented as their respective types, so we don't need to do anything
      LOG.debug("Unhandled header value type: {}. Original value will be returned",
          value.getClass());
      return value;
    }
  }
}
