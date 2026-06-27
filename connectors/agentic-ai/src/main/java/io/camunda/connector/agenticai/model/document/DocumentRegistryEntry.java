/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.model.document;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import io.camunda.connector.agenticai.model.AgenticAiRecord;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.ExternalDocumentReferenceModel;
import io.camunda.connector.document.jackson.DocumentReferenceModel.InlineDocumentReferenceModel;
import org.springframework.lang.Nullable;

/**
 * A single entry in the {@link DocumentRegistry}: maps a stable id to a stored document reference.
 */
@AgenticAiRecord
@JsonDeserialize(builder = DocumentRegistryEntry.DocumentRegistryEntryJacksonProxyBuilder.class)
public record DocumentRegistryEntry(
    String id,
    DocumentReferenceModel reference,
    @Nullable String fileName,
    @Nullable String contentType)
    implements DocumentRegistryEntryBuilder.With {

  static DocumentRegistryEntryBuilder builder() {
    return DocumentRegistryEntryBuilder.builder();
  }

  /**
   * Creates a registry entry from a document. The id is derived via {@link DocumentHandle#idFor}.
   * Reference type is converted to a {@link DocumentReferenceModel} subtype. The byte content of
   * inline documents is not stored — only the metadata is retained.
   */
  static DocumentRegistryEntry from(Document document) {
    final var id = DocumentHandle.idFor(document);
    final var md = document.metadata();
    final var reference = toReferenceModel(document);
    return builder()
        .id(id)
        .reference(reference)
        .fileName(md != null ? md.getFileName() : null)
        .contentType(md != null ? md.getContentType() : null)
        .build();
  }

  private static DocumentReferenceModel toReferenceModel(Document document) {
    return switch (document.reference()) {
      case CamundaDocumentReference ref ->
          new CamundaDocumentReferenceModel(
              ref.getStoreId(), ref.getDocumentId(), ref.getContentHash(), null);
      case ExternalDocumentReference ref ->
          new ExternalDocumentReferenceModel(ref.url(), ref.name());
      case null, default -> new InlineDocumentReferenceModel(null, null, null);
    };
  }

  @JsonPOJOBuilder(withPrefix = "")
  public static class DocumentRegistryEntryJacksonProxyBuilder
      extends DocumentRegistryEntryBuilder {}
}
