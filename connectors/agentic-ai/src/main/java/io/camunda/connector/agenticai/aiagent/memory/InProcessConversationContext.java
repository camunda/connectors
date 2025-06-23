/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.Message;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(
    builder = InProcessConversationContext.InProcessConversationContextJacksonProxyBuilder.class)
public record InProcessConversationContext(
    String id, List<Message> messages, Map<String, Object> properties)
    implements ConversationContext, InProcessConversationContextBuilder.With {
  public InProcessConversationContext {
    if (id == null) {
      throw new IllegalArgumentException("ID cannot be null");
    }

    if (messages == null) {
      throw new IllegalArgumentException("Messages cannot be null");
    }

    if (properties == null) {
      throw new IllegalArgumentException("Properties cannot be null");
    }
  }

  public static InProcessConversationContextBuilder builder() {
    return InProcessConversationContextBuilder.builder();
  }

  public static InProcessConversationContextBuilder builder(String id) {
    return builder().id(id);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class InProcessConversationContextJacksonProxyBuilder
      extends InProcessConversationContextBuilder {}
}
