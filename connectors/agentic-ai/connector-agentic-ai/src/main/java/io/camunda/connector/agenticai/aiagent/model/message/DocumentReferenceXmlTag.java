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
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;

/**
 * The self-closing XML tag that is the only way the model sees a document, e.g.:
 *
 * <pre>{@code
 * <doc id="25ece9fa-aeea-..." fileName="report.pdf" contentType="application/pdf" size="48213" toolName="search" toolCallId="call_abc" />
 * }</pre>
 *
 * <p>{@code id} is the {@link DocumentHandle#idFor stable document id}. The tag never carries a
 * store id, content hash, other metadata, URL (an external URL may be a presigned one) or message
 * id. Null or blank attributes are omitted; {@code toolName} and {@code toolCallId} are only
 * present for documents that came from a tool result, {@code source} ({@code user_message} or
 * {@code event}) only for the other documents. A file name that equals the id (a document stored
 * without a name of its own) is omitted as well, it would only repeat the id. {@code size} (bytes)
 * is present for a Camunda document whose reference metadata carries one; it is never looked up by
 * reading the document.
 *
 * <p>The persisted form of a tag is a {@link TextContent} whose metadata marks it as a {@value
 * #KIND_DOCUMENT_REFERENCE} and carries the id of its document (see {@link #toContent()}). The
 * content of the document is shown to the model only next to a tag that is also marked as loaded
 * (see {@link #toLoadedContent()}).
 */
public record DocumentReferenceXmlTag(
    String id,
    @Nullable String fileName,
    @Nullable String contentType,
    @Nullable Long size,
    @Nullable String source,
    @Nullable String toolName,
    @Nullable String toolCallId) {

  /** Metadata key that tells what kind of text a {@link TextContent} is. */
  public static final String METADATA_KIND = "kind";

  /** Value of {@link #METADATA_KIND} for the text that is the tag of a document. */
  public static final String KIND_DOCUMENT_REFERENCE = "document-reference";

  /** Metadata key of the id of the document whose tag the {@link TextContent} holds. */
  public static final String METADATA_DOCUMENT_ID = "documentId";

  /** Metadata key whose value {@code true} marks a tag whose document content is loaded. */
  public static final String METADATA_LOADED = "loaded";

  /** A text that is exactly one tag: {@code <doc}, optional attributes, {@code />}. */
  private static final Pattern SINGLE_TAG = Pattern.compile("<doc(\\s[^<>]*)?/>");

  private static final Pattern ID_ATTRIBUTE = Pattern.compile("(?:^|\\s)id=\"([^\"]*)\"");

  /** Creates a tag without a size. */
  public DocumentReferenceXmlTag(
      String id,
      @Nullable String fileName,
      @Nullable String contentType,
      @Nullable String source,
      @Nullable String toolName,
      @Nullable String toolCallId) {
    this(id, fileName, contentType, null, source, toolName, toolCallId);
  }

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
        DocumentHandle.sizeFor(document),
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
        DocumentHandle.sizeFor(document),
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
        DocumentHandle.sizeFor(entry.reference()),
        documentSource.type().tagValue(),
        documentSource.toolName(),
        documentSource.toolCallId());
  }

  /**
   * Returns the id of the document if the text is exactly one tag, surrounded by whitespace at
   * most, and the tag has a non-blank {@code id} attribute; the value is XML-unescaped. Returns
   * {@code null} for any other text: text around the tag, more than one tag, or a tag without an
   * id.
   */
  public static @Nullable String idOfSingleTag(String text) {
    final var tag = SINGLE_TAG.matcher(text.strip());
    if (!tag.matches() || tag.group(1) == null) {
      return null;
    }
    final var id = ID_ATTRIBUTE.matcher(tag.group(1));
    if (!id.find()) {
      return null;
    }
    final var unescaped = StringEscapeUtils.unescapeXml(id.group(1));
    return unescaped.isBlank() ? null : unescaped;
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
   * Returns whether the content is a tag marked as loaded: the content of its document is shown to
   * the model next to it.
   */
  public static boolean isLoaded(TextContent content) {
    final var metadata = content.metadata();
    return documentIdOf(content) != null
        && metadata != null
        && Boolean.TRUE.equals(metadata.get(METADATA_LOADED));
  }

  /**
   * The persisted form of the tag: the tag text marked as a document reference of its document. The
   * model sees the tag only.
   */
  public TextContent toContent() {
    return toContent(false);
  }

  /**
   * The persisted form of the tag of a loaded document: marked like {@link #toContent()} and
   * additionally as loaded, so the content of the document is added next to it.
   */
  public TextContent toLoadedContent() {
    return toContent(true);
  }

  private TextContent toContent(boolean loaded) {
    // the order of the entries is part of the serialized form
    final var metadata = new LinkedHashMap<String, Object>();
    metadata.put(METADATA_KIND, KIND_DOCUMENT_REFERENCE);
    metadata.put(METADATA_DOCUMENT_ID, id);
    if (loaded) {
      metadata.put(METADATA_LOADED, true);
    }
    return new TextContent(toXml(), Collections.unmodifiableMap(metadata));
  }

  public String toXml() {
    final var sb = new StringBuilder("<doc");
    appendAttribute(sb, "id", id);
    appendAttribute(sb, "fileName", fileName);
    appendAttribute(sb, "contentType", contentType);
    appendAttribute(sb, "size", size == null ? null : String.valueOf(size));
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
