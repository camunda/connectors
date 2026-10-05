/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code return_documents} tool. The model calls it to select documents for the final
 * response of the agent. It is available while the response configuration opts in to returning
 * documents.
 *
 * <p>Every valid call adds its documents to the selection of the agent context (ordered union, no
 * duplicates); a response without tool calls returns the selection and clears it.
 */
public final class ReturnDocumentsTool implements LocalTool {

  /** The reserved tool name; ad-hoc tool elements must not use it. */
  public static final String NAME = "return_documents";

  /** The array parameter carrying the ids of the {@code <doc/>} tags to return. */
  public static final String PARAMETER_IDS = "ids";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Attaches documents to your final response. Call this before you give your final"
              + " answer for every document the response must contain. Pass the ids of the"
              + " <doc/> tags of the documents; the ids accumulate over several calls.",
          ordered(
              "type",
              "object",
              "properties",
              Map.of(
                  PARAMETER_IDS,
                  ordered(
                      "type",
                      "array",
                      "description",
                      "The ids of the <doc/> tags of the documents to return.",
                      "items",
                      Map.of("type", "string"))),
              "required",
              List.of(PARAMETER_IDS)),
          List.of("/properties/" + PARAMETER_IDS + "/items"));

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

  /** The arguments of a call: the selected documents, resolved from the ids the model sent. */
  record Arguments(@Nullable List<Document> ids) {}

  @Override
  public ToolDefinition definition() {
    return DEFINITION;
  }

  @Override
  public boolean isAvailable(AgentConfiguration configuration) {
    return configuration.returnsDocuments();
  }

  @Override
  public LocalToolResult call(LocalToolCall call, LocalToolContext context) {
    final var arguments = call.argumentsAs(Arguments.class, context.objectMapper());
    if (arguments.ids() == null || arguments.ids().isEmpty()) {
      return LocalToolResult.error(
          "Parameter '%s' must be a non-empty array of document ids. %s"
              .formatted(PARAMETER_IDS, context.documents().knownDocumentsText()));
    }

    // the framework resolved every element to a known document
    final var selected = new LinkedHashSet<String>();
    arguments.ids().forEach(document -> selected.add(DocumentHandle.idFor(document)));
    return LocalToolResult.success(
            "Selected %d document(s) to return: %s"
                .formatted(selected.size(), String.join(", ", selected)))
        .withContextUpdate(agentContext -> select(agentContext, selected));
  }

  private static AgentContext select(AgentContext agentContext, LinkedHashSet<String> selected) {
    final var merged = new LinkedHashSet<>(agentContext.returnDocumentIds());
    merged.addAll(selected);
    return merged.size() == agentContext.returnDocumentIds().size()
        ? agentContext
        : agentContext.withReturnDocumentIds(List.copyOf(merged));
  }
}
