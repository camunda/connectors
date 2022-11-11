/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.model;

import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;
import javax.validation.constraints.NotBlank;

public class RabbitMqRouting {

  @Secret private String virtualHost;
  @Secret private String hostName;
  @Secret private String port;
  @NotBlank @Secret private String exchange;
  @NotBlank @Secret private String routingKey;

  public String getVirtualHost() {
    return virtualHost;
  }

  public void setVirtualHost(final String virtualHost) {
    this.virtualHost = virtualHost;
  }

  public String getHostName() {
    return hostName;
  }

  public void setHostName(final String hostName) {
    this.hostName = hostName;
  }

  public String getPort() {
    return port;
  }

  public void setPort(final String port) {
    this.port = port;
  }

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
    final RabbitMqRouting routing = (RabbitMqRouting) o;
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
