/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.Authentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public class PollingRuntimeProperties {
  @TemplateProperty(
      id = "authenticationConfiguration",
      label = "Authentication credential",
      group = "authentication",
      type = PropertyType.Configuration,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
      description =
          "Choose a reusable authentication credential, or configure one-time authentication"
              + " parameters below.")
  @Valid
  private RestAuthenticationConfiguration authenticationConfiguration;

  // Hidden and un-required (via the isEmpty condition) once a credential is chosen above.
  @NestedProperties(
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.TRUE))
  @Valid
  private Authentication authentication;

  @FEEL
  @NotNull
  @TemplateProperty(group = "endpoint", id = "method", defaultValue = "GET")
  private HttpMethod method;

  // Requiredness and the http(s) shape moved off this field onto getUrl() below: once a credential
  // can supply the URL, it is the *effective* value that must be valid, and this field may
  // legitimately be blank. The template constraints are therefore spelled out here - the generator
  // derives them from field annotations, which no longer carry them.
  @FEEL
  @TemplateProperty(
      group = "endpoint",
      label = "URL",
      feel = FeelMode.optional,
      binding = @TemplateProperty.PropertyBinding(name = "url"),
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.TRUE),
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern =
                  @TemplateProperty.Pattern(
                      value = HttpCommonRequest.URL_PATTERN,
                      message = HttpCommonRequest.URL_PATTERN_MESSAGE)))
  private String url;

  // Template-only twin of `url`, bound to the same `url` input and shown in its place once a
  // credential is chosen: there the URL may come from the credential, so the inline value is an
  // optional override rather than a required field. Never populated - the engine writes a single
  // `url` input, which Jackson binds to the field above.
  @JsonIgnore
  @TemplateProperty(
      id = "urlOverride",
      group = "endpoint",
      label = "URL",
      feel = FeelMode.optional,
      optional = true,
      binding = @TemplateProperty.PropertyBinding(name = "url"),
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.FALSE),
      constraints =
          @TemplateProperty.PropertyConstraints(
              pattern =
                  @TemplateProperty.Pattern(
                      value = HttpCommonRequest.URL_PATTERN,
                      message = HttpCommonRequest.URL_PATTERN_MESSAGE)),
      description =
          "Optional. Overrides the URL of the selected reusable credential; leave empty to use"
              + " the credential's own URL.")
  private String urlOverride;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      tooltip = "Map of query parameters to add to the request URL")
  private Map<String, String> queryParameters;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      tooltip = "Map of HTTP headers to add to the request",
      binding = @TemplateProperty.PropertyBinding(name = "headers"))
  private Map<String, String> headers;

  @FEEL
  @TemplateProperty(
      label = "Request body",
      tooltip = "Payload to send with the request",
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
      tooltip = "Skip the default URL decoding and encoding behavior",
      type = TemplateProperty.PropertyType.Hidden,
      feel = FeelMode.disabled,
      group = "endpoint",
      optional = true)
  private String skipEncoding;

  @TemplateProperty(
      group = "timeout",
      label = "Connection timeout in seconds",
      tooltip = "Use 0 for an infinite timeout",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      feel = FeelMode.optional,
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")))
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
      tooltip = "Use 0 for an infinite timeout")
  @FEEL
  private Integer readTimeoutInSeconds;

  @TemplateProperty(
      group = "endpoint",
      type = TemplateProperty.PropertyType.Boolean,
      label = "Follow redirects",
      tooltip =
          "If enabled, HTTP 3xx redirects will be followed automatically. Disabled by default.",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      defaultValue = "false",
      optional = true)
  @FEEL
  private boolean followRedirects;

  /**
   * Per-connector consumption of the bound authentication credential: when a credential
   * (configuration) is bound, its authentication takes precedence; the inline authentication is the
   * fallback. Per-field inline override is not modeled because authentication is a whole object.
   */
  public Authentication getAuthentication() {
    if (authenticationConfiguration != null) {
      return authenticationConfiguration.authentication();
    }
    return authentication;
  }

  public void setAuthentication(Authentication authentication) {
    this.authentication = authentication;
  }

  public RestAuthenticationConfiguration getAuthenticationConfiguration() {
    return authenticationConfiguration;
  }

  public void setAuthenticationConfiguration(
      RestAuthenticationConfiguration authenticationConfiguration) {
    this.authenticationConfiguration = authenticationConfiguration;
  }

  public HttpMethod getMethod() {
    return method;
  }

  public void setMethod(HttpMethod method) {
    this.method = method;
  }

  /**
   * The URL is the one place where the inline value wins over the credential rather than the other
   * way round: the credential carries the endpoint it is bound to, and the model may override it
   * per task (the {@code urlOverride} template property). An OAuth credential carries no URL at
   * all, so the inline value is the only source there.
   */
  @Pattern(regexp = HttpCommonRequest.URL_PATTERN, message = HttpCommonRequest.URL_PATTERN_MESSAGE)
  public String getUrl() {
    if (url != null && !url.isBlank()) {
      return url;
    }
    return authenticationConfiguration != null ? authenticationConfiguration.url() : null;
  }

  /**
   * The URL is required, but it may come from the bound credential instead of the inline field, so
   * requiredness is asserted on the effective value returned by {@link #getUrl()} - the same shape
   * as {@code JdbcRequest#isConnectionSourceProvided()}. A field-level {@code @NotBlank} could not
   * do this: the inline field is legitimately blank when the credential supplies the URL.
   */
  @AssertTrue(message = "No URL provided by the credential or the element template")
  @JsonIgnore
  public boolean isUrlPresent() {
    String effectiveUrl = getUrl();
    return effectiveUrl != null && !effectiveUrl.isBlank();
  }

  /**
   * A bound Basic/Bearer/API-key credential's secret must never be sent to an origin other than the
   * one it was created for - see {@link RestAuthenticationConfiguration#sharesOriginWith}. The
   * inline override may still change the path/query on that same origin (the intended use: one
   * credential, several call sites on the same host).
   */
  @AssertTrue(message = "Inline URL override must stay on the bound credential's origin")
  @JsonIgnore
  public boolean isUrlOverrideSameOriginAsCredential() {
    if (authenticationConfiguration == null) {
      return true;
    }
    return authenticationConfiguration.sharesOriginWith(url);
  }

  /**
   * A blank inline URL means "not set", not "set to an invalid value". Normalizing it to {@code
   * null} on the way in is what lets the shape check stay on the field: Modeler may write an empty
   * input when the optional override is cleared, and a bound credential's URL must then take over
   * rather than {@code @Pattern} rejecting {@code ""}. It also turns a blank URL with no credential
   * into the accurate "No URL provided by the credential or the element template" rather than "Must
   * be a http(s) URL".
   */
  public void setUrl(String url) {
    this.url = url == null || url.isBlank() ? null : url;
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

  public boolean isFollowRedirects() {
    return followRedirects;
  }

  public void setFollowRedirects(boolean followRedirects) {
    this.followRedirects = followRedirects;
  }
}
