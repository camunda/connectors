/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.SelectedDocument;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.InternalToolNames;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code c8__return_document} tool. The model calls it once per document to select a
 * document for the final response of the agent under a key the process can find it by. It is
 * offered to every agent.
 *
 * <p>Every valid call puts its document into the selection of the agent context under its key; a
 * call with a key that is already selected replaces that entry in place. The selection stays in the
 * agent context for the lifetime of the agent instance, and every response without tool calls
 * returns all of it.
 */
public final class ReturnDocumentTool implements LocalTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = InternalToolNames.PREFIX + "return_document";

  /** The parameter carrying the stable identifier the process finds the document by. */
  public static final String PARAMETER_KEY = "key";

  /** The parameter carrying the id of the document to return. */
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
          ordered(
              "type",
              "object",
              "properties",
              ordered(
                  PARAMETER_KEY,
                  ordered(
                      "type",
                      "string",
                      "description",
                      "A stable identifier the process uses to find the document, matching"
                          + " [A-Za-z_][A-Za-z0-9_-]*."),
                  PARAMETER_DOCUMENT,
                  DocumentParameterSchema.documentSchema("The document to return."),
                  PARAMETER_DESCRIPTION,
                  ordered(
                      "type",
                      "string",
                      "description",
                      "An optional short description of the document.")),
              "required",
              List.of(PARAMETER_KEY, PARAMETER_DOCUMENT)));

  // An insertion-ordered map built through the canonical constructor of the record: the definition
  // is part of the configuration fingerprint and of the request sent to the model, which need the
  // same bytes in every JVM (the record builder copies maps into an immutable map of salted order).
  private static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return Collections.unmodifiableMap(map);
  }

  /** The arguments of a call: the key, the id of the document and its description. */
  record Arguments(@Nullable String key, @Nullable String document, @Nullable String description) {}

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  /** The tool works with the document registry: only an agent with the documents feature has it. */
  @Override
  public boolean isAvailable(AgentConfiguration configuration) {
    return configuration.supportsDocuments();
  }

  @Override
  public LocalToolResult call(LocalToolCall call, LocalToolContext context) {
    final var arguments = call.argumentsAs(Arguments.class, context.objectMapper());
    if (arguments.key() == null || !KEY_PATTERN.matcher(arguments.key()).matches()) {
      return LocalToolResult.error(
          "Parameter '%s' must match [A-Za-z_][A-Za-z0-9_-]*: it starts with a letter or an"
                  .formatted(PARAMETER_KEY)
              + " underscore and continues with letters, digits, underscores or hyphens.");
    }
    if (arguments.document() == null
        || context.documents().findById(arguments.document()).isEmpty()) {
      return LocalToolResult.error(
          "Parameter '%s' must be the id of a known document. %s"
              .formatted(PARAMETER_DOCUMENT, context.documents().knownDocumentsText()));
    }

    final var key = arguments.key();
    final var selected =
        new SelectedDocument(
            arguments.document(),
            StringUtils.isBlank(arguments.description()) ? null : arguments.description());
    return LocalToolResult.success("Returned the document under the key '%s'.".formatted(key))
        .withContextUpdate(agentContext -> select(agentContext, key, selected));
  }

  private static AgentContext select(
      AgentContext agentContext, String key, SelectedDocument selected) {
    if (selected.equals(agentContext.returnDocuments().get(key))) {
      return agentContext;
    }
    // put keeps the position of a key that is already selected
    final var updated = new LinkedHashMap<>(agentContext.returnDocuments());
    updated.put(key, selected);
    return agentContext.withReturnDocuments(updated);
  }
}
