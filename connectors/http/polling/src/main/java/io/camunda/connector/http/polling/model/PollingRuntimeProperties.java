/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

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

public class PollingRuntimeProperties {
  @Valid private Authentication authentication;

  @FEEL
  @NotNull
  @TemplateProperty(group = "endpoint", id = "method", defaultValue = "GET")
  private HttpMethod method;

  @FEEL
  @NotBlank
  @Pattern(regexp = "^(=|(http://|https://|secrets|\\{\\{).*$)", message = "Must be a http(s) URL")
  @TemplateProperty(
      group = "endpoint",
      label = "URL",
      feel = FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "url"))
  private String url;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      description = "Map of query parameters to add to the request URL")
  private Map<String, String> queryParameters;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      description = "Map of HTTP headers to add to the request",
      binding = @TemplateProperty.PropertyBinding(name = "headers"))
  private Map<String, String> headers;

  @FEEL
  @TemplateProperty(
      label = "Request body",
      description = "Payload to send with the request",
      feel = FeelMode.optional,
      group = "payload",
      type = TemplateProperty.PropertyType.Text,
      binding = @TemplateProperty.PropertyBinding(name = "body"),
      optional = true,
      condition =
          @TemplateProperty.PropertyCondition(
              property = "method",
              oneOf = {"POST", "PUT", "PATCH"}))
  private Object body;

  @TemplateProperty(
      label = "Skip URL encoding",
      description = "Skip the default URL decoding and encoding behavior",
      type = TemplateProperty.PropertyType.Hidden,
      feel = FeelMode.disabled,
      group = "endpoint",
      optional = true)
  private String skipEncoding;

  @TemplateProperty(
      group = "timeout",
      label = "Connection timeout in seconds",
      tooltip = "Set the timeout in seconds to establish a connection or 0 for an infinite timeout",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      feel = FeelMode.optional,
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")),
      description = "Defines the connection timeout in seconds, or 0 for an infinite timeout")
  @FEEL
  private Integer connectionTimeoutInSeconds;

  @TemplateProperty(
      group = "timeout",
      label = "Read timeout in seconds",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      feel = FeelMode.optional,
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")),
      description =
          "Timeout in seconds to read data from an established connection or 0 for an infinite timeout")
  @FEEL
  private Integer readTimeoutInSeconds;

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Map<String, String> getQueryParameters() {
    return queryParameters;
  }

  public void setQueryParameters(Map<String, String> queryParameters) {
    this.queryParameters = queryParameters;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  public String getSkipEncoding() {
    return skipEncoding;
  }

  public void setSkipEncoding(String skipEncoding) {
    this.skipEncoding = skipEncoding;
  }

  public Integer getConnectionTimeoutInSeconds() {
    return connectionTimeoutInSeconds;
  }

  public void setConnectionTimeoutInSeconds(Integer connectionTimeoutInSeconds) {
    this.connectionTimeoutInSeconds = connectionTimeoutInSeconds;
  }

  public Integer getReadTimeoutInSeconds() {
    return readTimeoutInSeconds;
  }

  public void setReadTimeoutInSeconds(Integer readTimeoutInSeconds) {
    this.readTimeoutInSeconds = readTimeoutInSeconds;
  }
}
