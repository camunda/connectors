/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request.chatmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * OpenAI wire-format selector. Maps to the capability-matrix api-family keys in {@code
 * model-capabilities.yaml} ({@code openai-completions} / {@code openai-responses}).
 */
public enum OpenAiApiFamily {
  @JsonProperty("completions")
  COMPLETIONS("openai-completions"),
  @JsonProperty("responses")
  RESPONSES("openai-responses");

  private final String familyKey;

  OpenAiApiFamily(String familyKey) {
    this.familyKey = familyKey;
  }

  /** The api-family key used to look the model up in the capability matrix. */
  public String familyKey() {
    return familyKey;
  }
}
