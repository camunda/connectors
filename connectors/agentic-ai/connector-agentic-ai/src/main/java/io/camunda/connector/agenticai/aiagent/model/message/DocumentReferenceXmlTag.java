/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistryEntry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.api.document.Document;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * store id, content hash, other metadata, URL (an external URL may be a presigned one) or message
 * id. Null or blank attributes are omitted; {@code toolName} and {@code toolCallId} are only
 * present for documents that came from a tool result, {@code source} ({@code user_message} or
 * {@code event}) only for the other documents. A file name that equals the id (a document stored
 * without a name of its own) is omitted as well, it would only repeat the id.
 *
 * <p>The persisted form of a tag is a {@link TextContent} whose metadata marks it as a {@value
 * #KIND_DOCUMENT_REFERENCE} and carries the id of its document (see {@link #toContent()}).
 */
public record DocumentReferenceXmlTag(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable String source,
    @Nullable String toolName,
    @Nullable String toolCallId) {

  /** Metadata key that tells what kind of text a {@link TextContent} is. */
  public static final String METADATA_KIND = "kind";

  /** Value of {@link #METADATA_KIND} for the text that is the tag of a document. */
  public static final String KIND_DOCUMENT_REFERENCE = "document-reference";

  /** Metadata key of the id of the document whose tag the {@link TextContent} holds. */
  public static final String METADATA_DOCUMENT_ID = "documentId";

  private static final Pattern DOC_TAG = Pattern.compile("\\s*<doc(\\s[^<>]*?)?\\s*/?>\\s*");
  private static final Pattern ATTRIBUTE =
      Pattern.compile("([A-Za-z_][\\w.:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

  public DocumentReferenceXmlTag {
    if (fileName != null && fileName.equals(id)) {
      fileName = null;
    }
  }

  /** Creates a tag from a document and where it entered the conversation. */
  public static DocumentReferenceXmlTag from(Document document, DocumentSource documentSource) {
    return new DocumentReferenceXmlTag(
        DocumentHandle.idFor(document),
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        documentSource.type().tagValue(),
        documentSource.toolName(),
        documentSource.toolCallId());
  }

  /** Creates a tag from a document without a source. */
  public static DocumentReferenceXmlTag from(Document document) {
    return new DocumentReferenceXmlTag(
        DocumentHandle.idFor(document),
        DocumentHandle.fileNameFor(document),
        DocumentHandle.contentTypeFor(document),
        null,
        null,
        null);
  }

  /** Creates a tag from a registry entry. */
  public static DocumentReferenceXmlTag from(DocumentRegistryEntry entry) {
    final var documentSource = entry.source();
    return new DocumentReferenceXmlTag(
        entry.id(),
        DocumentHandle.fileNameFor(entry.reference()),
        DocumentHandle.contentTypeFor(entry.reference()),
        documentSource.type().tagValue(),
        documentSource.toolName(),
        documentSource.toolCallId());
  }

  /**
   * Returns the id of the document whose tag the content holds, or {@code null} when the content is
   * not marked as a {@value #KIND_DOCUMENT_REFERENCE}.
   */
  public static @Nullable String documentIdOf(TextContent content) {
    final var metadata = content.metadata();
    return metadata != null
            && KIND_DOCUMENT_REFERENCE.equals(metadata.get(METADATA_KIND))
            && metadata.get(METADATA_DOCUMENT_ID) instanceof String id
            && !id.isBlank()
        ? id
        : null;
  }

  /**
   * Returns {@code true} if the text is a single {@code <doc .../>} tag, whatever its attributes
   * (surrounding whitespace is tolerated).
   */
  public static boolean isTag(String value) {
    return DOC_TAG.matcher(value).matches();
  }

  /**
   * Reads the attributes of a string that is a single {@code <doc .../>} tag, in document order and
   * XML-unescaped; {@code null} if the string is not a tag.
   */
  public static @Nullable Map<String, String> parseAttributes(String value) {
    final var tag = DOC_TAG.matcher(value);
    if (!tag.matches()) {
      return null;
    }
    final var attributes = new LinkedHashMap<String, String>();
    if (tag.group(1) != null) {
      final var matcher = ATTRIBUTE.matcher(tag.group(1));
      while (matcher.find()) {
        final var raw = matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
        attributes.putIfAbsent(matcher.group(1), StringEscapeUtils.unescapeXml(raw));
      }
    }
    return attributes;
  }

  /** The persisted form of the tag: the tag text marked as a document reference of its document. */
  public TextContent toContent() {
    // the order of the entries is part of the serialized form
    final var metadata = new LinkedHashMap<String, Object>();
    metadata.put(METADATA_KIND, KIND_DOCUMENT_REFERENCE);
    metadata.put(METADATA_DOCUMENT_ID, id);
    return new TextContent(toXml(), Collections.unmodifiableMap(metadata));
  }

  public String toXml() {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id);
    appendAttribute(sb, "fileName", fileName);
    appendAttribute(sb, "contentType", contentType);
    appendAttribute(sb, "source", source);
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
