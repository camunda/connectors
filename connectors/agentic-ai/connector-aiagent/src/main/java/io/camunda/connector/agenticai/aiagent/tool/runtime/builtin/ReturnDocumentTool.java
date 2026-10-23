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
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolSchemas;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
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

  private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_-]*");

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Attaches one document to your final response. Use this only for a document the user"
              + " should receive in your answer; do not use it for documents you only pass to"
              + " other tools. Call it once per document. The key is a stable identifier the"
              + " process uses to find the document; it starts with a letter or an underscore and"
              + " continues with letters, digits, underscores or hyphens. The description is"
              + " optional. Calling it again with the same key replaces the document returned"
              + " under that key. Documents you returned earlier stay attached to your later final"
              + " responses too, so you do not need to return them again.",
          schema());

  /** The arguments of a call: the key, the document and its description. */
  record Arguments(
      @Nullable String key, @Nullable DocumentId document, @Nullable String description) {}

  private static Map<String, Object> schema() {
    final var properties = new LinkedHashMap<String, Object>();
    properties.put(
        PARAMETER_KEY,
        text(
            "A stable identifier the process uses to find the document, matching"
                + " [A-Za-z_][A-Za-z0-9_-]*."));
    properties.put(PARAMETER_DOCUMENT, RuntimeToolSchemas.document("The document to return."));
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

  /** The tool works with the document registry: only an agent with the documents feature has it. */
  @Override
  public boolean isEnabled(RuntimeToolContext context) {
    return context.agentContext().supports(AgentFeatures.DOCUMENTS);
  }

  @Override
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    final var arguments = call.argumentsAs(Arguments.class);
    final var key = arguments.key();
    if (key == null || !KEY_PATTERN.matcher(key).matches()) {
      return RuntimeToolResult.error(
          "Parameter '%s' must match [A-Za-z_][A-Za-z0-9_-]*: it starts with a letter or an"
                  .formatted(PARAMETER_KEY)
              + " underscore and continues with letters, digits, underscores or hyphens.");
    }
    final var document = arguments.document();
    if (document == null || context.documents().find(document).isEmpty()) {
      return RuntimeToolResult.error(
          "Parameter '%s' must be the tag of a known document.".formatted(PARAMETER_DOCUMENT));
    }

    final var description =
        arguments.description() == null || arguments.description().isBlank()
            ? null
            : arguments.description();
    return RuntimeToolResult.success("Returned the document under the key '%s'.".formatted(key))
        .withResponseDocument(key, document, description);
  }
}
