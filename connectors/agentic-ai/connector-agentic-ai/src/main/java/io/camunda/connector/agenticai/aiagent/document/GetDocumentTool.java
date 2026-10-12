/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.BuiltInToolNames;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTool;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolContext;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolException;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolResult;
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
 * <p>A successful call answers {@code Loaded the document <doc/ tag>. Its content follows in the
 * next message.} with the tag of the document, as the model saw it before, and marks its result
 * with the id of the document ({@link ToolCallResult#PROPERTY_DOCUMENT_ID_WITH_CONTENT}); the
 * content reaches the model in the message that follows the tool results. A document that is
 * already part of the conversation is not added again. A document that does not pass the {@link
 * DocumentContentGuard} (a content type outside the allowlist, a size above the limit of its type)
 * is rejected without its content being read.
 */
public final class GetDocumentTool implements RuntimeTool {

  /** The tool name, with the prefix of the built-in tools. */
  public static final String NAME = BuiltInToolNames.PREFIX + "get_document";

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

  /** The arguments of a call: the id of the document to load. */
  record Arguments(@Nullable String id) {}

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
  public RuntimeToolResult call(RuntimeToolCall call, RuntimeToolContext context) {
    final var id = call.argumentsAs(Arguments.class, context.objectMapper()).id();
    final var entry = id == null ? null : context.documents().findById(id).orElse(null);
    if (entry == null) {
      return RuntimeToolResult.error(
          "Parameter '%s' must be the id of a known document. %s"
              .formatted(PARAMETER_ID, context.documents().knownDocumentsText()));
    }

    if (context.documentIdsWithContent().contains(entry.id())) {
      return RuntimeToolResult.success(
          "Document %s is already in the conversation.".formatted(entry.id()));
    }

    final var document = documentOf(entry, context);

    final var rejection = DocumentContentGuard.check(document).orElse(null);
    if (rejection != null) {
      if (rejection.retryable()) {
        final var message = "The document " + rejection.problem();
        throw rejection.cause() == null
            ? new RuntimeToolException(message)
            : new RuntimeToolException(message, rejection.cause());
      }
      return RuntimeToolResult.error(
          "The document %s; pass its id to a tool that processes it."
              .formatted(rejection.problem()));
    }

    return RuntimeToolResult.success(
            "Loaded the document %s. Its content follows in the next message."
                .formatted(DocumentReferenceContent.from(entry).toTag()))
        .withProperty(ToolCallResult.PROPERTY_DOCUMENT_ID_WITH_CONTENT, entry.id());
  }

  /** The document of a registry entry, read from its reference. */
  private static Document documentOf(DocumentRegistryEntry entry, RuntimeToolContext context) {
    try {
      return context.objectMapper().convertValue(entry.reference(), Document.class);
    } catch (IllegalArgumentException e) {
      throw new RuntimeToolException(
          "The document %s could not be read from its reference: %s"
              .formatted(entry.id(), e.getMessage()),
          e);
    }
  }
}
