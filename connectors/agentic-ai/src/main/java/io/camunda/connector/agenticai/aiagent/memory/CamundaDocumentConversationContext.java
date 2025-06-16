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
import io.camunda.document.Document;
import java.util.List;
import java.util.Map;

@AgenticAiRecord
@JsonDeserialize(
    builder =
        CamundaDocumentConversationContext.CamundaDocumentConversationContextJacksonProxyBuilder
            .class)
public record CamundaDocumentConversationContext(
    String id, Document document, List<Document> previousDocuments, Map<String, Object> properties)
    implements ConversationContext, CamundaDocumentConversationContextBuilder.With {

  public static CamundaDocumentConversationContextBuilder builder() {
    return CamundaDocumentConversationContextBuilder.builder();
  }

  public static CamundaDocumentConversationContextBuilder builder(String id) {
    return builder().id(id);
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class CamundaDocumentConversationContextJacksonProxyBuilder
      extends CamundaDocumentConversationContextBuilder {}

  public record DocumentContent(List<Message> messages) {}
}
