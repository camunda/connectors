package io.camunda.connector.runtime.core.document.client;

import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference.StaticDocumentReference;

public interface DocumentStoreClient {

  StaticDocumentReference createDocument(DocumentMetadata metadata, byte[] content);

  StaticDocumentReference createDocument(DocumentMetadata metadata,)
}
