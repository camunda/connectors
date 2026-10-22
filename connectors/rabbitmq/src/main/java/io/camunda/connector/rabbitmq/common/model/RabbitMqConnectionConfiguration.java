/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * A reusable RabbitMQ connection: either a URI, or a username/password together with the host, port
 * and virtual host it applies to. The exchange, routing key and queue stay on the task, since they
 * depend on what the process does rather than on where it connects.
 */
@Configuration(
    id = "io.camunda.connectors:rabbitmq-connection:1",
    version = 1,
    name = "RabbitMQ Connection")
public record RabbitMqConnectionConfiguration(
    @Valid @NotNull RabbitMqAuthentication authentication, FactoryRoutingData routing) {

  /** Input name of the chooser in the inbound and outbound element templates. */
  @TemplateProperty(ignore = true)
  public static final String CHOOSER_ID = "rabbitMqConnection";

  /**
   * {@code routing} is only shown, and only read, for username/password authentication, so it is
   * checked here rather than through a cascading {@code @Valid}, which would also reject leftover
   * blank routing values on a URI credential.
   */
  @AssertTrue(
      message = "Host name, port and virtual host are required for username/password connections")
  @JsonIgnore
  public boolean isRoutingPresentWhenRequired() {
    return FactoryRoutingData.isPresentWhenRequired(authentication, routing);
  }
}
