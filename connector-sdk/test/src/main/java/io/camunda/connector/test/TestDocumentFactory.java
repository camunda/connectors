package io.camunda.connector.test;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import io.camunda.connector.api.document.DocumentReference.ExternalDocumentReference;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TestDocumentFactory implements DocumentFactory {

  private final Map<String, Document> documents = new HashMap<>();

  @Override
  public Document resolve(DocumentReference reference) {
    if (reference == null) {
      return null;
    }

    if (reference instanceof CamundaDocumentReference camundaRef) {
      return documents.get(camundaRef.getDocumentId());
    }

    if (reference instanceof ExternalDocumentReference extRef) {
      return documents.get(extRef.url());
    }

    throw new IllegalArgumentException("Unknown document reference type: " + reference.getClass());
  }

  @Override
  public Document create(DocumentCreationRequest request) {
    String documentId = UUID.randomUUID().toString();

    final byte[] content;
    try (InputStream in = request.content()) {
      content = in.readAllBytes();
    } catch (IOException e) {
      throw new RuntimeException("Failed to read InputStream", e);
    }

    DocumentMetadata metadata =
        new TestDocumentMetadata(
            request.contentType(),
            OffsetDateTime.now().plus(request.timeToLive()),
            (long) content.length,
            request.fileName(),
            request.processDefinitionId(),
            request.processInstanceKey(),
            request.customProperties());

    DocumentReference.CamundaDocumentReference reference =
        new CamundaDocumentReference() {
          @Override
          public String getDocumentId() {
            return documentId;
          }

          @Override
          public String getStoreId() {
            return "test-hashmap-store";
          }

          @Override
          public String getContentHash() {
            return Integer.toHexString(documentId.hashCode());
          }

          @Override
          public DocumentMetadata getMetadata() {
            return metadata;
          }
        };

    // Create TestDocument
    Document document = new TestDocument(content, metadata, reference, documentId);
    documents.put(documentId, document);
    return document;
  }
}
