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
import java.util.regex.Pattern;
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
 * documents that came from a tool result. A file name that equals the id (a document stored without
 * a name of its own) is omitted as well, it would only repeat the id.
 */
public record DocumentReferenceXmlTag(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable String toolName,
    @Nullable String toolCallId) {

  private static final Pattern DOC_TAG = Pattern.compile("\\s*<doc(\\s[^<>]*?)?\\s*/?>\\s*");
  private static final Pattern ATTRIBUTE =
      Pattern.compile("([A-Za-z_][\\w.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

  public DocumentReferenceXmlTag {
    if (fileName != null && fileName.equals(id)) {
      fileName = null;
    }
  }

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
    final var origin = entry.origin();
    return new DocumentReferenceXmlTag(
        entry.id(),
        DocumentHandle.fileNameFor(entry.reference()),
        DocumentHandle.contentTypeFor(entry.reference()),
        origin == null ? null : origin.toolName(),
        origin == null ? null : origin.toolCallId());
  }

  /**
   * Reads the document id from a string that is a {@code <doc .../>} tag, as the model passes it
   * back. Only the {@code id} attribute is read; other attributes are ignored and surrounding
   * whitespace is tolerated. Returns {@code null} if the string is not a tag or has no non-blank
   * {@code id}.
   */
  public static @Nullable String parseId(String value) {
    final var tag = DOC_TAG.matcher(value);
    if (!tag.matches() || tag.group(1) == null) {
      return null;
    }
    final var attributes = ATTRIBUTE.matcher(tag.group(1));
    while (attributes.find()) {
      if ("id".equals(attributes.group(1))) {
        final var raw = attributes.group(2) != null ? attributes.group(2) : attributes.group(3);
        final var id = StringEscapeUtils.unescapeXml(raw).strip();
        return id.isEmpty() ? null : id;
      }
    }
    return null;
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
