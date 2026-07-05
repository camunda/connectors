/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model.auth;

import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.generator.java.annotation.TemplateSubType;

@TemplateSubType(id = ApiKeyAuthentication.TYPE, label = "API key")
public record ApiKeyAuthentication(
    @FEEL
        @TemplateProperty(
            group = "authentication",
            label = "API key",
            description =
                "Shared API key for authenticating to the App Integrations backend (self-managed).",
            tooltip =
                "Tip: store the API key as a secret, e.g. <code>= secrets.APP_INTEGRATIONS_API_KEY</code>.")
        String apiKey)
    implements AppIntegrationsAuthentication {

  @TemplateProperty(ignore = true)
  public static final String TYPE = "apikey";

  @Override
  public String toString() {
    return "ApiKeyAuthentication{apiKey=***}";
  }
}
