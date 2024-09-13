package io.camunda.document.store;

import io.camunda.document.DocumentLink;
import io.camunda.document.reference.DocumentReference.CamundaDocumentReference;
import io.camunda.identity.sdk.authentication.Authentication;
import java.io.InputStream;

public class CamundaDocumentStoreImpl implements CamundaDocumentStore {

  Authentication

  @Override
  public CamundaDocumentReference createDocument(DocumentCreationRequest request) {
    return null;
  }

  @Override
  public InputStream getDocumentContent(CamundaDocumentReference reference) {
    return null;
  }

  @Override
  public void deleteDocument(CamundaDocumentReference reference) {

  }

  @Override
  public DocumentLink createLink(CamundaDocumentReference reference) {
    return null;
  }
}
