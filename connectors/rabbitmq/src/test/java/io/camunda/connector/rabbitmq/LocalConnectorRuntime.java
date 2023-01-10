/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq;

import io.camunda.connector.runtime.ConnectorRuntimeApplication;
import org.springframework.boot.SpringApplication;

public class LocalConnectorRuntime {

  public static void main(String[] args) {
    SpringApplication.run(ConnectorRuntimeApplication.class, args);
  }
}
