/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FactoryRoutingData(
    @NotBlank
        @TemplateProperty(
            group = "routing",
            label = "Virtual host",
            description =
                "Virtual name: get from RabbitMQ external application configurations. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound#routing-data\"target=\"_blank\">documentation</a>")
        String virtualHost,
    @NotBlank
        @TemplateProperty(
            group = "routing",
            label = "Host name",
            description =
                "Host name: get from RabbitMQ external application configurations. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound#routing-data\"target=\"_blank\">documentation</a>")
        String hostName,
    @NotNull
        @TemplateProperty(
            group = "routing",
            label = "Port",
            description =
                "Port: get from RabbitMQ external application configurations. Details in the <a href=\"https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/rabbitmq/?rabbitmq=outbound#routing-data\"target=\"_blank\">documentation</a>")
        String port) {}
