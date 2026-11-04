/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentWorkspace;
import io.camunda.connector.agenticai.aiagent.model.document.WorkspaceDocument;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * <p>The file name, content type and size are read from the stored reference of the document in the
 * {@link DocumentWorkspace}, never by reading the document. A file name that equals the Camunda
 * document id of the reference is left out: it is what a document stored without a name gets, and
 * would only repeat an id. A document the workspace does not know is rendered as a tag with the id
 * only.
 */
public final class DocumentTags {

  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTags.class);

  private DocumentTags() {}

  /** The long tag of the document with the given id, read from the workspace. */
  public static String longTag(DocumentWorkspace workspace, DocumentId id) {
    return workspace
        .findById(id)
        .map(entry -> render(id, descriptorsOf(entry), entry.origin()))
        .orElseGet(() -> render(id, null, null));
  }

  /** The short tag of the document with the given id, read from the workspace. */
  public static String shortTag(DocumentWorkspace workspace, DocumentId id) {
    return workspace
        .findById(id)
        .map(entry -> render(id, descriptorsOf(entry), null))
        .orElseGet(() -> render(id, null, null));
  }

  /** The short tag of the given document. */
  public static String shortTag(Document document) {
    final var reference = document.reference();
    return render(
        DocumentHandle.idFor(document),
        new Descriptors(
            fileName(reference, DocumentHandle.fileNameFor(document)),
            DocumentHandle.contentTypeFor(document),
            DocumentHandle.sizeFor(document)),
        null);
  }

  /** The tag of a document the workspace has no entry for: the id only. */
  public static String idOnlyTag(DocumentId id) {
    return render(id, null, null);
  }

  private static @Nullable Descriptors descriptorsOf(WorkspaceDocument entry) {
    try {
      final var reference = DocumentIngestion.referenceOf(entry);
      return new Descriptors(
          fileName(reference, DocumentHandle.fileNameFor(reference)),
          DocumentHandle.contentTypeFor(reference),
          DocumentHandle.sizeFor(reference));
    } catch (RuntimeException e) {
      LOGGER.warn(
          "The reference of document {} cannot be read, rendering its tag without descriptors",
          entry.id(),
          e);
      return null;
    }
  }

  private static @Nullable String fileName(
      @Nullable DocumentReference reference, @Nullable String fileName) {
    return reference instanceof CamundaDocumentReference camunda
            && fileName != null
            && fileName.equals(camunda.getDocumentId())
        ? null
        : fileName;
  }

  private static String render(
      DocumentId id, @Nullable Descriptors descriptors, @Nullable DocumentOrigin origin) {
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

  private record Descriptors(
      @Nullable String fileName, @Nullable String contentType, @Nullable Long size) {}
}
