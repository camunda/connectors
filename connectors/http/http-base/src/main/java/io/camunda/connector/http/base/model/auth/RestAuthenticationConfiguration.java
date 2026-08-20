/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.api.annotation.Configuration;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyCondition;
import io.camunda.connector.generator.java.annotation.TemplateProperty.PropertyConstraints;
import io.camunda.connector.hostvalidator.VerifiedHost;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;

/**
 * Configuration (credential) template for reusable REST authentication. Reuses the existing sealed
 * {@link Authentication} union (none / basic / bearer / API key / OAuth / OAuth refresh token),
 * demonstrating that a rich multi-variant auth model maps onto the configuration-template format.
 *
 * <p>Lives in http-base (rather than a single connector's module) so every connector built on the
 * shared {@link Authentication} union — REST, GraphQL, HTTP polling — can bind the same
 * configuration template.
 *
 * <p>The union is narrowed here: {@link NoAuthentication} is excluded, because a credential that
 * authenticates with nothing is not a credential — there would be nothing to store, share or
 * rotate. A connector needing no authentication simply leaves the chooser unset and uses its inline
 * fields.
 *
 * @param authentication the reusable authentication. Never {@link NoAuthentication}.
 * @param url the endpoint this credential is bound to, carried only for the authentication types
 *     that are host-bound by nature — a static secret: basic, bearer or API key. The OAuth variants
 *     already carry their own token endpoint and are routinely reused across resource URLs, so they
 *     omit it (see {@link #carriesUrl(Authentication)}). The consuming connector may override it;
 *     see {@code HttpJsonRequest#getUrl()}.
 */
@Configuration(
    id = "io.camunda.connectors:rest-authentication:1",
    version = 1,
    name = "REST Authentication")
public record RestAuthenticationConfiguration(
    @Valid @TemplateProperty(group = "authentication", excludeSubTypes = NoAuthentication.class)
        Authentication authentication,
    @Pattern(
            regexp = RestAuthenticationConfiguration.URL_PATTERN,
            message = "Must be a http(s) URL")
        @VerifiedHost(isUri = true)
        @TemplateProperty(
            group = "endpoint",
            label = "URL",
            condition =
                @PropertyCondition(
                    property = "authentication.type",
                    oneOf = {
                      BasicAuthentication.TYPE,
                      BearerAuthentication.TYPE,
                      ApiKeyAuthentication.TYPE
                    }),
            constraints = @PropertyConstraints(notEmpty = true),
            description =
                "The endpoint this credential is bound to. A connector using it may override this"
                    + " URL.")
        String url) {

  /**
   * Unlike the inline URL of a connector, this one is never a FEEL expression — configuration
   * values are atomic literals or secret references (ADR-0004) — so a leading {@code =} is not
   * accepted here.
   */
  @TemplateProperty(ignore = true)
  public static final String URL_PATTERN = "^((http://|https://|secrets|\\{\\{).*$)";

  /**
   * True if {@code authentication} is one of the types for which this credential carries a {@link
   * #url()} — mirroring the {@code authentication.type} condition on the field, so the editor and
   * the runtime agree on when the URL is part of the credential.
   */
  public static boolean carriesUrl(Authentication authentication) {
    return authentication instanceof BasicAuthentication
        || authentication instanceof BearerAuthentication
        || authentication instanceof ApiKeyAuthentication;
  }

  /**
   * The URL is mandatory exactly when the chosen authentication type carries one. Enforced on the
   * record (not as a {@code @NotBlank} on the component) because the requirement is conditional —
   * an OAuth credential legitimately has no URL.
   */
  @AssertTrue(message = "URL is required for this authentication type")
  @JsonIgnore
  public boolean isUrlPresentWhenRequired() {
    return !carriesUrl(authentication) || (url != null && !url.isBlank());
  }

  /**
   * {@link NoAuthentication} is excluded from the chooser's dropdown by {@code excludeSubTypes}, so
   * the editor cannot produce it. Rejecting it here too closes the hand-edited and hand-constructed
   * paths, and covers the absent case: a credential must carry an authentication, which the
   * discriminator dropdown alone cannot require.
   */
  @AssertTrue(message = "A credential must specify an authentication mechanism other than 'None'")
  @JsonIgnore
  public boolean isAuthenticationSupported() {
    return authentication != null && !(authentication instanceof NoAuthentication);
  }
}
