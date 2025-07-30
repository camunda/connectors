/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.base.model;

import io.camunda.document.Document;
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

  public int status() {
    return status;
  }

  public Map<String, Object> headers() {
    return headers;
  }

  public Object body() {
    return body;
  }

  public String reason() {
    return reason;
  }

  public Document document() {
    return document;
  }
}
