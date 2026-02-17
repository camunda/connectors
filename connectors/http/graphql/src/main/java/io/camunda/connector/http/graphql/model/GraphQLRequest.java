/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

/**
 * Due to legacy reasons, the data format has to stay this way. The `graphql`
 *
 * <p>TODO: Restructure this data class when moving to a new connector version in the context of the
 * switch of the underlying http library
 *
 * @param graphql
 * @param authentication
 */
public record GraphQLRequest(@Valid GraphQL graphql, @Valid Authentication authentication) {

  public record GraphQL(
      @TemplateProperty(
              id = "query",
              label = "Query/Mutation",
              description =
                  "See <a href=\"https://docs.camunda.io/docs/components/connectors/protocol/graphql/#querymutation\" target=\"_blank\">documentation</a>",
              type = TemplateProperty.PropertyType.Text,
              // TODO add support for language property supported by element templates: language:
              // graphql
              group = "graphql")
          @NotBlank
          String query,
      @TemplateProperty(
              id = "variables",
              group = "graphql",
              feel = FeelMode.required,
              optional = true)
          @FEEL
          Map<String, Object> variables,
      @FEEL
          @NotNull
          @TemplateProperty(
              group = "endpoint",
              id = "method",
              defaultValue = "get",
              choices = {
                @TemplateProperty.DropdownPropertyChoice(label = "GET", value = "get"),
                @TemplateProperty.DropdownPropertyChoice(label = "POST", value = "post")
              })
          HttpMethod method,
      @FEEL
          @NotBlank
          @Pattern(
              regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)",
              message = "Must be a http(s) URL")
          @TemplateProperty(id = "url", group = "endpoint", label = "URL")
          String url,
      @FEEL
          @TemplateProperty(
              feel = FeelMode.required,
              group = "endpoint",
              optional = true,
              description = "Map of HTTP headers to add to the request")
          Map<String, String> headers,
      @TemplateProperty(
              group = "endpoint",
              type = TemplateProperty.PropertyType.Boolean,
              defaultValueType = TemplateProperty.DefaultValueType.Boolean,
              defaultValue = "false",
              description = "Store the response as a document in the document store")
          boolean storeResponse,
      @TemplateProperty(
              group = "timeout",
              defaultValue = "20",
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              optional = true,
              description =
                  "Sets the timeout in seconds to establish a connection or 0 for an infinite timeout")
          Integer connectionTimeoutInSeconds) {}
}
