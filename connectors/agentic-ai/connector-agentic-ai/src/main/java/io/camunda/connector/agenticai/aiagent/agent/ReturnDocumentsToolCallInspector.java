/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.document.ReturnDocumentsTool;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Answers the calls of the built-in {@link ReturnDocumentsTool} while the response configuration
 * offers it. A call whose {@code ids} are all known documents is answered with the selection, which
 * the loop records for the final response; an invalid call (no ids, or an unknown id) is answered
 * with an error listing the known documents. All other calls pass.
 */
public class ReturnDocumentsToolCallInspector implements ToolCallInspector {

  @Override
  public ToolCallVerdict inspect(ToolCall toolCall, LocalToolCallContext context) {
    if (!context.returnDocumentsEnabled() || !ReturnDocumentsTool.NAME.equals(toolCall.name())) {
      return new ToolCallVerdict.Pass();
    }

    final var arguments = toolCall.arguments();
    final var ids = arguments == null ? null : arguments.get(ReturnDocumentsTool.PARAMETER_IDS);
    if (ids == null || (ids instanceof List<?> list && list.isEmpty())) {
      return new ToolCallVerdict.Answer(
          "Parameter '%s' must be a non-empty array of document ids. %s"
              .formatted(
                  ReturnDocumentsTool.PARAMETER_IDS,
                  DocumentParameterToolCallInspector.knownDocuments(context.documents())));
    }

    final var resolution =
        DocumentParameterResolver.resolve(
            toolCall, ReturnDocumentsTool.definition(), context.documents());
    if (resolution instanceof DocumentParameterResolver.Resolution.Invalid invalid) {
      return new ToolCallVerdict.Answer(
          String.join(" ", invalid.problems())
              + " "
              + DocumentParameterToolCallInspector.knownDocuments(context.documents()));
    }

    // resolved without problems: every element is the id of a known document
    final var selected = new LinkedHashSet<String>();
    ((List<?>) ids).forEach(id -> selected.add((String) id));
    return new ToolCallVerdict.Answer(
        "Selected %d document(s) to return: %s"
            .formatted(selected.size(), String.join(", ", selected)),
        List.copyOf(selected));
  }
}
