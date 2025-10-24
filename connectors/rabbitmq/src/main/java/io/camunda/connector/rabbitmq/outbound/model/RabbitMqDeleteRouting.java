package io.camunda.connector.rabbitmq.outbound.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.rabbitmq.common.model.FactoryRoutingData;
import jakarta.validation.constraints.NotBlank;

public record RabbitMqDeleteRouting(
    @NotBlank
        @TemplateProperty(
            group = "routing",
            label = "Queue name",
            description = "Name of the queue to delete")
        String queueName,
    @TemplateProperty(
            group = "routing",
            label = "If unused",
            description = "Delete only if there are no consumers")
        boolean ifUnused,
    @TemplateProperty(
            group = "routing",
            label = "If empty",
            description = "Delete only if there are no messages")
        boolean ifEmpty,
    @NestedProperties(
            addNestedPath = false,
            condition =
                @TemplateProperty.PropertyCondition(
                    property = "authentication.authType",
                    equals = "credentials"))
        @JsonUnwrapped
        FactoryRoutingData routingData) {

  @JsonCreator
  public RabbitMqDeleteRouting(
      @JsonProperty("queueName") String queueName,
      @JsonProperty("ifUnused") Boolean ifUnused,
      @JsonProperty("ifEmpty") Boolean ifEmpty,
      @JsonProperty("virtualHost") String virtualHost,
      @JsonProperty("hostName") String hostName,
      @JsonProperty("port") String port) {
    this(
        queueName,
        ifUnused != null ? ifUnused : false,
        ifEmpty != null ? ifEmpty : false,
        new FactoryRoutingData(virtualHost, hostName, port));
  }
}
