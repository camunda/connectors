/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandling;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code c8__return_document} tool. The model calls it once per document to select a
 * document for the final response of the agent under a key the process can find it by. It is
 * offered to every agent instance with the documents feature.
 *
 * <p>A valid call answers with the text {@code Returned the document under the key 'k'.} and asks
 * the agent to select the document for the response documents under its key ({@link
 * RuntimeToolResult.Success#withResponseDocument}); a call with a key that is already used replaces
 * that selection in place. The selections stay in the agent context for the lifetime of the agent
 * instance, and every response without tool calls returns all of them. An invalid key or a document
 * the agent does not know is answered with an error result, which selects nothing.
 */
public final class ReturnDocumentTool implements RuntimeTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = BuiltInToolNames.PREFIX + "return_document";

  /** The parameter carrying the stable identifier the process finds the document by. */
  public static final String PARAMETER_KEY = "key";

  /** The parameter carrying the document to return. */
  public static final String PARAMETER_DOCUMENT = "document";

  /** The parameter carrying an optional description of the document. */
  public static final String PARAMETER_DESCRIPTION = "description";

  private static final ToolDefinition DEFINITION =
      ToolDefinition.runtime(
          NAME,
          "Adds a document from the conversation to your final response. Call it once per"
              + " document. A returned document stays in all later final responses.",
          schema());

  /** The arguments of a call: the key, the document and its description. */
  record Arguments(
      @Nullable String key, @Nullable AgentDocumentId document, @Nullable String description) {}

  private static Map<String, Object> schema() {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put(
        PARAMETER_KEY,
        text(
            "Key of the document in the response. Pattern: "
                + ResponseDocumentSelection.KEY_PATTERN
                + "."));
    properties.put(
        PARAMETER_DOCUMENT, DocumentParameter.documentSchema("Handle of the document to return."));
    properties.put(PARAMETER_DESCRIPTION, text("Optional short description of the document."));
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "object");
    schema.put("properties", properties);
    schema.put("required", List.of(PARAMETER_KEY, PARAMETER_DOCUMENT));
    return schema;
  }

  private static Map<String, Object> text(String description) {
    final var schema = new LinkedHashMap<String, Object>();
    schema.put("type", "string");
    schema.put("description", description);
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
    final var arguments = call.argumentsAs(Arguments.class);
    final var key = arguments.key();
    if (key == null || !ResponseDocumentSelection.isValidKey(key)) {
      return invalidKey();
    }
    final var document = arguments.document();
    if (document == null) {
      return RuntimeToolResult.error(
          "Parameter '%s' is required: pass the handle of a document from the conversation."
              .formatted(PARAMETER_DOCUMENT));
    }

    final var description =
        arguments.description() == null || arguments.description().isBlank()
            ? null
            : arguments.description();
    return RuntimeToolResult.success("Returned the document under the key '%s'.".formatted(key))
        .withResponseDocument(key, document, description);
  }

  private static RuntimeToolResult invalidKey() {
    return RuntimeToolResult.error(
        "Parameter '%s' must match %s."
            .formatted(PARAMETER_KEY, ResponseDocumentSelection.KEY_PATTERN));
  }
}
