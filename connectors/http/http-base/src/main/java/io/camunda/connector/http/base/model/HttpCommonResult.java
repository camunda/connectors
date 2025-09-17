/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import io.camunda.connector.api.document.Document;
import io.camunda.connector.generator.java.annotation.DataExample;
// import io.camunda.connector.runtime.core.document.CamundaDocument;
// import io.camunda.connector.runtime.core.document.CamundaDocumentReferenceImpl;
// import io.camunda.connector.runtime.core.document.DocumentMetadataImpl;
// import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
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
    var body = Map.of("order", Map.of("id", "123", "total", "100.00€"));
    return new HttpCommonResult(200, headers, body);
  }
}
