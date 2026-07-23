/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.multimodal;

import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;

/**
 * Sent-only, capability-keyed transform applied to the already-windowed {@link
 * ConversationSnapshot} right before it is sent to the chat model. Routes tool-result documents to
 * an inline content block when the resolved {@link ModelCapabilities} support the document's
 * modality at the {@code toolResult} location, or strips them and inserts a byte-identical {@code
 * <doc/>} synthetic fallback message otherwise. Persists nothing: the transformed snapshot is used
 * only for this model call.
 */
public interface ToolCallResultStrategy {

  ConversationSnapshot routeToolResults(
      ConversationSnapshot snapshot, ModelCapabilities capabilities);
}
