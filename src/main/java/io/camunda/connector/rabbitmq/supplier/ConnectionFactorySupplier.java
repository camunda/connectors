/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.supplier;

import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.rabbitmq.model.RabbitMqAuthenticationType;
import io.camunda.connector.rabbitmq.model.RabbitMqRequest;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class ConnectionFactorySupplier {

  public ConnectionFactory createFactory(final RabbitMqRequest request)
      throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    final var auth = request.getAuthentication();
    final var routing = request.getRouting();
    final var factory = new ConnectionFactory();
    if (auth.getAuthType() == RabbitMqAuthenticationType.URI) {
      factory.setUri(auth.getUri());
    } else {
      factory.setUsername(auth.getUserName());
      factory.setPassword(auth.getPassword());
      factory.setVirtualHost(routing.getVirtualHost());
      factory.setHost(routing.getHostName());
      factory.setPort(Integer.parseInt(routing.getPort()));
    }
    return factory;
  }
}
