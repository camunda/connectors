/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.api.document.Document;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * Represents a self-closing XML tag used to label a document in context, e.g.:
 *
 * <pre>{@code
 * <doc id="25ece9fa-aeea-..." fileName="report.pdf" contentType="application/pdf" />
 * <doc id="25ece9fa-..." fileName="report.pdf" contentType="application/pdf" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>The {@code id} is derived via {@link DocumentHandle#idFor(Document)} and serves as the stable
 * correlation key between the {@code <doc/>} marker in a tool-call result and the content-bearing
 * user message that follows. Attribute {@code toolName} and {@code toolCallId} are present only
 * when the document came from a tool call (they are omitted for prompt/event documents). Dropped
 * from the old scheme: {@code storeId}, raw {@code documentId} (subsumed by {@code id}), and {@code
 * url} (security: no raw address ever reaches the model).
 */
public record DocumentReferenceXmlTag(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable String toolCallId,
    @Nullable String toolName) {

  /**
   * Creates a tag from a document and its tool call context. The {@code id} is derived via {@link
   * DocumentHandle#idFor(Document)}.
   */
  public static DocumentReferenceXmlTag from(
      Document document, @Nullable String toolCallId, @Nullable String toolName) {
    final var md = document.metadata();
    return new DocumentReferenceXmlTag(
        DocumentHandle.idFor(document),
        md != null ? md.getFileName() : null,
        md != null ? md.getContentType() : null,
        toolCallId,
        toolName);
  }

  /** Creates a tag from a document without tool call context (e.g. for prompt/event documents). */
  public static DocumentReferenceXmlTag from(Document document) {
    return from(document, null, null);
  }

  /** Renders this tag as an XML self-closing element string. */
  public String toXml() {
    var attributes = new LinkedHashMap<String, String>();
    attributes.put("id", id);
    attributes.put("fileName", fileName);
    attributes.put("contentType", contentType);
    attributes.put("toolName", toolName);
    attributes.put("toolCallId", toolCallId);
    return renderTag(attributes);
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
