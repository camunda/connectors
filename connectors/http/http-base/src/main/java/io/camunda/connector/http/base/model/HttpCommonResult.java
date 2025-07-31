/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import io.camunda.client.api.response.DocumentMetadata;
import io.camunda.connector.generator.java.annotation.DataExample;
import io.camunda.document.CamundaDocument;
import io.camunda.document.Document;
import io.camunda.document.reference.CamundaDocumentReferenceImpl;
import io.camunda.document.reference.DocumentReference;
import io.camunda.document.store.InMemoryDocumentStore;
import java.time.OffsetDateTime;
import java.util.Map;

public record HttpCommonResult(
    int status, Map<String, Object> headers, Object body, String reason, Document document) {

  public HttpCommonResult(int status, Map<String, Object> headers, Object body, String reason) {
    this(status, headers, body, reason, null);
  }

  public HttpCommonResult(
      int status, Map<String, Object> headers, Object body, Document documentReference) {
    this(status, headers, body, null, documentReference);
  }

  public HttpCommonResult(int status, Map<String, Object> headers, Object body) {
    this(status, headers, body, null, null);
  }

  @DataExample(id = "basic", feel = "= body.order.id")
  public static HttpCommonResult exampleResult() {
    Map<String, Object> headers = Map.of("Content-Type", "application/json");
    DocumentReference.CamundaDocumentReference documentReference =
        new CamundaDocumentReferenceImpl(
            "theStoreId",
            "977c5cbf-0f19-4a76-a8e1-60902216a07b",
            "hash",
            new DocumentMetadata() {
              @Override
              public String getContentType() {
                return "application/pdf";
              }

              @Override
              public OffsetDateTime getExpiresAt() {
                return null;
              }

              @Override
              public Long getSize() {
                return 516554L;
              }

              @Override
              public String getFileName() {
                return "theFileName.pdf";
              }

              @Override
              public String getProcessDefinitionId() {
                return "";
              }

              @Override
              public Long getProcessInstanceKey() {
                return 0L;
              }

              @Override
              public Map<String, Object> getCustomProperties() {
                return Map.of("key", "value");
              }
            });
    CamundaDocument doc =
        new CamundaDocument(
            documentReference.getMetadata(), documentReference, InMemoryDocumentStore.INSTANCE);
    var body = Map.of("order", Map.of("id", "123", "total", "100.00€"));
    return new HttpCommonResult(200, headers, body, doc);
  }
}
