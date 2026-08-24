/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.graphql.model;

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
import io.camunda.connector.http.base.model.HttpCommonRequest;
import io.camunda.connector.http.base.model.HttpMethod;
import io.camunda.connector.http.base.model.auth.Authentication;
import io.camunda.connector.http.base.model.auth.RestAuthenticationConfiguration;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
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
 * @param authenticationConfiguration reusable REST authentication credential; when bound, takes
 *     precedence over {@code authentication} (see {@link #authentication()}). Declared before
 *     {@code authentication} so it renders first and satisfies ConditionPropertyOrderRule for the
 *     isEmpty condition below.
 * @param authentication hidden and un-required once a credential is bound above.
 */
@DocumentReturnFormat(
    group = "endpoint",
    defaultFormat = DocumentReturnChoice.JSON,
    tooltip =
        "How the response body should be returned. Document reference uploads the body to the"
            + " document store; as text decodes it as a String; as JSON parses it into a structure"
            + " you can access via dot notation.")
public record GraphQLRequest(
    @Valid GraphQL graphql,
    @TemplateProperty(
            id = "authenticationConfiguration",
            label = "Authentication credential",
            group = "authentication",
            type = PropertyType.Configuration,
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "authenticationConfiguration"),
            description =
                "Choose a reusable authentication credential, or configure one-time"
                    + " authentication parameters below.")
        @Valid
        RestAuthenticationConfiguration authenticationConfiguration,
    @NestedProperties(
            condition =
                @PropertyCondition(
                    property = "authenticationConfiguration",
                    isEmpty = NullableBoolean.TRUE))
        @Valid
        Authentication authentication,
    // Template-only twin of `graphql.url`, bound to the same input and shown in its place once a
    // credential is chosen: there the URL may come from the credential, so the inline value is an
    // optional override rather than a required field. Declared on the outer record (rather than
    // inside GraphQL) so the nested record's canonical constructor stays untouched; the explicit
    // binding is what places it on `graphql.url`. Never populated - the engine writes a single
    // `graphql.url` input, which Jackson binds to GraphQL#url above.
    @TemplateProperty(
            id = "urlOverride",
            group = "endpoint",
            label = "URL",
            optional = true,
            binding = @TemplateProperty.PropertyBinding(name = "graphql.url"),
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
                "Required for an OAuth credential, which carries no URL of its own; not allowed"
                    + " once a Basic/Bearer/API-key credential is bound.")
        String urlOverride) {

  /** Convenience constructor for the shape without the template-only URL-override component. */
  public GraphQLRequest(
      GraphQL graphql,
      RestAuthenticationConfiguration authenticationConfiguration,
      Authentication authentication) {
    this(graphql, authenticationConfiguration, authentication, null);
  }

  /**
   * The URL is the one place where the inline value wins over the credential rather than the other
   * way round - but only for OAuth credentials, which carry no URL of their own (see {@link
   * RestAuthenticationConfiguration#carriesUrl}) and so have nothing to conflict with. A
   * Basic/Bearer/API-key credential rejects any inline URL outright (see {@link
   * #isUrlOverrideAbsentForHostBoundCredential()}), so if one is bound and reaches this point, the
   * inline value is necessarily blank. Named as a getter so bean validation treats it as a property
   * - a record-style {@code effectiveUrl()} accessor would not be validated.
   */
  @JsonIgnore
  public String getEffectiveUrl() {
    String inlineUrl = graphql != null ? graphql.url() : null;
    if (inlineUrl != null && !inlineUrl.isBlank()) {
      return inlineUrl;
    }
    return authenticationConfiguration != null ? authenticationConfiguration.url() : null;
  }

  /**
   * The URL is required, but it may come from the bound credential instead of the inline field, so
   * requiredness is asserted on the effective value (see {@link #getEffectiveUrl()}) - the same
   * shape as {@code JdbcRequest#isConnectionSourceProvided()}. A component-level {@code @NotBlank}
   * could not do this: the inline field is legitimately blank when the credential supplies the URL.
   */
  @AssertTrue(message = "No URL provided by the credential or the element template")
  @JsonIgnore
  public boolean isUrlPresent() {
    String effectiveUrl = getEffectiveUrl();
    return effectiveUrl != null && !effectiveUrl.isBlank();
  }

  /**
   * A bound Basic/Bearer/API-key credential's secret must never risk being sent to a different host
   * than the one it was created for, so no inline URL is allowed at all once one is bound - simpler
   * and safer than comparing origins. OAuth credentials carry no URL (see {@link
   * RestAuthenticationConfiguration#carriesUrl}), so the inline value is the only source there and
   * is unaffected by this check.
   */
  @AssertTrue(message = "Inline URL override is not allowed once a credential provides the URL")
  @JsonIgnore
  public boolean isUrlOverrideAbsentForHostBoundCredential() {
    if (authenticationConfiguration == null
        || !RestAuthenticationConfiguration.carriesUrl(
            authenticationConfiguration.authentication())) {
      return true;
    }
    String inlineUrl = graphql != null ? graphql.url() : null;
    return inlineUrl == null || inlineUrl.isBlank();
  }

  /**
   * Per-connector consumption of the bound authentication credential: when a credential
   * (configuration) is bound, its authentication takes precedence; the inline authentication is the
   * fallback. Per-field inline override is not modeled because authentication is a whole object.
   */
  public Authentication authentication() {
    if (authenticationConfiguration != null) {
      return authenticationConfiguration.authentication();
    }
    return authentication;
  }

  public record GraphQL(
      @TemplateProperty(
              id = "query",
              label = "Query/Mutation",
              tooltip =
                  "The GraphQL query or mutation to execute. See the <a href=\"https://docs.camunda.io/docs/components/connectors/protocol/graphql/#querymutation\" target=\"_blank\">GraphQL query/mutation syntax</a>.",
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
          @TemplateProperty(
              feel = FeelMode.required,
              group = "endpoint",
              optional = true,
              tooltip = "Map of HTTP headers to add to the request")
          Map<String, String> headers,
      // Only @NotBlank moved off this component: once a credential can supply the URL, the inline
      // value may legitimately be absent, so presence is asserted on the effective value in
      // GraphQLRequest#isUrlPresent(). The shape checks stay here so a malformed inline value is
      // still reported against `graphql.url` rather than against a synthetic property - they pass
      // on a null value and reject a blank one, which is exactly right: Modeler omits the input
      // when the optional override is left empty. A URL coming from the credential is shape-checked
      // by RestAuthenticationConfiguration's own constraints. notEmpty is spelled out in the
      // template constraints because the generator derives it from @NotBlank, which this component
      // no longer carries. Declared last (after headers) so it lands immediately before the
      // top-level `urlOverride` twin once grouped into the "endpoint" tab - the two occupy the same
      // visual slot and must render adjacent to each other, not split apart by other endpoint
      // fields.
      @FEEL
          @Pattern(
              regexp = HttpCommonRequest.URL_PATTERN,
              message = HttpCommonRequest.URL_PATTERN_MESSAGE)
          @VerifiedHost(isUri = true)
          @TemplateProperty(
              id = "url",
              group = "endpoint",
              label = "URL",
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
          String url,
      @TemplateProperty(ignore = true) @Deprecated boolean storeResponse,
      @TemplateProperty(
              group = "timeout",
              defaultValue = "20",
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              optional = true,
              tooltip = "Use 0 for an infinite timeout")
          Integer connectionTimeoutInSeconds,
      @TemplateProperty(
              group = "timeout",
              label = "Read timeout in seconds",
              defaultValue = "20",
              defaultValueType = TemplateProperty.DefaultValueType.Number,
              optional = true,
              tooltip = "Use 0 for an infinite timeout")
          Integer readTimeoutInSeconds) {

    /**
     * A blank inline URL means "not set", not "set to an invalid value". Normalizing it to {@code
     * null} is what lets the shape check stay on the component: Modeler may write an empty input
     * when the optional override is cleared, and a bound credential's URL must then take over
     * rather than {@code @Pattern} rejecting {@code ""}. It also turns a blank URL with no
     * credential into the accurate "No URL provided by the credential or the element template"
     * rather than "Must be a http(s) URL".
     */
    public GraphQL {
      url = url == null || url.isBlank() ? null : url;
    }
  }
}
