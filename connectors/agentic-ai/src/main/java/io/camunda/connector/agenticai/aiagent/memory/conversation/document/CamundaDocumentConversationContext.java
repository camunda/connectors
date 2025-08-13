/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationContext;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.agenticai.model.message.Message;
import io.camunda.connector.api.document.Document;
import java.util.List;

@AgenticAiRecord
@JsonDeserialize(
    builder =
        CamundaDocumentConversationContext.CamundaDocumentConversationContextJacksonProxyBuilder
            .class)
public record CamundaDocumentConversationContext(
    String conversationId, Document document, List<Document> previousDocuments)
    implements ConversationContext, CamundaDocumentConversationContextBuilder.With {

  public static CamundaDocumentConversationContextBuilder builder() {
    return CamundaDocumentConversationContextBuilder.builder();
  }

  public static CamundaDocumentConversationContextBuilder builder(String conversationId) {
    return builder().conversationId(conversationId);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class CamundaDocumentConversationContextJacksonProxyBuilder
      extends CamundaDocumentConversationContextBuilder {}

  public record DocumentContent(List<Message> messages) {}
}
