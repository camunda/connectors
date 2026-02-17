/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.http.base.model.auth.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class HttpCommonRequest {

  @TemplateProperty(ignore = true)
  private static final int DEFAULT_TIMEOUT = 20;

  @FEEL
  @NotNull
  @TemplateProperty(group = "endpoint", id = "method", defaultValue = "GET")
  private HttpMethod method;

  @FEEL
  @NotBlank
  @Pattern(regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)", message = "Must be a http(s) URL")
  @TemplateProperty(group = "endpoint", label = "URL", feel = FeelMode.optional)
  private String url;

  @Valid private Authentication authentication;

  @TemplateProperty(
      group = "timeout",
      label = "Connection timeout in seconds",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")),
      description = "Defines the connection timeout in seconds, or 0 for an infinite timeout")
  private Integer connectionTimeoutInSeconds;

  @TemplateProperty(
      group = "timeout",
      label = "Read timeout in seconds",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")),
      description =
          "Timeout in seconds to read data from an established connection or 0 for an infinite timeout")
  private Integer readTimeoutInSeconds;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      description = "Map of HTTP headers to add to the request")
  private Map<String, String> headers;

  @FEEL
  @TemplateProperty(
      label = "Request body",
      description = "Payload to send with the request",
      feel = FeelMode.optional,
      group = "payload",
      type = PropertyType.Text,
      optional = true,
      condition =
          @PropertyCondition(
              property = "method",
              oneOf = {"POST", "PUT", "PATCH"}))
  private Object body;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      description = "Map of query parameters to add to the request URL")
  private Map<String, String> queryParameters;

  @TemplateProperty(
      group = "endpoint",
      type = PropertyType.Boolean,
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      defaultValue = "false",
      description = "Store the response as a document in the document store")
  private boolean storeResponse;

  @TemplateProperty(
      label = "Skip URL encoding",
      description = "Skip the default URL decoding and encoding behavior",
      type = TemplateProperty.PropertyType.Hidden,
      feel = FeelMode.disabled,
      group = "endpoint",
      optional = true)
  private String skipEncoding;

  @TemplateProperty(
      group = "payload",
      type = PropertyType.Boolean,
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      defaultValue = "false",
      tooltip = "Null values will not be sent",
      condition =
          @PropertyCondition(
              property = "method",
              oneOf = {"POST", "PUT", "PATCH"}))
  private boolean ignoreNullValues;

  // write getters for all attributes of this class
  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(final HttpMethod method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(final String url) {
    this.url = url;
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final Authentication authentication) {
    this.authentication = authentication;
  }

  public Integer getConnectionTimeoutInSeconds() {
    return Optional.ofNullable(connectionTimeoutInSeconds).orElse(DEFAULT_TIMEOUT);
  }

  public void setConnectionTimeoutInSeconds(final Integer connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  public Integer getReadTimeoutInSeconds() {
    return Optional.ofNullable(readTimeoutInSeconds)
        .orElse(connectionTimeoutInSeconds != null ? connectionTimeoutInSeconds : DEFAULT_TIMEOUT);
  }

  public void setReadTimeoutInSeconds(final Integer readTimeoutInSeconds) {
    this.readTimeoutInSeconds = readTimeoutInSeconds;
  }

  public Optional<String> getHeader(final String key) {
    if (Objects.nonNull(headers)) {
      return headers.keySet().stream().filter(key::equalsIgnoreCase).findFirst().map(headers::get);
    }
    return Optional.empty();
  }

  public Optional<Map<String, String>> getHeaders() {
    return Optional.ofNullable(headers);
  }

  public void setHeaders(final Map<String, String> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(final Object body) {
    this.body = body;
  }

  public Map<String, String> getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(final Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
  }

  public boolean isStoreResponse() {
    return storeResponse;
  }

  public void setStoreResponse(final boolean storeResponse) {
    this.storeResponse = storeResponse;
  }

  public String getSkipEncoding() {
    return skipEncoding;
  }

  public void setSkipEncoding(final String skipEncoding) {
    this.skipEncoding = skipEncoding;
  }

  public boolean isIgnoreNullValues() {
    return ignoreNullValues;
  }

  public void setIgnoreNullValues(final boolean ignoreNullValues) {
    this.ignoreNullValues = ignoreNullValues;
  }
}
