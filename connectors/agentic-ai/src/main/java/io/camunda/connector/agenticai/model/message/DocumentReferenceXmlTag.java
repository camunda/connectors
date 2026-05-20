/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.message;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.springframework.lang.Nullable;

/**
 * Represents a self-closing XML tag used to label a document in the synthetic user message, e.g.:
 *
 * <pre>{@code
 * <doc toolName="search" toolCallId="call_abc" documentId="25ece9fa-aeea-..." storeId="in-memory" contentType="application/pdf" fileName="report.pdf" />
 * }</pre>
 *
 * <p>Attribute names mirror the JSON field names emitted by the standard {@code DocumentSerializer}
 * so the model can correlate references in the tool result JSON with the actual document content
 * blocks 1:1 without inferring partial-id matches. Blank/null attributes are omitted.
 *
 * <p>Only {@link CamundaDocumentReferenceXmlTag} carries {@code contentType} and {@code fileName}:
 * Camunda references read their metadata in-memory from the document store on resolve, so surfacing
 * those fields here is free. External documents would have to HTTP-fetch the response headers to
 * learn their content type or filename — the tag deliberately omits the metadata block for those to
 * avoid a download just to render a label.
 */
public sealed interface DocumentReferenceXmlTag {

  @Nullable
  String toolCallId();

  @Nullable
  String toolName();

  String toXml();

  /**
   * Creates a tag from a document and its tool call context. Dispatches on the {@link Document}'s
   * reference type:
   *
   * <ul>
   *   <li>{@link CamundaDocumentReference} → {@link CamundaDocumentReferenceXmlTag}
   *   <li>{@link ExternalDocumentReference} → {@link ExternalDocumentReferenceXmlTag}
   *   <li>any other reference type (including inline) → {@link GenericDocumentReferenceXmlTag}
   * </ul>
   */
  static DocumentReferenceXmlTag from(
      Document document, @Nullable String toolCallId, @Nullable String toolName) {
    return switch (document.reference()) {
      case CamundaDocumentReference ref -> {
        // Camunda references carry metadata in-memory, so reading it back here is free.
        final DocumentMetadata md = document.metadata();
        yield new CamundaDocumentReferenceXmlTag(
            toolCallId,
            toolName,
            ref.getDocumentId(),
            ref.getStoreId(),
            md != null ? md.getContentType() : null,
            md != null ? md.getFileName() : null);
      }
      case ExternalDocumentReference ref ->
          new ExternalDocumentReferenceXmlTag(toolCallId, toolName, ref.url(), ref.name());
      case null, default -> new GenericDocumentReferenceXmlTag(toolCallId, toolName);
    };
  }

  /** Creates a tag from a document without tool call context (e.g. for event documents). */
  static DocumentReferenceXmlTag from(Document document) {
    return from(document, null, null);
  }

  record CamundaDocumentReferenceXmlTag(
      @Nullable String toolCallId,
      @Nullable String toolName,
      String documentId,
      @Nullable String storeId,
      @Nullable String contentType,
      @Nullable String fileName)
      implements DocumentReferenceXmlTag {

    @Override
    public String toXml() {
      var attributes = new LinkedHashMap<String, String>();
      attributes.put("toolName", toolName);
      attributes.put("toolCallId", toolCallId);
      attributes.put("documentId", documentId);
      attributes.put("storeId", storeId);
      attributes.put("contentType", contentType);
      attributes.put("fileName", fileName);
      return renderTag(attributes);
    }
  }

  record ExternalDocumentReferenceXmlTag(
      @Nullable String toolCallId, @Nullable String toolName, String url, @Nullable String name)
      implements DocumentReferenceXmlTag {

    @Override
    public String toXml() {
      var attributes = new LinkedHashMap<String, String>();
      attributes.put("toolName", toolName);
      attributes.put("toolCallId", toolCallId);
      attributes.put("url", url);
      attributes.put("name", name);
      return renderTag(attributes);
    }
  }

  record GenericDocumentReferenceXmlTag(@Nullable String toolCallId, @Nullable String toolName)
      implements DocumentReferenceXmlTag {

    @Override
    public String toXml() {
      var attributes = new LinkedHashMap<String, String>();
      attributes.put("toolName", toolName);
      attributes.put("toolCallId", toolCallId);
      return renderTag(attributes);
    }
  }

  private static String renderTag(Map<String, String> attributes) {
    var sb = new StringBuilder("<doc");
    attributes.forEach((name, value) -> appendAttribute(sb, name, value));
    sb.append(" />");
    return sb.toString();
  }

  private static void appendAttribute(StringBuilder sb, String name, String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(" %s=\"%s\"".formatted(name, StringEscapeUtils.escapeXml10(value)));
    }
  }
}
