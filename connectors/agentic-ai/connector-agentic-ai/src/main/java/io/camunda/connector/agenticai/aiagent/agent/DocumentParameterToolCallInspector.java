/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.agent;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.message.DocumentReferenceXmlTag;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import java.util.stream.Collectors;

/**
 * Validates the document parameters of ad-hoc tool calls. A call whose document parameter is not
 * the id of a known document is answered with an error naming the parameter and listing the known
 * documents, so the model can correct the id; the tool does not run. Tools without document
 * parameters, including gateway tools (their definitions never declare any), pass.
 */
public class DocumentParameterToolCallInspector implements ToolCallInspector {

  static final int MAX_LISTED_DOCUMENTS = 50;

  @Override
  public ToolCallVerdict inspect(ToolCall toolCall, LocalToolCallContext context) {
    final var definition =
        context.toolDefinitions().stream()
            .filter(candidate -> candidate.name().equals(toolCall.name()))
            .filter(candidate -> !candidate.documentParameters().isEmpty())
            .findFirst();
    if (definition.isEmpty()) {
      return new ToolCallVerdict.Pass();
    }

    return switch (DocumentParameterResolver.resolve(
        toolCall, definition.get(), context.documents())) {
      case DocumentParameterResolver.Resolution.Resolved ignored -> new ToolCallVerdict.Pass();
      case DocumentParameterResolver.Resolution.Invalid invalid ->
          new ToolCallVerdict.Answer(
              String.join(" ", invalid.problems()) + " " + knownDocuments(context.documents()));
    };
  }

  static String knownDocuments(DocumentRegistry registry) {
    if (registry.isEmpty()) {
      return "No documents are known.";
    }
    final var entries = registry.entries();
    final var listed =
        entries.stream()
            .limit(MAX_LISTED_DOCUMENTS)
            .map(entry -> DocumentReferenceXmlTag.from(entry).toXml())
            .collect(Collectors.joining("\n"));
    final var omitted = entries.size() - MAX_LISTED_DOCUMENTS;
    return "Known documents:\n"
        + listed
        + (omitted > 0 ? "\n(and %d more)".formatted(omitted) : "");
  }
}
