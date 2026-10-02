/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.api.document.Document;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * The self-closing XML tag that is the only way the model sees a document, e.g.:
 *
 * <pre>{@code
 * <doc id="25ece9fa-aeea-..." fileName="report.pdf" contentType="application/pdf" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>{@code id} is the {@link DocumentHandle#idFor stable document id}. The tag never carries a
 * store id, content hash, other metadata or URL (an external URL may be a presigned one). Null or
 * blank attributes are omitted; {@code toolName} and {@code toolCallId} are only present for
 * documents that came from a tool result.
 */
public record DocumentReferenceXmlTag(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable String toolName,
    @Nullable String toolCallId) {

  /** Creates a tag from a document and its tool call context. */
  public static DocumentReferenceXmlTag from(
      Document document, @Nullable String toolCallId, @Nullable String toolName) {
    return new DocumentReferenceXmlTag(
        DocumentHandle.idFor(document),
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        toolName,
        toolCallId);
  }

  /** Creates a tag from a document without tool call context (e.g. for user prompt documents). */
  public static DocumentReferenceXmlTag from(Document document) {
    return from(document, null, null);
  }

  /** Creates a tag from a registry entry. */
  public static DocumentReferenceXmlTag from(DocumentRegistryEntry entry) {
    return new DocumentReferenceXmlTag(
        entry.id(), entry.fileName(), entry.contentType(), entry.toolName(), entry.toolCallId());
  }

  public String toXml() {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id);
    appendAttribute(sb, "fileName", fileName);
    appendAttribute(sb, "contentType", contentType);
    appendAttribute(sb, "toolName", toolName);
    appendAttribute(sb, "toolCallId", toolCallId);
    return sb.append(" />").toString();
  }

  private static void appendAttribute(StringBuilder sb, String name, @Nullable String value) {
    if (StringUtils.isNotBlank(value)) {
      sb.append(" %s=\"%s\"".formatted(name, StringEscapeUtils.escapeXml10(value)));
    }
  }
}
