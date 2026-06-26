/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model;

import io.camunda.connector.agenticai.aiagent.model.message.SystemMessage;
import java.util.List;
import java.util.Optional;

/**
 * Reconstructed view of the conversation prior to the current invocation: the optional leading
 * system message and the completed turns. Decoupled from {@link TurnReconstructor} (which is only a
 * helper to rebuild this structure from the stored flat message list) so consumers depend on the
 * domain shape rather than the reconstruction mechanism.
 */
public record PreviousConversation(
    Optional<SystemMessage> systemMessage, List<AgentConversationTurn> turns) {

  public PreviousConversation {
    turns = List.copyOf(turns);
  }
}
