/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import static io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice.enabled;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.inbound.InboundConnectorContext;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.inbound.model.WebhookAuthorization;
import io.camunda.connector.inbound.model.WebhookAuthorization.None;
import io.camunda.connector.inbound.signature.HMACSwitchCustomerChoice;

/**
 * Rejects activation of a webhook-family inbound connector whose every permissive layer is combined
 * at once: no authorization ({@link None}, which the property wrapper constructors also substitute
 * for an unset {@code auth}) together with HMAC verification not enabled. Either layer alone is a
 * legitimate configuration; only the combination leaves the endpoint fully unauthenticated on a
 * low-entropy, modeler-chosen path.
 *
 * <p>Shared by every webhook-family connector (e.g. {@code HttpWebhookExecutable} and the A2A
 * client webhook connector) so the rejection behaves identically across variants.
 */
public final class WebhookAuthorizationGuard {

  /**
   * The exact runtime opt-in named by security-testing-findings#266's remediation hint 1: "reject
   * the unauthenticated combination at activate() unless an explicit
   * camunda.connector.webhook.allow-unauthenticated=true runtime opt-in is set." Read as a JVM
   * system property (e.g. {@code -Dcamunda.connector.webhook.allow-unauthenticated=true}):
   * webhook-family connector modules have no Spring wiring to read {@code application.properties}
   * or environment variables through Spring's relaxed binding directly, and a system property is
   * the one channel that both matches the property key verbatim and is visible to a plain Java
   * library. No other opt-in mechanism is added — the finding names exactly this one.
   */
  public static final String ALLOW_UNAUTHENTICATED_PROPERTY =
      "camunda.connector.webhook.allow-unauthenticated";

  private WebhookAuthorizationGuard() {}

  public static boolean isFullyUnauthenticated(
      WebhookAuthorization auth, HMACSwitchCustomerChoice shouldValidateHmac) {
    boolean noAuthorization = auth == null || auth instanceof None;
    boolean hmacNotEnabled = !enabled.equals(shouldValidateHmac);
    return noAuthorization && hmacNotEnabled;
  }

  public static boolean isUnauthenticatedActivationAllowed() {
    return Boolean.getBoolean(ALLOW_UNAUTHENTICATED_PROPERTY);
  }

  /**
   * Fails activation when {@code auth} and {@code shouldValidateHmac} combine into a fully
   * unauthenticated webhook, unless the operator opted in via {@link
   * #ALLOW_UNAUTHENTICATED_PROPERTY} — in which case activation proceeds but a WARNING activity is
   * logged so the exposure surfaces in Manage & Run.
   */
  public static void rejectUnauthenticatedActivation(
      InboundConnectorContext context,
      WebhookAuthorization auth,
      HMACSwitchCustomerChoice shouldValidateHmac) {
    if (!isFullyUnauthenticated(auth, shouldValidateHmac)) {
      return;
    }
    if (isUnauthenticatedActivationAllowed()) {
      context.log(
          activity ->
              activity
                  .withSeverity(Severity.WARNING)
                  .withTag("webhook-authorization")
                  .withMessage(
                      "This webhook has no authorization and HMAC verification disabled, so it "
                          + "accepts unauthenticated requests. This is only permitted because '"
                          + ALLOW_UNAUTHENTICATED_PROPERTY
                          + "' is set."));
      return;
    }
    throw new ConnectorInputException(
        "This webhook has no authorization (auth type 'None') and HMAC verification disabled, "
            + "which would accept unauthenticated requests. Configure an authorization type (API "
            + "key, Basic, or JWT) or enable HMAC verification. To deploy an intentionally "
            + "unauthenticated webhook, set the '"
            + ALLOW_UNAUTHENTICATED_PROPERTY
            + "' system property to true.");
  }
}
