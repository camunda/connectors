/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import com.auth0.jwk.JwkProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HttpHeaders;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.inbound.model.WebhookConnectorProperties;
import io.camunda.connector.inbound.model.authorization.JWTProperties;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.util.Base64;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationService {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthorizationService.class);

  /**
   * Verifies authorization for the webhook request.
   *
   * @param props The WebhookConnectorProperties.
   * @param payload The WebhookProcessingPayload.
   * @param jwkProvider The JwkProvider for JWT verification.
   * @param objectMapper The ObjectMapper for JSON processing.
   * @throws WebhookAuthorizationException If the authorization check fails.
   */
  public static void verifyAuthorization(
      final WebhookConnectorProperties props,
      final WebhookProcessingPayload payload,
      final JwkProvider jwkProvider,
      final ObjectMapper objectMapper)
      throws WebhookAuthorizationException {

    switch (props.getAuthorizationType()) {
      case NONE -> LOGGER.debug("Webhook request without authorization");
      case BASIC -> verifyBasicAuthOrThrow(props, payload);
      case API_KEY -> verifyApiKeyOrThrow(props, payload);
      case JWT -> verifyJWTorThrow(
          new JWTProperties(
              props.getRequiredPermissions(), props.getJwtRoleExpression(), payload.headers()),
          jwkProvider,
          objectMapper);
    }
  }

  private static void verifyApiKeyOrThrow(
      final WebhookConnectorProperties props, final WebhookProcessingPayload payload)
      throws WebhookAuthorizationException {
    String key;
    switch (Objects.requireNonNull(props.getApiKeyProperties()).getType()) {
      case HEADER -> key =
          HttpWebhookUtil.extractHeader(payload.headers(), props.getApiKeyProperties().getKey());
      case QUERY_PARAMS -> key =
          HttpWebhookUtil.extractHeader(payload.params(), props.getApiKeyProperties().getKey());
      default -> key = "";
    }
    if (!key.equals(props.getApiKeyProperties().getValue())) {
      throw new WebhookAuthorizationException("API key check didn't pass");
    }
  }

  private static void verifyBasicAuthOrThrow(
      final WebhookConnectorProperties props, final WebhookProcessingPayload payload)
      throws WebhookAuthorizationException {
    var authProps = Objects.requireNonNull(props.getBasicAuthProperties());
    var actualHeader = HttpWebhookUtil.extractHeader(payload.headers(), HttpHeaders.AUTHORIZATION);
    var expectedHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (authProps.getUsername() + ":" + authProps.getPassword()).getBytes());

    if (!actualHeader.equals(expectedHeader)) {
      throw new WebhookAuthorizationException("Basic authorization check didn't pass");
    }
  }

  private static void verifyJWTorThrow(
      final JWTProperties jwtProperties,
      final JwkProvider jwkProvider,
      final ObjectMapper objectMapper)
      throws WebhookAuthorizationException {
    if (!JWTChecker.verify(jwtProperties, jwkProvider, objectMapper)) {
      throw new WebhookAuthorizationException("JWT check didn't pass");
    }
  }
}
