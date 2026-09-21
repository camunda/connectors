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
 * @param url the endpoint this credential is bound to. Mandatory for the authentication types that
 *     are meaningless without one — a static secret: basic, bearer or API key (see {@link
 *     #requiresUrl(Authentication)}) — and, to keep the field's required-ness honest in Modeler,
 *     hidden entirely for the OAuth variants, which already carry their own token endpoint and are
 *     routinely reused across resource URLs. Whichever type is chosen, the consuming connector's
 *     inline URL — when the process author sets one — wins over this value: binding a credential
 *     and pointing a task at a URL are both decisions of the same process author, so an override is
 *     a modelling choice rather than an escalation, and there is no attempt to constrain it here.
 */
@Configuration(
    id = "io.camunda.connectors:rest-authentication:1",
    version = 2,
    name = "REST Authentication")
public record RestAuthenticationConfiguration(
    @Valid
        @TemplateProperty(
            group = "authentication",
            excludeSubTypes = NoAuthentication.class,
            // Overrides the type-level description on Authentication, which tells the reader to
            // select 'None' - a choice this narrowed dropdown does not offer.
            description = "Choose the authentication mechanism this credential provides.")
        Authentication authentication,
    @Pattern(
            regexp = RestAuthenticationConfiguration.URL_PATTERN,
            message = "Must be a http(s) URL")
        @VerifiedHost(isUri = true)
        @TemplateProperty(
            group = "endpoint",
            label = "URL",
            description =
                "The endpoint this credential is bound to. Required for basic, bearer and API key"
                    + " authentication; not shown for OAuth, which carries its own token"
                    + " endpoint.",
            condition =
                @PropertyCondition(
                    property = "authentication.type",
                    oneOf = {
                      ApiKeyAuthentication.TYPE,
                      BasicAuthentication.TYPE,
                      BearerAuthentication.TYPE
                    }),
            constraints = @PropertyConstraints(notEmpty = true))
        String url) {

  /**
   * A blank URL means "not set", not "set to an invalid value": Modeler writes an empty value when
   * the field is cleared, and the unconditional {@link Pattern} below would otherwise reject it.
   * Collapsing it to {@code null} also makes the field absent for {@link
   * #isUrlPresentWhenRequired()} and for a consuming connector's fallback to {@link #url()}.
   */
  public RestAuthenticationConfiguration {
    if (url != null && url.isBlank()) {
      url = null;
    }
  }

  /**
   * Unlike the inline URL of a connector, this one is never a FEEL expression — configuration
   * values are atomic literals or secret references (ADR-0004) — so a leading {@code =} is not
   * accepted here.
   */
  @TemplateProperty(ignore = true)
  public static final String URL_PATTERN = "^((http://|https://|secrets|\\{\\{).*$)";

  /**
   * True if {@code authentication} is one of the types for which a {@link #url()} is mandatory: a
   * static secret (basic, bearer, API key) is bound to the endpoint it was issued for and is
   * meaningless without it. This is about requiredness only — every type may carry a URL, and the
   * OAuth variants simply need not.
   */
  public static boolean requiresUrl(Authentication authentication) {
    return authentication instanceof BasicAuthentication
        || authentication instanceof BearerAuthentication
        || authentication instanceof ApiKeyAuthentication;
  }

  /**
   * The URL is mandatory exactly when the chosen authentication type requires one. The element
   * template above enforces this in Modeler: the field is shown, and required, only for the
   * authentication types {@link #requiresUrl(Authentication)} names. This method remains the
   * backstop for paths that bypass Modeler entirely — a hand-edited BPMN file or a credential
   * constructed directly against the API.
   */
  @AssertTrue(message = "URL is required for this authentication type")
  @JsonIgnore
  public boolean isUrlPresentWhenRequired() {
    return !requiresUrl(authentication) || (url != null && !url.isBlank());
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
