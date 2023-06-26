/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.inbound.model;

import com.rabbitmq.client.AMQP;
import java.util.Date;
import java.util.Map;

/** Model of the RabbitMQ message properties. Mirrors the {@link AMQP.BasicProperties}. */
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
    String clusterId) {}
