/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.DocumentReturnChoice;
import io.camunda.connector.generator.java.annotation.DocumentReturnFormat;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.NestedProperties;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.NullableBoolean;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyType;
import io.camunda.connector.hostvalidator.VerifiedHost;
import io.camunda.connector.http.base.model.auth.Authentication;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@DocumentReturnFormat(
    group = "endpoint",
    defaultFormat = DocumentReturnChoice.JSON,
    tooltip =
        "How the response body should be returned. Document reference uploads the body to the"
            + " document store; as text decodes it as a String; as JSON parses it into a structure"
            + " you can access via dot notation.")
public class HttpCommonRequest {

  /**
   * Shared by every URL property built on this model, inline or credential-override. Matches the
   * empty string too: the override field is optional, and without that alternative Modeler's
   * client-side pattern check rejects a blank value even though {@code notEmpty} correctly allows
   * it - requiredness and shape are two different constraints.
   */
  @TemplateProperty(ignore = true)
  public static final String URL_PATTERN = "^($|=|(http://|https://|secrets|\\{\\{).*$)";

  @TemplateProperty(ignore = true)
  public static final String URL_PATTERN_MESSAGE = "Must be a http(s) URL";

  @TemplateProperty(ignore = true)
  private static final int DEFAULT_TIMEOUT = 20;

  @FEEL
  @NotNull
  @TemplateProperty(group = "endpoint", id = "method", defaultValue = "GET")
  private HttpMethod method;

  // Only @NotBlank moved off this field: once a credential can supply the URL, the inline value may
  // legitimately be absent, so presence is asserted on the effective value in isUrlPresent() below.
  // The shape checks stay here so a malformed inline value is still reported against `url` rather
  // than against a synthetic property - they pass on a null value and reject a blank one, which is
  // exactly right: Modeler omits the input when the optional override is left empty. A URL coming
  // from the credential is shape-checked by RestAuthenticationConfiguration's own constraints.
  // notEmpty is spelled out in the template constraints below because the generator derives it from
  // @NotBlank, which this field no longer carries.
  @FEEL
  @Pattern(regexp = URL_PATTERN, message = URL_PATTERN_MESSAGE)
  @VerifiedHost(isUri = true)
  @TemplateProperty(
      group = "endpoint",
      label = "URL",
      feel = FeelMode.optional,
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.TRUE),
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern =
                  @TemplateProperty.Pattern(value = URL_PATTERN, message = URL_PATTERN_MESSAGE)))
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
                  @TemplateProperty.Pattern(value = URL_PATTERN, message = URL_PATTERN_MESSAGE)),
      description =
          "Optional. Overrides the URL of the selected reusable credential; leave empty to use"
              + " the credential's own URL.")
  private String urlOverride;

  // Hidden and un-required (via the isEmpty condition) once a credential is chosen. The chooser
  // (authenticationConfiguration) is declared in the HttpJsonRequest subclass, which renders
  // before this field since subclass fields precede superclass fields.
  @NestedProperties(
      condition =
          @PropertyCondition(
              property = "authenticationConfiguration",
              isEmpty = NullableBoolean.TRUE))
  @Valid
  private Authentication authentication;

  @Valid private ClientTls clientTls;

  @TemplateProperty(
      group = "timeout",
      label = "Connection timeout in seconds",
      defaultValueType = TemplateProperty.DefaultValueType.Number,
      defaultValue = "20",
      constraints =
          @TemplateProperty.PropertyConstraints(
              notEmpty = true,
              pattern = @TemplateProperty.Pattern(value = "^\\d+$", message = "Must be a number")),
      tooltip = "Use 0 for an infinite timeout")
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
      tooltip = "Use 0 for an infinite timeout")
  private Integer readTimeoutInSeconds;

  @FEEL
  @TemplateProperty(
      feel = FeelMode.required,
      group = "endpoint",
      optional = true,
      tooltip = "Map of HTTP headers to add to the request")
  private Map<String, String> headers;

  @FEEL
  @TemplateProperty(
      label = "Request body",
      tooltip = "Payload to send with the request",
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
      tooltip = "Map of query parameters to add to the request URL")
  private Map<String, String> queryParameters;

  @TemplateProperty(ignore = true)
  @Deprecated
  private boolean storeResponse;

  @TemplateProperty(
      label = "Skip URL encoding",
      tooltip = "Skip the default URL decoding and encoding behavior",
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

  @TemplateProperty(
      group = "endpoint",
      type = PropertyType.Boolean,
      label = "Follow redirects",
      tooltip =
          "If enabled, HTTP 3xx redirects will be followed automatically. Disabled by default.",
      defaultValueType = TemplateProperty.DefaultValueType.Boolean,
      defaultValue = "false")
  private boolean followRedirects;

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

  /**
   * A blank inline URL means "not set", not "set to an invalid value". Normalizing it to {@code
   * null} on the way in is what lets the shape check stay on the field: Modeler may write an empty
   * input when the optional override is cleared, and a bound credential's URL must then take over
   * rather than {@code @Pattern} rejecting {@code ""}. It also turns a blank URL with no credential
   * into the accurate "No URL provided by the credential or the element template" rather than
   * "Must be a http(s) URL".
   */
  public void setUrl(final String url) {
    this.url = url == null || url.isBlank() ? null : url;
  }

  /**
   * The URL is required, but a subclass may source it from a bound credential by overriding {@link
   * #getUrl()}. Asserting on the getter (not the field) respects that override while preserving the
   * original requirement for subclasses without a credential - the same shape as {@code
   * JdbcRequest#isConnectionSourceProvided()}. A field-level {@code @NotBlank} could not do this:
   * the inline field is legitimately blank when the credential supplies the URL.
   */
  @AssertTrue(message = "No URL provided by the credential or the element template")
  @JsonIgnore
  public boolean isUrlPresent() {
    String effectiveUrl = getUrl();
    return effectiveUrl != null && !effectiveUrl.isBlank();
  }

  public Authentication getAuthentication() {
    return authentication;
  }

  public void setAuthentication(final Authentication authentication) {
    this.authentication = authentication;
  }

  public ClientTls getClientTls() {
    return clientTls;
  }

  public void setClientTls(final ClientTls clientTls) {
    this.clientTls = clientTls;
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

  public boolean isFollowRedirects() {
    return followRedirects;
  }

  public void setFollowRedirects(final boolean followRedirects) {
    this.followRedirects = followRedirects;
  }
}
