/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.rabbitmq.common.model;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(id = "uri", label = "URI")
public record UriAuthentication(
    @NotBlank
        @Pattern(
            regexp = "^(=|amqps?://|secrets|\\{\\{).*$",
            message = "Must start with amqp(s):// or contain a secret reference")
        @TemplateProperty(
            group = "authentication",
            label = "URI",
            description =
                "URI should contain username, password, host name, port number, and virtual host")
        String uri)
    implements RabbitMqAuthentication {}
