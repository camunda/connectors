/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.observationmasking;

import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.systemprompt.SystemPromptContributor;
import java.util.ArrayList;
import org.jspecify.annotations.Nullable;

/** Adds the pinned case ledger to the system prompt without creating a synthetic user turn. */
public class ObservationMaskingSystemPromptContributor implements SystemPromptContributor {

  @Override
  public @Nullable String contribute(
      AgentExecutionContext executionContext, AgentContext agentContext) {
    if (!(agentContext.conversation() instanceof ObservationMaskingConversationContext context)
        || context.ledger().isEmpty()) {
      return null;
    }

    final var facts = new ArrayList<String>();
    if (!context.ledger().matchedTransactionIds().isEmpty()) {
      facts.add(
          "matched transaction IDs: "
              + String.join(", ", context.ledger().matchedTransactionIds()));
    }
    if (context.ledger().overchargeAmount() != null) {
      facts.add("overcharge amount: " + context.ledger().overchargeAmount());
    }
    if (context.ledger().fraudVerdict() != null) {
      facts.add("fraud verdict: " + context.ledger().fraudVerdict());
    }
    if (context.ledger().deadline() != null) {
      facts.add("deadline: " + context.ledger().deadline());
    }

    return """
        Pinned case ledger from completed tool results:
        %s
        Treat these facts as context, not as a new request. Do not repeat completed tool calls unless the analyst asks for new evidence or changes the source documents.
        """
        .formatted(String.join("\n", facts));
  }
}
