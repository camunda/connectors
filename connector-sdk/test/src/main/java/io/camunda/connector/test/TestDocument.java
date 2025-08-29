package io.camunda.connector.test;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentLinkParameters;
import io.camunda.connector.api.document.DocumentMetadata;
import io.camunda.connector.api.document.DocumentReference;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;

public class TestDocument implements Document {

  private final byte[] content;
  private final DocumentMetadata metadata;
  private final DocumentReference reference;
  private final String documentId;

  public TestDocument(
      byte[] content, DocumentMetadata metadata, DocumentReference reference, String documentId) {
    this.content = content;
    this.metadata = metadata;
    this.reference = reference;
    this.documentId = documentId;
  }

  @Override
  public DocumentMetadata metadata() {
    return metadata;
  }

  @Override
  public String asBase64() {
    return Base64.getEncoder().encodeToString(content);
  }

  @Override
  public InputStream asInputStream() {
    return new ByteArrayInputStream(content);
  }

  @Override
  public byte[] asByteArray() {
    return content;
  }

  @Override
  public DocumentReference reference() {
    return reference;
  }

  @Override
  public String generateLink(DocumentLinkParameters parameters) {
    return "https://test/" + documentId;
  }
}
