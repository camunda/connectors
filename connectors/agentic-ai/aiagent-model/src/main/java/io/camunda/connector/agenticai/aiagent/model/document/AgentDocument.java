/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.api.document.DocumentReference.InlineDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A document known to the agent, as persisted in the agent context: its stable id, where it entered
 * the conversation and its document reference.
 *
 * <p>The reference is the document reference model of the connector SDK. It holds the URL of an
 * external document and the full content of an inline one, so it must never be rendered to the
 * model.
 *
 * <p>Reading a document costs memory and can reach other systems. The file name, content type and
 * size come from the reference only; they are never read from the {@link Document} of the connector
 * SDK. For an external document, the SDK downloads the whole file into memory to answer {@code
 * metadata().getContentType()} or {@code metadata().getSize()}, from a URL that the process or a
 * tool supplied. A Camunda document loads its content from the document store. Load the content of
 * a document, or the metadata of an external one, only where the content is needed, such as when
 * the agent adds it to a model call, and check its size against a limit first where one is known.
 *
 * @param id the stable id the document is known by
 * @param origin where the document entered the conversation
 * @param reference the document reference
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDocument(
    AgentDocumentId id, DocumentOrigin origin, DocumentReferenceModel reference) {

  public AgentDocument {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(reference, "reference must not be null");
  }

  /**
   * The agent document of a document, with the reference in the form it has once persisted and read
   * again.
   *
   * @throws UnsupportedDocumentReferenceException if the reference type of the document is not
   *     supported
   */
  public static AgentDocument of(Document document, DocumentOrigin origin) {
    final var reference = document.reference();
    final var id = AgentDocumentId.of(reference);
    return new AgentDocument(id, origin, toModel(reference));
  }

  /** The document the reference points to, restored with the given factory. */
  public Document toDocument(DocumentFactory documentFactory) {
    return documentFactory.resolve(reference);
  }

  /** What the model is shown about the document besides its id and origin. */
  public Descriptors descriptors() {
    return Descriptors.of(reference);
  }

  /**
   * The file name the model is shown, or {@code null} when none is known: the file name of the
   * metadata of a Camunda document, the {@code name} of an external or inline reference. A file
   * name that equals the Camunda document id of the reference is left out: it is what a document
   * stored without a name gets, and would only repeat an id.
   */
  public @Nullable String fileName() {
    return descriptors().fileName();
  }

  /**
   * The content type the model is shown, or {@code null} when it is not known without fetching the
   * content: the content type of the metadata of a Camunda document or the {@code contentType} of
   * an inline reference. An external reference has none.
   */
  public @Nullable String contentType() {
    return descriptors().contentType();
  }

  /**
   * The size in bytes the model is shown, or {@code null} when it is not known without reading the
   * content: the size of the metadata of a Camunda document. Other references have none.
   */
  public @Nullable Long size() {
    return descriptors().size();
  }

  /**
   * Whether the document is the one this agent document stands for: the identity keys are equal.
   */
  boolean isSameDocumentAs(AgentDocument other) {
    return Objects.equals(
        AgentDocumentId.identityKey(reference), AgentDocumentId.identityKey(other.reference()));
  }

  /**
   * What the model is shown about a document besides its id, read from the reference of the
   * document.
   *
   * @param fileName see {@link AgentDocument#fileName()}
   * @param contentType see {@link AgentDocument#contentType()}
   * @param size see {@link AgentDocument#size()}
   */
  public record Descriptors(
      @Nullable String fileName, @Nullable String contentType, @Nullable Long size) {

    public static Descriptors of(DocumentReference reference) {
      return switch (reference) {
        case CamundaDocumentReference ref -> ofCamunda(ref);
        case ExternalDocumentReference ref ->
            new Descriptors(StringUtils.trimToNull(ref.name()), null, null);
        case InlineDocumentReference ref ->
            new Descriptors(
                StringUtils.trimToNull(ref.name()),
                StringUtils.trimToNull(ref.contentType()),
                null);
        case null, default -> new Descriptors(null, null, null);
      };
    }

    private static Descriptors ofCamunda(CamundaDocumentReference reference) {
      final DocumentMetadata metadata = reference.getMetadata();
      if (metadata == null) {
        return new Descriptors(null, null, null);
      }
      final var fileName = StringUtils.trimToNull(metadata.getFileName());
      return new Descriptors(
          fileName != null && fileName.equals(reference.getDocumentId()) ? null : fileName,
          StringUtils.trimToNull(metadata.getContentType()),
          metadata.getSize());
    }
  }

  private static DocumentReferenceModel toModel(DocumentReference reference) {
    return switch (reference) {
      case DocumentReferenceModel model -> model;
      case CamundaDocumentReference ref ->
          new CamundaDocumentReferenceModel(
              ref.getStoreId(),
              ref.getDocumentId(),
              ref.getContentHash(),
              ref.getMetadata() == null
                  ? null
                  : new CamundaDocumentMetadataModel(ref.getMetadata()));
      case ExternalDocumentReference ref ->
          new ExternalDocumentReferenceModel(ref.url(), ref.name());
      case InlineDocumentReference ref ->
          new InlineDocumentReferenceModel(ref.content(), ref.name(), ref.contentType());
      default ->
          throw new UnsupportedDocumentReferenceException(
              "Unsupported document reference type '%s'"
                  .formatted(reference.getClass().getSimpleName()));
    };
  }
}
