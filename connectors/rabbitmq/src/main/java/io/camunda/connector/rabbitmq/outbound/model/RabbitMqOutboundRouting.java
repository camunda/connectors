/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.outbound.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents the routing information for a RabbitMQ outbound request. This record is structured to
 * support flat JSON deserialization for compatibility with specific JSON structures, facilitating
 * easier integration with RabbitMQ configurations.
 *
 * <p>Note: The strategic use of @JsonCreator for deserialization and @JsonUnwrapped for
 * serialization is specifically chosen to ensure compatibility with previous versions during
 * backporting efforts. This approach overcomes limitations and guarantees that JSON structure
 * management remains consistent across versions, facilitating the maintenance and extension of
 * functionality without breaking changes.
 */
public record RabbitMqOutboundRouting(
    @NotBlank
        @TemplateProperty(
            group = "routing",
            description =
                "Topic exchange: get from RabbitMQ external application configurations. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound#routing-data\"target=\"_blank\">documentation</a>")
        String exchange,
    @NotBlank
        @TemplateProperty(
            group = "routing",
            label = "Routing key",
            description =
                "Routing key: a binding is a \"link\" that was set up to bind a queue to an exchange. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound#routing-data\"target=\"_blank\">documentation</a>")
        String routingKey,
    @NestedProperties(
            addNestedPath = false,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "authentication.authType",
                    equals = "credentials"))
        @JsonUnwrapped
        FactoryRoutingData routingData) {
  @JsonCreator
  public RabbitMqOutboundRouting(
      @JsonProperty("exchange") String exchange,
      @JsonProperty("routingKey") String routingKey,
      @JsonProperty("virtualHost") String virtualHost,
      @JsonProperty("hostName") String hostName,
      @JsonProperty("port") String port) {
    this(exchange, routingKey, new FactoryRoutingData(virtualHost, hostName, port));
  }
}
