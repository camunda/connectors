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
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentMetadataModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * A document known to the agent, as persisted in the agent context: its stable id, where it entered
 * the conversation and its document reference. The agent knows Camunda documents only, see {@link
 * #isSupported(DocumentReference)}.
 *
 * <p>The reference is the document reference model of the connector SDK.
 *
 * <p>Reading a document costs memory. The file name, content type and size come from the metadata
 * of the reference only; they are never read from the {@link Document} of the connector SDK. Load
 * the content of a document only where the content is needed, such as when the agent adds it to a
 * model call, and check its size against a limit first where one is known.
 *
 * @param id the stable id the document is known by
 * @param origin where the document entered the conversation
 * @param reference the document reference
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentDocument(
    AgentDocumentId id, AgentDocumentOrigin origin, CamundaDocumentReferenceModel reference) {

  public AgentDocument {
    Objects.requireNonNull(id, "id must not be null");
    Objects.requireNonNull(origin, "origin must not be null");
    Objects.requireNonNull(reference, "reference must not be null");
  }

  /**
   * Whether the agent supports the document the reference points to: only a Camunda document. The
   * content of a Camunda document cannot change and its metadata is known without reading it,
   * unlike an external document, which is read from its URL again in every job, and an inline
   * document, which holds its content in the reference.
   */
  public static boolean isSupported(DocumentReference reference) {
    return reference instanceof CamundaDocumentReference;
  }

  /** Whether the agent supports the document, see {@link #isSupported(DocumentReference)}. */
  public static boolean isSupported(Document document) {
    return isSupported(document.reference());
  }

  /**
   * The agent document of a document, with the reference in the form it has once persisted and read
   * again.
   *
   * @throws UnsupportedDocumentReferenceException if the document is not supported, see {@link
   *     #isSupported(DocumentReference)}
   */
  public static AgentDocument of(Document document, AgentDocumentOrigin origin) {
    final var id = AgentDocumentId.of(document);
    return new AgentDocument(id, origin, toModel((CamundaDocumentReference) document.reference()));
  }

  /** The document the reference points to, restored with the given factory. */
  public Document toDocument(DocumentFactory documentFactory) {
    return documentFactory.resolve(reference);
  }

  /**
   * Whether the document is the one this agent document stands for: the store id and the document
   * id are equal.
   */
  boolean isSameDocumentAs(AgentDocument other) {
    return Objects.equals(reference.getStoreId(), other.reference().getStoreId())
        && Objects.equals(reference.getDocumentId(), other.reference().getDocumentId());
  }

  /**
   * What the model is shown about a document besides its id, read from the metadata of the
   * reference of the document.
   *
   * @param fileName the file name of the metadata, or {@code null} when none is known. A file name
   *     that equals the Camunda document id of the reference is left out: it is what a document
   *     stored without a name gets, and would only repeat an id.
   * @param contentType the content type of the metadata, or {@code null} when it has none
   * @param size the size in bytes of the metadata, or {@code null} when it has none
   */
  public record Descriptors(
      @Nullable String fileName, @Nullable String contentType, @Nullable Long size) {

    public static Descriptors of(CamundaDocumentReference reference) {
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

  private static CamundaDocumentReferenceModel toModel(CamundaDocumentReference reference) {
    return reference instanceof CamundaDocumentReferenceModel model
        ? model
        : new CamundaDocumentReferenceModel(
            reference.getStoreId(),
            reference.getDocumentId(),
            reference.getContentHash(),
            reference.getMetadata() == null
                ? null
                : new CamundaDocumentMetadataModel(reference.getMetadata()));
  }
}
