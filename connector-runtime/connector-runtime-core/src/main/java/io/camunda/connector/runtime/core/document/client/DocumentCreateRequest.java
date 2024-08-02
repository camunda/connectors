package io.camunda.connector.runtime.core.document.client;

import java.io.InputStream;

public record DocumentCreateRequest() {

  public static DocumentCreateRequestBuilder create(bye[] content) {
    return new DocumentCreateRequestBuilder();
  }

  public static class DocumentCreateRequestBuilder {
    private DocumentCreateRequestBuilder(InputStream content) {
    }

    public DocumentCreateRequest build() {
      return new DocumentCreateRequest();
    }
  }
}
