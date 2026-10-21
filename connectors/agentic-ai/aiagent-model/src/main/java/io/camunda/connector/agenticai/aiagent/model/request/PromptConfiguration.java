/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.request;

import io.camunda.connector.api.document.Document;
import java.util.List;

/** A prompt of the agent. */
public interface PromptConfiguration {
  String prompt();

  /** The system prompt of the agent. */
  record SystemPromptConfiguration(String prompt) implements PromptConfiguration {}

  /**
   * The user prompt of one invocation.
   *
   * @param documents the documents to include in the prompt, empty if none are configured
   */
  record UserPromptConfiguration(String prompt, List<Document> documents)
      implements PromptConfiguration {}
}
