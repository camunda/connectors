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
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * @param jwtCredential reusable credential; when bound, it supplies the JWK URL, issuer and
 *     audience (see {@link #jwkUrl()}, {@link #issuer()}, {@link #audience()}). Declared first, as
 *     the inline fields it gates must render after it.
 */
public record JWTProperties(
    @TemplateProperty(
            id = "jwtCredential",
            label = "JWT credential",
            group = "authorization",
            type = PropertyType.Configuration,
            optional = true,
            feel = FeelMode.disabled,
            description =
                "Choose a reusable JWT authorization credential, or configure one-time parameters"
                    + " below.")
        @FEEL
        @Valid
        WebhookJwtConfiguration jwtCredential,
    @TemplateProperty(
            label = "JWK URL",
            description = "Well-known URL of JWKs",
            feel = FeelMode.optional,
            group = "authorization",
            condition =
                @PropertyCondition(
                    property = "inbound.auth.jwt.jwtCredential",
                    isEmpty = NullableBoolean.TRUE))
        @FEEL
        String jwkUrl,
    @TemplateProperty(
            label = "Issuer",
            description = "Expected value of the JWT 'iss' claim",
            feel = FeelMode.optional,
            group = "authorization",
            condition =
                @PropertyCondition(
                    property = "inbound.auth.jwt.jwtCredential",
                    isEmpty = NullableBoolean.TRUE))
        @FEEL
        @NotBlank
        String issuer,
    @TemplateProperty(
            label = "Audience",
            description = "Expected value of the JWT 'aud' claim",
            feel = FeelMode.optional,
            group = "authorization",
            condition =
                @PropertyCondition(
                    property = "inbound.auth.jwt.jwtCredential",
                    isEmpty = NullableBoolean.TRUE))
        @FEEL
        @NotBlank
        String audience,
    @TemplateProperty(
            label = "JWT role property expression",
            description =
                "Expression to extract the roles from the JWT token. <a href='https://docs.camunda.io/docs/8.10/components/connectors/protocol/http-webhook/#how-to-extract-roles-from-jwt-data'>See documentation</a>",
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
  public JWTProperties(
      String jwkUrl,
      String issuer,
      String audience,
      Function<Object, List<String>> permissionsExpression,
      List<String> requiredPermissions) {
    this(null, jwkUrl, issuer, audience, permissionsExpression, requiredPermissions);
  }

  public JWTProperties {
    var effectiveJwkUrl = jwtCredential != null ? jwtCredential.jwkUrl() : jwkUrl;
    var effectiveIssuer = jwtCredential != null ? jwtCredential.issuer() : issuer;
    var effectiveAudience = jwtCredential != null ? jwtCredential.audience() : audience;
    Objects.requireNonNull(effectiveJwkUrl);
    if (effectiveIssuer == null || effectiveIssuer.isBlank()) {
      throw new IllegalArgumentException(
          "No issuer provided by the reusable credential or the element template");
    }
    if (effectiveAudience == null || effectiveAudience.isBlank()) {
      throw new IllegalArgumentException(
          "No audience provided by the reusable credential or the element template");
    }
  }

  /** The bound credential's JWK URL wins over the inline one. */
  @Override
  public String jwkUrl() {
    return jwtCredential != null ? jwtCredential.jwkUrl() : jwkUrl;
  }

  /** The bound credential's issuer wins over the inline one. */
  @Override
  public String issuer() {
    return jwtCredential != null ? jwtCredential.issuer() : issuer;
  }

  /** The bound credential's audience wins over the inline one. */
  @Override
  public String audience() {
    return jwtCredential != null ? jwtCredential.audience() : audience;
  }
}
