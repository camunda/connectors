/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record JWTProperties(
    @TemplateProperty(
            label = "JWK URL",
            description = "Well-known URL of JWKs",
            feel = FeelMode.optional,
            group = "authorization")
        @FEEL
        String jwkUrl,
    @TemplateProperty(
            label = "JWT role property expression",
            description =
                "Expression to extract the roles from the JWT token. <a href='https://docs.camunda.io/docs/components/connectors/protocol/http-webhook/#how-to-extract-roles-from-jwt-data'>See documentation</a>",
            group = "authorization",
            feel = FeelMode.required)
        Function<Object, List<String>> permissionsExpression,
    @TemplateProperty(
            label = "Required roles",
            description = "List of roles to test JWT roles against",
            group = "authorization",
            feel = FeelMode.required)
        @FEEL
        List<String> requiredPermissions) {
  public JWTProperties {
    Objects.requireNonNull(jwkUrl);
  }
}
