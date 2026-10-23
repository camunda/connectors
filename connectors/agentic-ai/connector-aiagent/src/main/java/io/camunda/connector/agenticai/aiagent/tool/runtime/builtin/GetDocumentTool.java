/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolSchemas;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code c8__get_document} tool. The model calls it to read the content of a known
 * document, which it otherwise sees as a {@code <doc/>} tag only. It is offered to every agent
 * instance with the documents feature.
 *
 * <p>A successful call answers with the text {@code Loaded the document.}, the document as a
 * reference block, and the text {@code Its content follows in the next message.}, and asks the
 * agent to add the content of the document ({@link RuntimeToolResult.Success#withDocumentContent});
 * the content reaches the model in the message that follows the tool results. A document the model
 * reads the content of already is not asked for again. The agent rejects a document that is not
 * allowed as content, for example because of its content type or its size, or because it is an
 * external document, and answers the call with an error.
 */
public final class GetDocumentTool implements RuntimeTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = BuiltInToolNames.PREFIX + "get_document";

  /** The parameter carrying the document to load. */
  public static final String PARAMETER_ID = "id";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Loads a known document so that its content can be read in the conversation. Use it when"
              + " you need the content of a document that you only see as a <doc/> tag. It fails"
              + " for documents that are too large and for documents that are external links: pass"
              + " the tag of such a document to a tool that processes it instead, for example a"
              + " sandbox. Only text, PDF and image documents can be loaded. A document that is"
              + " already in the conversation is not loaded again.",
          schema());

  /** The arguments of a call: the document to load. */
  record Arguments(@Nullable DocumentId id) {}

  private static Map<String, Object> schema() {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put(PARAMETER_ID, RuntimeToolSchemas.document("The document to load."));
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of(PARAMETER_ID));
    return schema;
  }

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  /** The tool works with the document registry: only an agent with the documents feature has it. */
  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return context.agentContext().supports(AgentFeatures.DOCUMENTS);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    final var id = call.argumentsAs(Arguments.class).id();
    final var document = id == null ? null : context.documents().find(id).orElse(null);
    if (document == null) {
      return RuntimeToolResult.error(
          "Parameter '%s' must be the tag of a known document.".formatted(PARAMETER_ID));
    }

    if (document.contentInConversation()) {
      return RuntimeToolResult.success(
          "Document %s is already in the conversation.".formatted(document.id()));
    }

    return RuntimeToolResult.success(
            List.of(
                textContent("Loaded the document."),
                context.documents().reference(document.id()),
                textContent("Its content follows in the next message.")))
        .withDocumentContent(document.id());
  }
}
