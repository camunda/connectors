/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * Represents a self-closing XML tag used to label a document in the synthetic user message, e.g.:
 *
 * <pre>{@code
 * <document tool-name="search" tool-call-id="call_abc" document-short-id="25ece9fa" filename="report.pdf" />
 * }</pre>
 *
 * <p>The tag provides correlation attributes so the model can match the document content block with
 * the document reference in the tool result JSON.
 */
public record DocumentXmlTag(
    @Nullable String toolName,
    @Nullable String toolCallId,
    @Nullable String documentShortId,
    @Nullable String filename) {

  /**
   * Creates a tag from a document and its tool call context. The document short ID is extracted as
   * the first segment of the document's UUID identifier (e.g. "25ece9fa" from
   * "25ece9fa-aeea-423d-98ed-67c1f08b137b").
   */
  public static DocumentXmlTag from(Document document, String toolName, String toolCallId) {
    return new DocumentXmlTag(
        toolName, toolCallId, extractDocumentShortId(document), extractFileName(document));
  }

  /** Creates a tag from a document without tool call context (e.g. for event documents). */
  public static DocumentXmlTag from(Document document) {
    return from(document, null, null);
  }

  /** Serializes this tag to an XML self-closing element string. */
  public String toXml() {
    var sb = new StringBuilder("<document");
    appendAttribute(sb, "tool-name", toolName);
    appendAttribute(sb, "tool-call-id", toolCallId);
    appendAttribute(sb, "document-short-id", documentShortId);
    appendAttribute(sb, "filename", filename);
    sb.append(" />");
    return sb.toString();
  }

  private static void appendAttribute(StringBuilder sb, String name, String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(" %s=\"%s\"".formatted(name, escapeXmlAttribute(value)));
    }
  }

  private static String escapeXmlAttribute(String value) {
    if (value == null) {
      return null;
    }
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  private static String extractDocumentShortId(Document document) {
    if (document.reference() instanceof CamundaDocumentReference camundaRef) {
      var documentId = camundaRef.getDocumentId();
      if (documentId != null) {
        int dashIndex = documentId.indexOf('-');
        return dashIndex > 0 ? documentId.substring(0, dashIndex) : documentId;
      }
    }
    return null;
  }

  private static String extractFileName(Document document) {
    return document.metadata() != null ? document.metadata().getFileName() : null;
  }
}
