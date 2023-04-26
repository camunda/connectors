/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import io.camunda.connector.api.annotation.Secret;
import java.util.Objects;

public class RabbitMqRouting {

  @Secret protected String virtualHost;
  @Secret protected String hostName;
  @Secret protected String port;

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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    RabbitMqRouting that = (RabbitMqRouting) o;
    return Objects.equals(virtualHost, that.virtualHost)
        && Objects.equals(hostName, that.hostName)
        && Objects.equals(port, that.port);
  }

  @Override
  public int hashCode() {
    return Objects.hash(virtualHost, hostName, port);
  }

  @Override
  public String toString() {
    return "RabbitMqRouting{"
        + "virtualHost='"
        + virtualHost
        + '\''
        + ", hostName='"
        + hostName
        + '\''
        + ", port='"
        + port
        + '\''
        + '}';
  }
}
