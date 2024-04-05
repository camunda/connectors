/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.jdbc.model.auth;

import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@TemplateSubType(id = "uri", label = "URI")
public record UriAuthentication(
    @NotBlank
        @Pattern(
            regexp = "^(jdbc:|secrets|\\{\\{).*$",
            message = "Must start with jdbc: or contain a secret reference")
        @TemplateProperty(
            group = "authentication",
            label = "URI",
            description =
                "URI should contain JDBC driver, username, password, host name, and port number")
        String uri)
    implements JdbcAuthentication {}
