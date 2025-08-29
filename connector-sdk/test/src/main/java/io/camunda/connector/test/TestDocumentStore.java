package io.camunda.connector.test;

import io.camunda.connector.api.document.*;
import io.camunda.connector.api.document.DocumentReference.CamundaDocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TestDocumentStore implements CamundaDocumentStore {

  private final Map<String, byte[]> store = new HashMap<>();
  private final Map<String, DocumentMetadata> metadataMap = new HashMap<>();

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    String documentId = UUID.randomUUID().toString();

    final byte[] content;
    try (InputStream in = request.content()) {
      content = in.readAllBytes();
    } catch (Exception e) {
      throw new RuntimeException("Failed to read InputStream", e);
    }

    // Minimal Metadata
    DocumentMetadata metadata =
        new DocumentMetadata() {
          @Override
          public String getContentType() {
            return request.contentType();
          }

          @Override
          public OffsetDateTime getExpiresAt() {
            Duration ttl = request.timeToLive();
            return ttl != null ? OffsetDateTime.now().plus(ttl) : null;
          }

          @Override
          public Long getSize() {
            return (long) content.length;
          }

          @Override
          public String getFileName() {
            return request.fileName();
          }

          @Override
          public String getProcessDefinitionId() {
            return request.processDefinitionId();
          }

          @Override
          public Long getProcessInstanceKey() {
            return request.processInstanceKey();
          }

          @Override
          public Map<String, Object> getCustomProperties() {
            return request.customProperties();
          }
        };

    store.put(documentId, content);
    metadataMap.put(documentId, metadata);

    // Reference
    return new CamundaDocumentReference() {
      @Override
      public String getDocumentId() {
        return documentId;
      }

      @Override
      public String getStoreId() {
        return "test-in-memory-store";
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
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    byte[] content = store.get(reference.getDocumentId());
    if (content == null) return null;
    return new ByteArrayInputStream(content);
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {
    store.remove(reference.getDocumentId());
    metadataMap.remove(reference.getDocumentId());
  }

  @Override
  public String generateLink(
      CamundaDocumentReference reference, DocumentLinkParameters parameters) {
    return "https://test-store/" + reference.getDocumentId();
  }
}
