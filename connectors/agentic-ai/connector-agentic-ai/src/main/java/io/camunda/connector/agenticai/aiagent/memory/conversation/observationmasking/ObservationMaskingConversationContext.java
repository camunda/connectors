/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.observationmasking;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.common.AgenticAiRecord;
import java.util.List;

@AgenticAiRecord
@JsonDeserialize(
    builder =
        ObservationMaskingConversationContext
            .ObservationMaskingConversationContextJacksonProxyBuilder.class)
public record ObservationMaskingConversationContext(
    String conversationId,
    String snapshotId,
    String storeUrl,
    List<Message> messages,
    CaseLedger ledger)
    implements ConversationContext, ObservationMaskingConversationContextBuilder.With {

  public ObservationMaskingConversationContext {
    messages = List.copyOf(messages);
  }

  public static ObservationMaskingConversationContextBuilder builder() {
    return ObservationMaskingConversationContextBuilder.builder();
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class ObservationMaskingConversationContextJacksonProxyBuilder
      extends ObservationMaskingConversationContextBuilder {}
}
