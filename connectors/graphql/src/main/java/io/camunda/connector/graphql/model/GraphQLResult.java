/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.graphql.model;

import com.google.common.base.Objects;
import java.util.Map;

public class GraphQLResult {

  private int status;
  private Map<String, Object> headers;
  private Object body;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public Map<String, Object> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, Object> headers) {
    this.headers = headers;
  }

  public Object getBody() {
    return body;
  }

  public void setBody(Object body) {
    this.body = body;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GraphQLResult that = (GraphQLResult) o;
    return status == that.status
        && com.google.common.base.Objects.equal(headers, that.headers)
        && com.google.common.base.Objects.equal(body, that.body);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(status, headers, body);
  }

  @Override
  public String toString() {
    return "GraphQLResult{" + "status=" + status + ", headers=" + headers + ", body=" + body + '}';
  }
}
