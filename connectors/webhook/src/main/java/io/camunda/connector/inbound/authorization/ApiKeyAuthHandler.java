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
import io.camunda.connector.inbound.model.WebhookAuthorization.ApiKeyAuth;
import io.camunda.connector.inbound.utils.HttpWebhookUtil;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ApiKeyAuthHandler extends AuthorizationHandler<ApiKeyAuth> {
  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyAuthHandler.class);

  public ApiKeyAuthHandler(ApiKeyAuth authorization, WebhookProcessingPayload payload) {
    super(authorization, payload);
  }

  private String apiKeyValue = null;
  private boolean isEvaluated = false;

  private String getApiKeyValue() {
    isEvaluated = true;
    try {
      WebhookTriggerResultContext result =
          new WebhookTriggerResultContext(
              new MappedHttpRequest(
                  HttpWebhookUtil.transformRawBodyToMap(
                      payload.rawBody(), HttpWebhookUtil.extractContentType(payload.headers())),
                  payload.headers(),
                  payload.params()),
              Map.of());

      return expectedAuthorization.apiKeyLocator().apply(result);
    } catch (Exception e) {
      LOG.info("Error while extracting API key value", e);
      return null;
    }
  }

  @Override
  public boolean isPresent() {
    if (apiKeyValue == null && !isEvaluated) {
      apiKeyValue = getApiKeyValue();
    }
    return apiKeyValue != null;
  }

  @Override
  public boolean isValid() {
    if (apiKeyValue == null && !isEvaluated) {
      apiKeyValue = getApiKeyValue();
    }
    return apiKeyValue != null && apiKeyValue.equals(expectedAuthorization.apiKey());
  }
}
