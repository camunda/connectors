/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.api.document.Document;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * Renders the tag the model reads for a document, a self-closing XML tag, e.g.:
 *
 * <pre>{@code
 * <doc id="doc_3f9a1c0b27de" fileName="report.pdf" contentType="application/pdf" size="48213" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>The attributes are written in the order {@code id}, {@code fileName}, {@code contentType},
 * {@code size}, {@code source}, {@code toolName}, {@code toolCallId}; a null or blank value is
 * omitted and every value is XML-escaped. The tag comes in two variants:
 *
 * <ul>
 *   <li>the long tag, with the origin of the document: {@code source} is {@code user_message} or
 *       {@code event} for a document of a user message or event, {@code toolName} and {@code
 *       toolCallId} are those of the tool call that returned a document of a tool call
 *   <li>the short tag, without origin attributes, for the document of a tool result and for a
 *       document nested in the object of a tool result
 * </ul>
 *
 * <p>The file name, content type and size are read from the reference of the document, never by
 * reading the document. A document the agent does not know is rendered as a tag with the id only.
 */
public final class DocumentTags {

  private DocumentTags() {}

  /** The long tag of the document with the given id, read from the known documents. */
  public static String longTag(AgentDocuments documents, AgentDocumentId id) {
    return documents
        .find(id)
        .map(document -> render(id, document.descriptors(), document.origin()))
        .orElseGet(() -> idOnlyTag(id));
  }

  /** The short tag of the document with the given id, read from the known documents. */
  public static String shortTag(AgentDocuments documents, AgentDocumentId id) {
    return documents
        .find(id)
        .map(document -> render(id, document.descriptors(), null))
        .orElseGet(() -> idOnlyTag(id));
  }

  /** The short tag of the given document. */
  public static String shortTag(Document document) {
    return render(
        AgentDocumentId.of(document), AgentDocument.Descriptors.of(document.reference()), null);
  }

  /** The tag of a document the agent does not know: the id only. */
  public static String idOnlyTag(AgentDocumentId id) {
    return render(id, null, null);
  }

  private static String render(
      AgentDocumentId id,
      AgentDocument.@Nullable Descriptors descriptors,
      @Nullable DocumentOrigin origin) {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id.value());
    if (descriptors != null) {
      appendAttribute(sb, "fileName", descriptors.fileName());
      appendAttribute(sb, "contentType", descriptors.contentType());
      final var size = descriptors.size();
      appendAttribute(sb, "size", size == null ? null : size.toString());
    }
    switch (origin) {
      case DocumentOrigin.UserMessage ignored -> appendAttribute(sb, "source", "user_message");
      case DocumentOrigin.Event ignored -> appendAttribute(sb, "source", "event");
      case DocumentOrigin.ToolCall toolCall -> {
        appendAttribute(sb, "toolName", toolCall.toolName());
        appendAttribute(sb, "toolCallId", toolCall.toolCallId());
      }
      case null -> {}
    }
    return sb.append(" />").toString();
  }

  private static void appendAttribute(StringBuilder sb, String name, @Nullable String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(" %s=\"%s\"".formatted(name, StringEscapeUtils.escapeXml10(value)));
    }
  }
}
