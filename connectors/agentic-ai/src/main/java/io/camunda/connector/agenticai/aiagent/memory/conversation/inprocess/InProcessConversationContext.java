/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.inprocess;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(
    builder = InProcessConversationContext.InProcessConversationContextJacksonProxyBuilder.class)
public record InProcessConversationContext(
    String conversationId,
    List<Message> messages,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> properties)
    implements ConversationContext, InProcessConversationContextBuilder.With {
  public static InProcessConversationContextBuilder builder() {
    return InProcessConversationContextBuilder.builder();
  }

  public static InProcessConversationContextBuilder builder(String conversationId) {
    return builder().conversationId(conversationId);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class InProcessConversationContextJacksonProxyBuilder
      extends InProcessConversationContextBuilder {}
}
