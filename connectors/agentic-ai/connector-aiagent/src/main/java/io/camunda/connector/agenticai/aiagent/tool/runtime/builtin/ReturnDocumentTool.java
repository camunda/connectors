/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
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
          "Adds one document to your final response, so that the process receives it. Use it only"
              + " for a document that you see as a handle in the conversation: a JSON object with"
              + " \"type\": \"camunda.agenticai.document\" and an \"id\" that starts with"
              + " \"doc_\". This tool does not return text or values; put them in your final"
              + " response. Do not call it when the conversation has no such handle, and do not use"
              + " it for a document that you only pass to other tools. Call it once per document."
              + " The key is a stable identifier the process uses to find the document; it starts"
              + " with a letter or an underscore and continues with letters, digits, underscores or"
              + " hyphens. The description is optional. Calling it again with the same key replaces"
              + " the document returned under that key. Documents you returned earlier stay"
              + " attached to your later final responses too, so you do not need to return them"
              + " again.",
          schema());

  /** The arguments of a call: the key, the document and its description. */
  record Arguments(
      @Nullable String key, @Nullable AgentDocumentId document, @Nullable String description) {}

  private static Map<String, Object> schema() {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put(
        PARAMETER_KEY,
        text(
            "A stable identifier the process uses to find the document, matching "
                + ResponseDocumentSelection.KEY_PATTERN
                + "."));
    properties.put(
        PARAMETER_DOCUMENT, DocumentSchemas.document("The handle of the document to return."));
    properties.put(PARAMETER_DESCRIPTION, text("An optional short description of the document."));
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

  /** The tool works with the agent documents: only an agent with the documents feature has it. */
  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return context.agentContext().supports(AgentFeatures.DOCUMENTS);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolContext context, RuntimeToolCall call) {
    final var arguments = call.argumentsAs(Arguments.class);
    final var key = arguments.key();
    if (key == null) {
      return invalidKey();
    }
    final var document = arguments.document();
    if (document == null || context.documents().find(document).isEmpty()) {
      return RuntimeToolResult.error(
          ("Parameter '%s' must be the handle of a document from the conversation: a JSON object"
                  + " such as {\"type\":\"%s\",\"id\":\"doc_…\"}.")
              .formatted(PARAMETER_DOCUMENT, AgentDocumentHandle.TYPE));
    }

    final var description =
        arguments.description() == null || arguments.description().isBlank()
            ? null
            : arguments.description();
    try {
      return RuntimeToolResult.success("Returned the document under the key '%s'.".formatted(key))
          .withResponseDocument(key, document, description);
    } catch (IllegalArgumentException e) {
      return invalidKey();
    }
  }

  private static RuntimeToolResult invalidKey() {
    return RuntimeToolResult.error(
        "Parameter '%s' must match %s: it starts with a letter or an"
                .formatted(PARAMETER_KEY, ResponseDocumentSelection.KEY_PATTERN)
            + " underscore and continues with letters, digits, underscores or hyphens.");
  }
}
