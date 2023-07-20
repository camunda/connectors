/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import io.camunda.connector.api.annotation.Secret;
import io.camunda.connector.rabbitmq.common.model.RabbitMqRouting;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

public class RabbitMqOutboundRouting extends RabbitMqRouting {

  @NotBlank @Secret private String exchange;

  @NotBlank @Secret private String routingKey;

  public String getExchange() {
    return exchange;
  }

  public void setExchange(final String exchange) {
    this.exchange = exchange;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public void setRoutingKey(final String routingKey) {
    this.routingKey = routingKey;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    final RabbitMqOutboundRouting routing = (RabbitMqOutboundRouting) o;
    return Objects.equals(virtualHost, routing.virtualHost)
        && Objects.equals(hostName, routing.hostName)
        && Objects.equals(port, routing.port)
        && Objects.equals(exchange, routing.exchange)
        && Objects.equals(routingKey, routing.routingKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(virtualHost, hostName, port, exchange, routingKey);
  }

  @Override
  public String toString() {
    return "RabbitMqRouting{"
        + "virtualHost='"
        + virtualHost
        + "'"
        + ", hostName='"
        + hostName
        + "'"
        + ", port='"
        + port
        + "'"
        + ", exchange='"
        + exchange
        + "'"
        + ", routingKey='"
        + routingKey
        + "'"
        + "}";
  }
}
