/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.inbound.authorization;

import io.camunda.connector.api.inbound.webhook.MappedHttpRequest;
import io.camunda.connector.api.inbound.webhook.WebhookProcessingPayload;
import io.camunda.connector.api.inbound.webhook.WebhookTriggerResultContext;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Failure.InvalidCredentials;
import io.camunda.connector.inbound.authorization.AuthorizationResult.Success;
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiKeyAuthHandler extends WebhookAuthorizationHandler<ApiKeyAuth> {
  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthHandler.class);

  public ApiKeyAuthHandler(ApiKeyAuth authorization) {
    super(authorization);
  }

  @Override
  public AuthorizationResult checkAuthorization(WebhookProcessingPayload payload) {
    try {
      WebhookTriggerResultContext result =
          new WebhookTriggerResultContext(
              new MappedHttpRequest(
                  HttpWebhookUtil.transformRawBodyToMap(
                      payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())),
                  payload.headers(),
                  payload.params()),
              Map.of());

      String apiKeyValue = expectedAuthorization.apiKeyLocator().apply(result);
      if (apiKeyValue == null) {
        return API_KEY_MISSING_RESULT;
      }
      if (!apiKeyValue.equals(expectedAuthorization.apiKey())) {
        return API_KEY_INVALID_RESULT;
      }
      return Success.INSTANCE;
    } catch (Exception e) {
      LOG.info("Error while extracting API key value", e);
      return new InvalidCredentials(e.getMessage());
    }
  }

  private static final AuthorizationResult API_KEY_MISSING_RESULT =
      new InvalidCredentials("API key is missing");
  private static final AuthorizationResult API_KEY_INVALID_RESULT =
      new InvalidCredentials("API key is invalid");
}
