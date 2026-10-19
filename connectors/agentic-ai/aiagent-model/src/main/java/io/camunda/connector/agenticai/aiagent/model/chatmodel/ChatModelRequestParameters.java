/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.chatmodel;

import org.jspecify.annotations.Nullable;

/**
 * Generation parameters configured for a chat model, as far as the provider exposes them. A value
 * is null when it is not configured and the provider default applies.
 */
public record ChatModelRequestParameters(
    @Nullable Long maxTokens,
    @Nullable Double temperature,
    @Nullable Double topP,
    @Nullable Long topK) {

  /** No parameter is known. */
  public static final ChatModelRequestParameters NONE =
      new ChatModelRequestParameters(null, null, null, null);
}
