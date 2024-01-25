/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.rpc.model;

import io.camunda.connector.rabbitmq.inbound.model.RabbitMqMessageProperties;

/** Model of the Connector output */
public record RabbitMqRpcResult(RabbitMqRpcMessage message) {

  public record RabbitMqRpcMessage(
      String consumerTag, Object body, RabbitMqMessageProperties properties) {}
}
