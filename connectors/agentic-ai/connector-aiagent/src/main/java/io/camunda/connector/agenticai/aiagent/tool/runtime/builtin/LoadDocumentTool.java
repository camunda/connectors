/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_PROPERTIES;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_REQUIRED;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.PROPERTY_TYPE;
import static io.camunda.connector.agenticai.common.jsonschema.JsonSchemaConstants.TYPE_OBJECT;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code c8__load_document} tool. The model calls it to read the content of a known
 * document, which it otherwise sees as a handle only. It is offered to every agent instance with
 * the documents feature.
 *
 * <p>A call for a known document returns no text of its own: it asks the agent to add the content
 * of the document ({@link RuntimeToolResult.Success#withDocumentContent}), and the agent writes
 * into the tool result what happens to the content: it follows in the next message, or it is in the
 * conversation already. The agent decides, the tool does not.
 */
public final class LoadDocumentTool implements RuntimeTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = BuiltInToolNames.PREFIX + "load_document";

  /** The parameter carrying the document to load. */
  public static final String PARAMETER_ID = "id";

  private static final ToolDefinition DEFINITION =
      ToolDefinition.runtime(
          NAME,
          "Loads the content of a document that you see as a handle in the conversation: a JSON"
              + " object with \"type\": \"camunda.agenticai.document\" and an \"id\" that starts"
              + " with \"doc_\". Do not call it when the conversation has no such handle. A document that"
              + " is already in the conversation is not loaded again.",
          schema());

  /** The arguments of a call: the document to load. */
  record Arguments(@Nullable AgentDocumentId id) {}

  private static Map<String, Object> schema() {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put(PROPERTY_TYPE, TYPE_OBJECT);
    schema.put(
        PROPERTY_PROPERTIES,
        Map.of(
            PARAMETER_ID, DocumentParameter.documentSchema("The handle of the document to load.")));
    schema.put(PROPERTY_REQUIRED, List.of(PARAMETER_ID));
    return schema;
  }

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  /** The tool works with the agent documents: only an agent that tracks its documents has it. */
  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return switch (context.agentContext().documentHandling()) {
      case DocumentHandling.Tracked ignored -> true;
      case DocumentHandling.Untracked ignored -> false;
    };
  }

  @Override
  public RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call) {
    final var documents = context.documents();
    final var id = call.argumentsAs(Arguments.class).id();
    final var document = id == null ? null : documents.find(id).orElse(null);
    if (document == null) {
      return RuntimeToolResult.error(
          ("Parameter '%s' must be the handle of a document from the conversation: a JSON object"
                  + " such as {\"type\":\"%s\",\"id\":\"doc_…\"}.")
              .formatted(PARAMETER_ID, AgentDocumentHandle.TYPE));
    }

    return RuntimeToolResult.success(List.of()).withDocumentContent(document.id());
  }
}
