/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent;

import io.camunda.connector.agenticai.aiagent.chatmodel.connection.RequestCustomizations;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.OpenAiConfiguration;

/** Domain chat model configurations for tests which do not care about their details. */
public final class ChatModelConfigurationFixture {

  private ChatModelConfigurationFixture() {}

  public static OpenAiConfiguration openAi(String model) {
    return new OpenAiConfiguration(
        new OpenAiConfiguration.Api.Responses(null, null, null, null),
        new OpenAiConfiguration.Backend.Api(
            "test-api-key", null, null, null, RequestCustomizations.NONE),
        model,
        ChatModelTranslationFixture.DEFAULT_TIMEOUT);
  }
}
