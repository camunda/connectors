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
import io.camunda.connector.agenticai.aiagent.memory.conversation.document.CamundaDocumentConversationContext.DocumentContent;
import io.camunda.connector.agenticai.aiagent.memory.versioning.AgentContextSchemaMigration;
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
   * Reads the conversation document content, migrating it step by step when the payload's own
   * {@code schemaVersion} predates {@link AgentContext#CURRENT_SCHEMA_VERSION}: an unversioned
   * payload (Camunda 8.9 tool-call results persisted with a flat {@code content} field) has its
   * {@code messages} upcast in place, a version 1 payload is bound unchanged. The version is read
   * from the document payload itself — each persisted conversation root is self-describing,
   * mirroring the AWS AgentCore blob envelope — so a legacy payload behind a pointer-style {@code
   * AgentContext} (whose bound {@code schemaVersion} defaults to current) is still migrated
   * correctly.
   */
  public DocumentContent readDocumentContent(Document document) throws IOException {
    final JsonNode tree = objectMapper.readTree(document.asInputStream());
    int schemaVersion =
        tree.hasNonNull("schemaVersion")
            ? tree.get("schemaVersion").asInt()
            : AgentContext.UNVERSIONED_SCHEMA_VERSION;
    if (schemaVersion > AgentContext.CURRENT_SCHEMA_VERSION) {
      throw new IllegalStateException(
          "Persisted conversation schema version %d is newer than the highest version supported by this connector (%d). This state was written by a newer connector version; rolling back to an older connector version is not supported. Upgrade the connector runtime to the version that wrote this state."
              .formatted(schemaVersion, AgentContext.CURRENT_SCHEMA_VERSION));
    }
    while (schemaVersion < AgentContext.CURRENT_SCHEMA_VERSION) {
      schemaVersion = migrate(schemaVersion, tree);
    }
    return objectMapper.treeToValue(tree, DocumentContent.class);
  }

  private int migrate(int fromVersion, JsonNode tree) {
    return switch (fromVersion) {
      case AgentContext.UNVERSIONED_SCHEMA_VERSION -> {
        AgentContextSchemaMigration.ToolCallResultUpcaster.upcastMessages(
            tree.get("messages"), objectMapper);
        yield 1;
      }
      case 1 -> 2;
      default ->
          throw new IllegalStateException(
              "No migration registered from schema version %d".formatted(fromVersion));
    };
  }

  public String writeDocumentContent(DocumentContent content) throws JsonProcessingException {
    return objectWriter.writeValueAsString(content);
  }
}
