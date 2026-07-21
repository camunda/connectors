/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.memory.conversation.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.agenticai.aiagent.memory.conversation.ConversationSchemaMigration;
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.api.document.Document;
import java.io.IOException;

public class CamundaDocumentConversationSerializer {
  private final ObjectMapper objectMapper;
  private final ObjectWriter objectWriter;

  public CamundaDocumentConversationSerializer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
  }

  /**
   * Reads the conversation document content, migrating its {@code messages} in place when {@code
   * schemaVersion} predates {@link AgentContext#CURRENT_SCHEMA_VERSION} (Camunda 8.9 tool-call
   * results persisted with a flat {@code content} field).
   */
  public DocumentContent readDocumentContent(Document document, int schemaVersion)
      throws IOException {
    if (schemaVersion >= AgentContext.CURRENT_SCHEMA_VERSION) {
      return objectMapper.readValue(document.asInputStream(), DocumentContent.class);
    }

    JsonNode tree = objectMapper.readTree(document.asInputStream());
    ConversationSchemaMigration.upcastMessages(tree.get("messages"), objectMapper);
    return objectMapper.treeToValue(tree, DocumentContent.class);
  }

  public String writeDocumentContent(DocumentContent content) throws JsonProcessingException {
    return objectWriter.writeValueAsString(content);
  }
}
