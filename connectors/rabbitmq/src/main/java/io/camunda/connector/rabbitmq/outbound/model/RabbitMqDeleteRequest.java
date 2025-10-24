package io.camunda.connector.rabbitmq.outbound.model;

import io.camunda.connector.rabbitmq.common.model.RabbitMqAuthentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record RabbitMqDeleteRequest(
    @Valid @NotNull RabbitMqAuthentication authentication,
    @Valid @NotNull RabbitMqDeleteRouting routing) {}
