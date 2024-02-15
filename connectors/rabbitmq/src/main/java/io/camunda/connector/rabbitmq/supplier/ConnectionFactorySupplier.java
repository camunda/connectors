/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.supplier;

import com.rabbitmq.client.ConnectionFactory;
import io.camunda.connector.rabbitmq.common.model.CredentialsAuthentication;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import io.camunda.connector.rabbitmq.common.model.UriAuthentication;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

public class ConnectionFactorySupplier {

  public ConnectionFactory createFactory(
      final RabbitMqAuthentication authentication, final FactoryRoutingData routing)
      throws URISyntaxException, NoSuchAlgorithmException, KeyManagementException {
    final var factory = new ConnectionFactory();
    switch (authentication) {
      case UriAuthentication uriAuthentication -> factory.setUri(uriAuthentication.uri());
      case CredentialsAuthentication credentialsAuthentication -> {
        factory.setUsername(credentialsAuthentication.userName());
        factory.setPassword(credentialsAuthentication.password());
        factory.setVirtualHost(routing.virtualHost());
        factory.setHost(routing.hostName());
        factory.setPort(Integer.parseInt(routing.port()));
      }
    }
    return factory;
  }
}
