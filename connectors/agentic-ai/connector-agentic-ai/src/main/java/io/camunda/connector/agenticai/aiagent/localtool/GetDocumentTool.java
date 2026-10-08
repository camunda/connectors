/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.localtool;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameterSchema;
import io.camunda.connector.agenticai.aiagent.model.tool.InternalToolNames;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolDefinition;
import io.camunda.connector.api.document.Document;
import java.io.IOException;
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
 * is not loaded again. A document above the {@link DocumentLoadLimits limit} of its content type is
 * rejected without being loaded; the size is read from the reference metadata, and when the
 * metadata has none, from at most one more byte than the limit of the document.
 */
public final class GetDocumentTool implements LocalTool {

  /** The tool name, in the reserved namespace of the tools the agent runs itself. */
  public static final String NAME = InternalToolNames.PREFIX + "get_document";

  /** The parameter carrying the id of the document to load. */
  public static final String PARAMETER_ID = "id";

  private static final ToolDefinition DEFINITION =
      new ToolDefinition(
          NAME,
          "Loads a known document so that its content can be read in the conversation. Use it when"
              + " you need the content of a document that you only see as a <doc/> tag. It fails"
              + " for documents that are too large: pass the id of a large document to a tool that"
              + " processes it instead, for example a sandbox. A document that is already in the"
              + " conversation is not loaded again.",
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

    final var limit = DocumentLoadLimits.limitFor(DocumentHandle.contentTypeFor(document));
    final var size = DocumentHandle.sizeFor(document);
    if (size != null) {
      if (size > limit) {
        return tooLarge("is " + kilobytes(size), limit);
      }
    } else if (exceeds(document, limit)) {
      return tooLarge("is larger than " + kilobytes(limit), limit);
    }

    return LocalToolResult.success("Loaded document %s.".formatted(id))
        .withProperty(ToolCallResult.PROPERTY_LOADED_DOCUMENT_ID, id);
  }

  private static LocalToolResult tooLarge(String sizeText, long limit) {
    return LocalToolResult.error(
        "The document %s, too large to load into the conversation (limit %s); pass its id to a tool that processes it."
            .formatted(sizeText, kilobytes(limit)));
  }

  /** Reads at most one byte more than the limit to find out whether the document exceeds it. */
  private static boolean exceeds(Document document, long limit) {
    try (final var in = document.asInputStream()) {
      return in.readNBytes((int) limit + 1).length > limit;
    } catch (IOException | RuntimeException e) {
      throw new LocalToolException(
          "The document could not be read to check its size: " + e.getMessage(), e);
    }
  }

  private static String kilobytes(long bytes) {
    return ((bytes + 1023) / 1024) + " KB";
  }
}
