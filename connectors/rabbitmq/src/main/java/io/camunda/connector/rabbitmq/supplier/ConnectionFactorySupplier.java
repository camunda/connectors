/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.supplier;

import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.common.model.RabbitMqRouting;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class ConnectionFactorySupplier {

  public ConnectionFactory createFactory(
      final RabbitMqAuthentication authentication, final RabbitMqRouting routing)
      throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    final var factory = new ConnectionFactory();
    if (authentication.getAuthType() == RabbitMqAuthenticationType.uri) {
      factory.setUri(authentication.getUri());
    } else {
      factory.setUsername(authentication.getUserName());
      factory.setPassword(authentication.getPassword());
      factory.setVirtualHost(routing.getVirtualHost());
      factory.setHost(routing.getHostName());
      factory.setPort(Integer.parseInt(routing.getPort()));
    }
    return factory;
  }
}
