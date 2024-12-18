package io.camunda.connector.aws.s3.model.response;

import io.camunda.document.Document;

public interface Element {
  record DocumentContent(Document document) implements Element {}

  record StringContent(String content) implements Element {}

  record JsonContent(Object content) implements Element {}
}
