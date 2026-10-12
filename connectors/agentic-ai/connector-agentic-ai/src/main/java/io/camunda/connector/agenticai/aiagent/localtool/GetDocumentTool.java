/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.InternalToolNames;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The built-in {@code c8__get_document} tool. The model calls it to read the content of a known
 * document, which it otherwise sees as a {@code <doc/>} tag only. It is offered to every agent
 * instance with the documents feature.
 *
 * <p>A successful call answers {@code Loaded document <id>.} and marks its result with the id of
 * the document ({@link ToolCallResult#PROPERTY_LOADED_DOCUMENT_ID}); the content reaches the model
 * in the message that follows the tool results. A document that is already part of the conversation
 * is not loaded again. A document that does not pass the {@link DocumentLoadGuard} (a content type
 * outside the allowlist, a size above the limit of its type) is rejected without being loaded.
 */
public final class GetDocumentTool implements LocalTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = InternalToolNames.PREFIX + "get_document";

  /** The parameter carrying the id of the document to load. */
  public static final String PARAMETER_ID = "id";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Loads a known document so that its content can be read in the conversation. Use it when"
              + " you need the content of a document that you only see as a <doc/> tag. It fails"
              + " for documents that are too large: pass the id of a large document to a tool that"
              + " processes it instead, for example a sandbox. Only text, PDF and image documents can"
              + " be loaded. A document that is already in the conversation is not loaded again.",
          ordered(
              "type",
              "object",
              "properties",
              ordered(
                  PARAMETER_ID, DocumentParameterSchema.documentSchema("The document to load.")),
              "required",
              List.of(PARAMETER_ID)));

  // An insertion-ordered map built through the canonical constructor of the record, see
  // ReturnDocumentTool
  private static Map<String, Object> ordered(Object... keysAndValues) {
    final var map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      map.put((String) keysAndValues[i], keysAndValues[i + 1]);
    }
    return Collections.unmodifiableMap(map);
  }

  /** The arguments of a call: the document is resolved from the id the model sent. */
  record Arguments(@Nullable Document id) {}

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
    final var document = call.argumentsAs(Arguments.class, context.objectMapper()).id();
    if (document == null) {
      return LocalToolResult.error(
          "Parameter '%s' must be the id of a known document. %s"
              .formatted(PARAMETER_ID, context.documents().knownDocumentsText()));
    }

    // the framework resolved the argument to a known document
    final var id = DocumentHandle.idFor(document);
    if (context.loadedDocumentIds().contains(id)) {
      return LocalToolResult.success("Document %s is already in the conversation.".formatted(id));
    }

    final var rejection = DocumentLoadGuard.check(document).orElse(null);
    if (rejection != null) {
      if (rejection.retryable()) {
        final var message = "The document " + rejection.problem();
        throw rejection.cause() == null
            ? new LocalToolException(message)
            : new LocalToolException(message, rejection.cause());
      }
      return LocalToolResult.error(
          "The document %s; pass its id to a tool that processes it."
              .formatted(rejection.problem()));
    }

    return LocalToolResult.success("Loaded document %s.".formatted(id))
        .withProperty(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID, id);
  }
}
